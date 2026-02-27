package com.echonotify.worker

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.PublishOutboxUseCase
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
import com.echonotify.core.application.usecase.RetryNotificationUseCase
import com.echonotify.core.infrastructure.bootstrap.BootstrapFactory
import com.echonotify.core.infrastructure.messaging.DlqErrorMessage
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.messaging.NotificationMessage
import com.echonotify.core.infrastructure.messaging.toDomain
import com.echonotify.core.infrastructure.notification.NotificationChannelFactory
import com.echonotify.core.infrastructure.observability.KafkaTracing
import com.echonotify.core.infrastructure.observability.NotificationMetrics
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID

fun main() = runBlocking {
    val log = LoggerFactory.getLogger("EchoNotifyWorker")
    val config = com.typesafe.config.ConfigFactory.load()

    val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
    val retryGroupId = config.getString("echo-notify.kafka.retryGroupId")
    val dlqGroupId = config.getString("echo-notify.kafka.dlqGroupId")
    val metricsPort = config.getInt("echo-notify.observability.metricsPort")
    val retryByType = BootstrapFactory.retryPolicyByType(config)

    val repository = BootstrapFactory.createRepository(config)
    val producer = BootstrapFactory.createProducer(config)
    val publisher = BootstrapFactory.createPublisher(producer)
    val httpClient = BootstrapFactory.createHttpClient()
    val channels = NotificationChannelFactory.build(httpClient, Json)
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val metrics = NotificationMetrics(meterRegistry)
    val metricsServer = startMetricsServer(metricsPort, meterRegistry)

    val processUseCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(retryByType),
        metrics = metrics
    )
    val retryUseCase = RetryNotificationUseCase(processUseCase)
    val publishOutboxUseCase = PublishOutboxUseCase(repository, publisher)

    val retryConsumer = KafkaConsumer<String, String>(KafkaClientFactory.consumerProps(bootstrapServers, retryGroupId))
    retryConsumer.subscribe(listOf(TopicNames.RETRY))

    val dlqConsumer = KafkaConsumer<String, String>(KafkaClientFactory.consumerProps(bootstrapServers, dlqGroupId))
    dlqConsumer.subscribe(listOf(TopicNames.DLQ))

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down worker resources")
        runCatching { retryConsumer.wakeup() }
        runCatching { dlqConsumer.wakeup() }
        runCatching { retryConsumer.close() }
        runCatching { dlqConsumer.close() }
        runCatching { producer.close() }
        runCatching { httpClient.close() }
        runCatching { metricsServer.stop(0) }
    })

    launch {
        while (true) {
            runCatching { publishOutboxUseCase.execute(limit = 100) }
                .onFailure { log.error("Outbox dispatch failed", it) }
            delay(500)
        }
    }

    launch {
        while (true) {
            val records = retryConsumer.poll(Duration.ofMillis(500))
            for (record in records) {
                try {
                    KafkaTracing.withConsumerSpan(record.headers(), "kafka.consume.retry") {
                        val message = Json.decodeFromString<NotificationMessage>(record.value())
                        retryUseCase.execute(message.toDomain())
                    }
                    commitRecord(retryConsumer, record)
                } catch (ex: SerializationException) {
                    publishParseErrorToDlq(producer, record)
                    commitRecord(retryConsumer, record)
                } catch (ex: Exception) {
                    log.error("Failed to process retry record key={} topic={}", record.key(), record.topic(), ex)
                    break
                }
            }
            delay(100)
        }
    }

    launch {
        while (true) {
            val records = dlqConsumer.poll(Duration.ofMillis(500))
            for (record in records) {
                KafkaTracing.withConsumerSpan(record.headers(), "kafka.consume.dlq") {
                    log.warn("DLQ notification received id={} payload={}", record.key(), record.value())
                }
                commitRecord(dlqConsumer, record)
            }
            delay(100)
        }
    }.join()

    metricsServer.stop(0)
}

private fun startMetricsServer(port: Int, meterRegistry: PrometheusMeterRegistry): HttpServer {
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/health") { exchange ->
        respond(exchange, 200, "ok")
    }
    server.createContext("/metrics") { exchange ->
        respond(exchange, 200, meterRegistry.scrape())
    }
    server.start()
    return server
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { output -> output.write(bytes) }
}

private fun commitRecord(consumer: KafkaConsumer<String, String>, record: ConsumerRecord<String, String>) {
    val offset = OffsetAndMetadata(record.offset() + 1)
    val partition = TopicPartition(record.topic(), record.partition())
    consumer.commitSync(mapOf(partition to offset))
}

private fun publishParseErrorToDlq(producer: KafkaProducer<String, String>, record: ConsumerRecord<String, String>) {
    val payload = Json.encodeToString(
        DlqErrorMessage(
            reason = "DESERIALIZATION_ERROR",
            sourceTopic = record.topic(),
            sourcePartition = record.partition(),
            sourceOffset = record.offset(),
            rawPayload = record.value()
        )
    )
    producer.send(ProducerRecord(TopicNames.DLQ, UUID.randomUUID().toString(), payload)).get()
}
