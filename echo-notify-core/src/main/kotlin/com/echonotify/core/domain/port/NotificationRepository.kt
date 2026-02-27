package com.echonotify.core.domain.port

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.OutboxEvent
import java.time.Instant
import java.util.UUID

interface NotificationRepository {
    suspend fun save(notification: Notification): Notification
    suspend fun saveWithOutbox(notification: Notification, topic: String): Notification
    suspend fun findById(id: UUID): Notification?
    suspend fun findByIds(ids: Set<UUID>): Map<UUID, Notification>
    suspend fun findByIdempotencyKey(key: String): Notification?
    suspend fun findByStatus(status: NotificationStatus, limit: Int = 100): List<Notification>
    suspend fun fetchPendingOutbox(limit: Int = 100): List<OutboxEvent>
    suspend fun markOutboxPublished(outboxId: UUID)
    suspend fun markOutboxFailed(outboxId: UUID, errorMessage: String, nextAttemptAt: Instant)
}
