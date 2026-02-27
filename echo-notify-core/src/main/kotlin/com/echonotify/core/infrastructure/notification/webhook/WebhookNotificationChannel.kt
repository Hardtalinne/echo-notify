package com.echonotify.core.infrastructure.notification.webhook

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationChannelPort
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WebhookPayload(
    val url: String,
    val body: String
)

class WebhookNotificationChannel(
    private val httpClient: HttpClient,
    private val json: Json = Json
) : NotificationChannelPort {
    override fun supports(type: NotificationType): Boolean = type == NotificationType.WEBHOOK

    override suspend fun send(notification: Notification): Result<Unit> = runCatching {
        val payload = json.decodeFromString<WebhookPayload>(notification.payload)
        val response = httpClient.post(payload.url) {
            contentType(ContentType.Application.Json)
            setBody(payload.body)
        }
        require(response.status.value in 200..299) { "Webhook call failed with status=${response.status.value}" }
    }
}
