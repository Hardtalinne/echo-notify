package com.echonotify.core.infrastructure.bootstrap

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.RetryPolicyByType
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.infrastructure.config.DatabaseFactory
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.messaging.KafkaNotificationPublisher
import com.echonotify.core.infrastructure.persistence.PostgresNotificationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.apache.kafka.clients.producer.KafkaProducer

object BootstrapFactory {
    fun createRepository(config: com.typesafe.config.Config): PostgresNotificationRepository {
        val database = DatabaseFactory.create(
            jdbcUrl = config.getString("echo-notify.database.url"),
            username = config.getString("echo-notify.database.user"),
            password = config.getString("echo-notify.database.password")
        )
        return PostgresNotificationRepository(database)
    }

    fun createProducer(config: com.typesafe.config.Config): KafkaProducer<String, String> {
        val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
        return KafkaProducer(KafkaClientFactory.producerProps(bootstrapServers))
    }

    fun createPublisher(producer: KafkaProducer<String, String>): KafkaNotificationPublisher =
        KafkaNotificationPublisher(producer)

    fun createHttpClient(): HttpClient = HttpClient(CIO)

    fun retryPolicyByType(config: com.typesafe.config.Config): RetryPolicyByType {
        val defaultRetryPolicy = readRetryPolicy(config, "echo-notify.retry.default", fallbackPath = "echo-notify.retry")
        return RetryPolicyByType(
            defaultPolicy = defaultRetryPolicy,
            perTypePolicy = mapOf(
                NotificationType.EMAIL to readRetryPolicy(config, "echo-notify.retry.byType.EMAIL", defaultRetryPolicy),
                NotificationType.WEBHOOK to readRetryPolicy(config, "echo-notify.retry.byType.WEBHOOK", defaultRetryPolicy)
            )
        )
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
}
