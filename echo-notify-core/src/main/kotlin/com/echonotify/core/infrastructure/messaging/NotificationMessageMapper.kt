package com.echonotify.core.infrastructure.messaging

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.NotificationType
import java.time.Instant
import java.util.UUID

fun NotificationMessage.toDomain(): Notification = Notification(
    id = UUID.fromString(notification.id),
    type = NotificationType.valueOf(notification.type),
    recipient = notification.recipient,
    clientId = notification.clientId,
    payload = notification.payload,
    idempotencyKey = notification.idempotencyKey,
    status = NotificationStatus.valueOf(notification.status),
    retryCount = notification.retryCount,
    errorMessage = notification.errorMessage,
    errorCode = notification.errorCode,
    errorCategory = notification.errorCategory,
    retryable = notification.retryable,
    nextRetryAt = notification.nextRetryAt?.let(Instant::parse),
    createdAt = Instant.parse(notification.createdAt),
    updatedAt = Instant.parse(notification.updatedAt)
)
