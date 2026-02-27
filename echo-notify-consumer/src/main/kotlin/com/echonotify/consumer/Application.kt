package com.echonotify.consumer

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
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
    val log = LoggerFactory.getLogger("EchoNotifyConsumer")
    val config = com.typesafe.config.ConfigFactory.load()

    val jdbcUrl = config.getString("echo-notify.database.url")
    val dbUser = config.getString("echo-notify.database.user")
    val dbPass = config.getString("echo-notify.database.password")
    val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
    val groupId = config.getString("echo-notify.kafka.groupId")
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

    val useCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(retryByType),
        metrics = metrics
    )

    val kafkaConsumer = KafkaConsumer<String, String>(KafkaClientFactory.consumerProps(bootstrapServers, groupId))
    kafkaConsumer.subscribe(listOf(TopicNames.SEND))

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down consumer resources")
        runCatching { kafkaConsumer.wakeup() }
        runCatching { kafkaConsumer.close() }
        runCatching { producer.close() }
        runCatching { httpClient.close() }
        runCatching { metricsServer.stop(0) }
    })

    try {
        while (true) {
            val records = kafkaConsumer.poll(Duration.ofMillis(500))
            for (record in records) {
                try {
                    KafkaTracing.withConsumerSpan(record.headers(), "kafka.consume.send") {
                        val message = Json.decodeFromString<NotificationMessage>(record.value())
                        useCase.execute(message.toDomain())
                    }
                    commitRecord(kafkaConsumer, record)
                } catch (ex: SerializationException) {
                    publishParseErrorToDlq(producer, record)
                    commitRecord(kafkaConsumer, record)
                } catch (ex: Exception) {
                    log.error("Failed to process record key={} topic={}", record.key(), record.topic(), ex)
                    break
                }
            }
        }
    } catch (ex: Exception) {
        log.warn("Consumer loop interrupted", ex)
    } finally {
        kafkaConsumer.close()
        producer.close()
        httpClient.close()
        metricsServer.stop(0)
    }
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
