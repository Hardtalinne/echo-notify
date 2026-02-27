package com.echonotify.core.domain.model

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val id: UUID,
    val notificationId: UUID,
    val topic: String,
    val status: OutboxStatus,
    val attempts: Int,
    val lastError: String? = null,
    val nextAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
