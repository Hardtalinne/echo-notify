package com.echonotify.worker

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.application.usecase.ProcessNotificationUseCase
import com.echonotify.core.application.usecase.RetryNotificationUseCase
import com.echonotify.core.infrastructure.config.DatabaseFactory
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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

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

    val producer = KafkaProducer<String, String>(
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
    )

    val publisher = KafkaNotificationPublisher(producer)
    val channels = listOf(
        CircuitBreakerNotificationChannel(EmailNotificationChannel(Json)),
        CircuitBreakerNotificationChannel(WebhookNotificationChannel(HttpClient(CIO), Json))
    )

    val processUseCase = ProcessNotificationUseCase(
        repository = repository,
        registry = NotificationChannelRegistry(channels),
        publisher = publisher,
        backoffCalculator = BackoffCalculator(RetryPolicy(maxAttempts = maxAttempts)),
        maxAttempts = maxAttempts
    )
    val retryUseCase = RetryNotificationUseCase(processUseCase)

    val retryConsumer = KafkaConsumer<String, String>(consumerProps(bootstrapServers, retryGroupId))
    retryConsumer.subscribe(listOf(TopicNames.RETRY))

    val dlqConsumer = KafkaConsumer<String, String>(consumerProps(bootstrapServers, dlqGroupId))
    dlqConsumer.subscribe(listOf(TopicNames.DLQ))

    launch {
        while (true) {
            val records = retryConsumer.poll(Duration.ofMillis(500))
            records.forEach { record ->
                runCatching {
                    val message = Json.decodeFromString<NotificationMessage>(record.value())
                    retryUseCase.execute(message.toDomain())
                }
            }
            retryConsumer.commitSync()
            delay(100)
        }
    }

    launch {
        while (true) {
            val records = dlqConsumer.poll(Duration.ofMillis(500))
            records.forEach { record ->
                log.warn("DLQ notification received id={} payload={}", record.key(), record.value())
            }
            dlqConsumer.commitSync()
            delay(100)
        }
    }.join()
}

private fun consumerProps(bootstrapServers: String, groupId: String): Properties = Properties().apply {
    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
}
