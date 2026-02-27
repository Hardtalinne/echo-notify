package com.echonotify.core.domain.port

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import java.util.UUID

interface NotificationRepository {
    suspend fun save(notification: Notification): Notification
    suspend fun findById(id: UUID): Notification?
    suspend fun findByIdempotencyKey(key: String): Notification?
    suspend fun findByStatus(status: NotificationStatus, limit: Int = 100): List<Notification>
}
