package com.echonotify.core.infrastructure.notification

import com.echonotify.core.domain.port.NotificationChannelPort
import com.echonotify.core.infrastructure.notification.email.EmailNotificationChannel
import com.echonotify.core.infrastructure.notification.webhook.WebhookNotificationChannel
import com.echonotify.core.infrastructure.resilience.CircuitBreakerNotificationChannel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

object NotificationChannelFactory {
    fun build(httpClient: HttpClient, json: Json = Json): List<NotificationChannelPort> = listOf(
        CircuitBreakerNotificationChannel(EmailNotificationChannel(json)),
        CircuitBreakerNotificationChannel(WebhookNotificationChannel(httpClient, json))
    )
}
