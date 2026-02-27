package com.echonotify.api

import com.echonotify.api.routes.healthRoutes
import com.echonotify.api.routes.notificationRoutes
import com.echonotify.core.application.usecase.QueryNotificationStatusUseCase
import com.echonotify.core.application.usecase.ReprocessDlqUseCase
import com.echonotify.core.application.usecase.SendNotificationUseCase
import com.echonotify.core.infrastructure.config.DatabaseFactory
import com.echonotify.core.infrastructure.messaging.KafkaClientFactory
import com.echonotify.core.infrastructure.messaging.KafkaNotificationPublisher
import com.echonotify.core.infrastructure.notification.email.EmailNotificationChannel
import com.echonotify.core.infrastructure.notification.webhook.WebhookNotificationChannel
import com.echonotify.core.infrastructure.persistence.PostgresNotificationRepository
import com.echonotify.core.infrastructure.resilience.CircuitBreakerNotificationChannel
import com.echonotify.core.infrastructure.resilience.ResilienceRateLimiterAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.module() {
    val config = environment.config

    val jdbcUrl = config.property("echo-notify.database.url").getString()
    val dbUser = config.property("echo-notify.database.user").getString()
    val dbPass = config.property("echo-notify.database.password").getString()
    val bootstrapServers = config.property("echo-notify.kafka.bootstrapServers").getString()
    val database = DatabaseFactory.create(jdbcUrl, dbUser, dbPass)
    val repository = PostgresNotificationRepository(database)

    val producer = KafkaProducer<String, String>(KafkaClientFactory.producerProps(bootstrapServers))

    val publisher = KafkaNotificationPublisher(producer)
    val rateLimiter = ResilienceRateLimiterAdapter()
    val httpClient = HttpClient(CIO)

    listOf(
        CircuitBreakerNotificationChannel(EmailNotificationChannel(Json)),
        CircuitBreakerNotificationChannel(WebhookNotificationChannel(httpClient, Json))
    )

    val sendNotificationUseCase = SendNotificationUseCase(repository, publisher, rateLimiter)
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
        notificationRoutes(sendNotificationUseCase, queryNotificationStatusUseCase, reprocessDlqUseCase)
        healthRoutes(meterRegistry)
    }
    configureOpenApi()

    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopped) {
        producer.close()
        httpClient.close()
    }
}
