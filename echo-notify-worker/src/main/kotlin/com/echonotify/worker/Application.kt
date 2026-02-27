package com.echonotify.worker

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
import com.echonotify.core.application.usecase.RetryNotificationUseCase
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.Duration

fun main() = runBlocking {
    val log = LoggerFactory.getLogger("EchoNotifyWorker")
    val config = com.typesafe.config.ConfigFactory.load()

    val jdbcUrl = config.getString("echo-notify.database.url")
    val dbUser = config.getString("echo-notify.database.user")
    val dbPass = config.getString("echo-notify.database.password")
    val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
    val retryGroupId = config.getString("echo-notify.kafka.retryGroupId")
    val dlqGroupId = config.getString("echo-notify.kafka.dlqGroupId")
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

    val processUseCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(RetryPolicy(maxAttempts = maxAttempts)),
        maxAttempts = maxAttempts
    )
    val retryUseCase = RetryNotificationUseCase(processUseCase)

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
    })

    launch {
        while (true) {
            val records = retryConsumer.poll(Duration.ofMillis(500))
            var processedAll = true
            for (record in records) {
                val result = runCatching {
                    val message = Json.decodeFromString<NotificationMessage>(record.value())
                    retryUseCase.execute(message.toDomain())
                }

                if (result.isFailure) {
                    processedAll = false
                    log.error("Failed to process retry record key={} topic={}", record.key(), record.topic(), result.exceptionOrNull())
                    break
                }
            }

            if (processedAll && records.count() > 0) {
                retryConsumer.commitSync()
            }
            delay(100)
        }
    }

    launch {
        while (true) {
            val records = dlqConsumer.poll(Duration.ofMillis(500))
            for (record in records) {
                log.warn("DLQ notification received id={} payload={}", record.key(), record.value())
            }
            if (records.count() > 0) {
                dlqConsumer.commitSync()
            }
            delay(100)
        }
    }.join()
}
