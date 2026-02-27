package com.echonotify.core.infrastructure.notification.email

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationChannelPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EmailPayload(
    val subject: String,
    val body: String,
    val from: String
)

class EmailNotificationChannel(
    private val json: Json = Json
) : NotificationChannelPort {
    override fun supports(type: NotificationType): Boolean = type == NotificationType.EMAIL

    override suspend fun send(notification: Notification): Result<Unit> = runCatching {
        val payload = json.decodeFromString<EmailPayload>(notification.payload)
        require(payload.subject.isNotBlank()) { "Email subject is required" }
        require(payload.body.isNotBlank()) { "Email body is required" }
    }
}
