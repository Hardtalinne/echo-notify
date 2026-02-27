package com.echonotify.api

import com.echonotify.api.routes.healthRoutes
import com.echonotify.api.routes.notificationRoutes
import com.echonotify.api.security.ApiSecurity
import com.echonotify.core.application.usecase.QueryNotificationStatusUseCase
import com.echonotify.core.application.usecase.ReprocessDlqUseCase
import com.echonotify.core.application.usecase.SendNotificationUseCase
import com.echonotify.core.infrastructure.bootstrap.BootstrapFactory
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.resilience.InMemoryIdempotencyLockAdapter
import com.echonotify.core.infrastructure.resilience.ResilienceRateLimiterAdapter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import com.typesafe.config.Config
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import java.util.concurrent.TimeUnit

fun Application.module() {
    val config = com.typesafe.config.ConfigFactory.load()
    val bootstrapServers = config.getString("echo-notify.kafka.bootstrapServers")
    val repository = BootstrapFactory.createRepository(config)
    val producer = BootstrapFactory.createProducer(config)
    val adminClient = AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers))
    val publisher = BootstrapFactory.createPublisher(producer)
    val apiSecurity = ApiSecurity(config)
    val rateLimiter = ResilienceRateLimiterAdapter(
        limitPerSecondByPrefix = mapOf(
            "type" to config.getIntOrDefault("echo-notify.rateLimit.type", 100),
            "recipient" to config.getIntOrDefault("echo-notify.rateLimit.recipient", 60),
            "client" to config.getIntOrDefault("echo-notify.rateLimit.client", 200)
        )
    )
    val idempotencyLock = InMemoryIdempotencyLockAdapter()

    val sendNotificationUseCase = SendNotificationUseCase(repository, rateLimiter, idempotencyLock)
    val queryNotificationStatusUseCase = QueryNotificationStatusUseCase(repository)
    val reprocessDlqUseCase = ReprocessDlqUseCase(repository, publisher)

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "unexpected error")))
        }
    }

    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { this.registry = meterRegistry }

    routing {
        notificationRoutes(sendNotificationUseCase, queryNotificationStatusUseCase, reprocessDlqUseCase, apiSecurity)
        healthRoutes(
            meterRegistry = meterRegistry,
            dbProbe = {
                runCatching {
                    repository.findByStatus(com.echonotify.core.domain.model.NotificationStatus.PENDING, 1)
                    true
                }.getOrDefault(false)
            },
            kafkaProbe = {
                runCatching {
                    adminClient.listTopics().names().get(2, TimeUnit.SECONDS)
                    true
                }.getOrDefault(false)
            }
        )
    }
    configureOpenApi()

    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopped) {
        adminClient.close()
        producer.close()
    }
}

private fun Config.getIntOrDefault(path: String, defaultValue: Int): Int {
    return if (hasPath(path)) getInt(path) else defaultValue
}
