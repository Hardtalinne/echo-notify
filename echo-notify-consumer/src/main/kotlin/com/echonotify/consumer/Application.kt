package com.echonotify.consumer

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.RetryPolicyByType
import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.infrastructure.config.DatabaseFactory
import com.echonotify.core.infrastructure.messaging.DlqErrorMessage
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.messaging.KafkaNotificationPublisher
import com.echonotify.core.infrastructure.messaging.NotificationMessage
import com.echonotify.core.infrastructure.messaging.toDomain
import com.echonotify.core.infrastructure.notification.NotificationChannelFactory
import com.echonotify.core.infrastructure.persistence.PostgresNotificationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
    val defaultRetryPolicy = readRetryPolicy(config, "echo-notify.retry.default", fallbackPath = "echo-notify.retry")
    val retryByType = RetryPolicyByType(
        defaultPolicy = defaultRetryPolicy,
        perTypePolicy = mapOf(
            NotificationType.EMAIL to readRetryPolicy(config, "echo-notify.retry.byType.EMAIL", defaultRetryPolicy),
            NotificationType.WEBHOOK to readRetryPolicy(config, "echo-notify.retry.byType.WEBHOOK", defaultRetryPolicy)
        )
    )

    val database = DatabaseFactory.create(jdbcUrl, dbUser, dbPass)
    val repository = PostgresNotificationRepository(database)

    val producer = KafkaProducer<String, String>(KafkaClientFactory.producerProps(bootstrapServers))
    val publisher = KafkaNotificationPublisher(producer)
    val httpClient = HttpClient(CIO)

    val channels = NotificationChannelFactory.build(httpClient, Json)

    val useCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(retryByType)
    )

    val kafkaConsumer = KafkaConsumer<String, String>(KafkaClientFactory.consumerProps(bootstrapServers, groupId))
    kafkaConsumer.subscribe(listOf(TopicNames.SEND))

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down consumer resources")
        runCatching { kafkaConsumer.wakeup() }
        runCatching { kafkaConsumer.close() }
        runCatching { producer.close() }
        runCatching { httpClient.close() }
    })

    try {
        while (true) {
            val records = kafkaConsumer.poll(Duration.ofMillis(500))
            for (record in records) {
                try {
                    val traceparent = record.headers().lastHeader("traceparent")?.value()?.toString(Charsets.UTF_8)
                    if (!traceparent.isNullOrBlank()) {
                        log.debug("Processing record with traceparent={}", traceparent)
                    }
                    val message = Json.decodeFromString<NotificationMessage>(record.value())
                    useCase.execute(message.toDomain())
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
    }
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

private fun readRetryPolicy(
    config: com.typesafe.config.Config,
    path: String,
    fallback: RetryPolicy? = null,
    fallbackPath: String? = null
): RetryPolicy {
    return when {
        config.hasPath("$path.maxAttempts") -> RetryPolicy(
            maxAttempts = config.getInt("$path.maxAttempts"),
            baseDelayMillis = config.getLong("$path.baseDelayMillis"),
            maxDelayMillis = config.getLong("$path.maxDelayMillis")
        )

        fallback != null -> fallback

        fallbackPath != null && config.hasPath("$fallbackPath.maxAttempts") -> RetryPolicy(
            maxAttempts = config.getInt("$fallbackPath.maxAttempts"),
            baseDelayMillis = 1_000,
            maxDelayMillis = 300_000
        )

        else -> RetryPolicy()
    }
}
