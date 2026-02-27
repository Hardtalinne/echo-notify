package com.echonotify.consumer

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
import com.echonotify.core.infrastructure.config.DatabaseFactory
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.messaging.KafkaNotificationPublisher
import com.echonotify.core.infrastructure.messaging.NotificationMessage
import com.echonotify.core.infrastructure.messaging.toDomain
import com.echonotify.core.infrastructure.notification.email.EmailNotificationChannel
import com.echonotify.core.infrastructure.notification.webhook.WebhookNotificationChannel
import com.echonotify.core.infrastructure.persistence.PostgresNotificationRepository
import com.echonotify.core.infrastructure.resilience.CircuitBreakerNotificationChannel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.Duration

fun main() = runBlocking {
    val log = LoggerFactory.getLogger("EchoNotifyConsumer")
    val config = com.typesafe.config.ConfigFactory.load()

    val jdbcUrl = config.getString("echo-notify.database.url")
    val dbUser = config.getString("echo-notify.database.user")
    val dbPass = config.getString("echo-notify.database.password")
    val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
    val groupId = config.getString("echo-notify.kafka.groupId")
    val maxAttempts = config.getInt("echo-notify.retry.maxAttempts")

    val database = DatabaseFactory.create(jdbcUrl, dbUser, dbPass)
    val repository = PostgresNotificationRepository(database)

    val producer = KafkaProducer<String, String>(KafkaClientFactory.producerProps(bootstrapServers))
    val publisher = KafkaNotificationPublisher(producer)
    val httpClient = HttpClient(CIO)

    val channels = listOf(
        CircuitBreakerNotificationChannel(EmailNotificationChannel(Json)),
        CircuitBreakerNotificationChannel(WebhookNotificationChannel(httpClient, Json))
    )

    val useCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(RetryPolicy(maxAttempts = maxAttempts)),
        maxAttempts = maxAttempts
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
            var processedAll = true
            for (record in records) {
                val result = runCatching {
                    val message = Json.decodeFromString<NotificationMessage>(record.value())
                    useCase.execute(message.toDomain())
                }

                if (result.isFailure) {
                    processedAll = false
                    log.error("Failed to process record key={} topic={}", record.key(), record.topic(), result.exceptionOrNull())
                    break
                }
            }

            if (processedAll && records.count() > 0) {
                kafkaConsumer.commitSync()
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
