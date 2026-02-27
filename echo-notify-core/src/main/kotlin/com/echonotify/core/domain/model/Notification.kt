package com.echonotify.core.domain.model

import java.time.Instant
import java.util.UUID

data class Notification(
    val id: UUID = UUID.randomUUID(),
    val type: NotificationType,
    val recipient: String,
    val clientId: String,
    val payload: String,
    val idempotencyKey: String,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val errorCategory: String? = null,
    val retryable: Boolean? = null,
    val nextRetryAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
