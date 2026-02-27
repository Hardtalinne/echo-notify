package com.echonotify.core.infrastructure.messaging

import com.echonotify.core.domain.model.Notification
import kotlinx.serialization.Serializable

@Serializable
data class NotificationMessage(
    val eventType: String,
    val notification: NotificationRecord,
    val timestamp: Long
)

@Serializable
data class NotificationRecord(
    val id: String,
    val type: String,
    val recipient: String,
    val clientId: String,
    val payload: String,
    val idempotencyKey: String,
    val status: String,
    val retryCount: Int,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val errorCategory: String? = null,
    val retryable: Boolean? = null,
    val nextRetryAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

fun Notification.toRecord(): NotificationRecord = NotificationRecord(
    id = id.toString(),
    type = type.name,
    recipient = recipient,
    clientId = clientId,
    payload = payload,
    idempotencyKey = idempotencyKey,
    status = status.name,
    retryCount = retryCount,
    errorMessage = errorMessage,
    errorCode = errorCode,
    errorCategory = errorCategory,
    retryable = retryable,
    nextRetryAt = nextRetryAt?.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
