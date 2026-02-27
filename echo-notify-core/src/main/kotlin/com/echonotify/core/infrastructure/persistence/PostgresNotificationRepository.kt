package com.echonotify.core.infrastructure.persistence

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.model.OutboxEvent
import com.echonotify.core.domain.model.OutboxStatus
import com.echonotify.core.domain.port.NotificationRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

class PostgresNotificationRepository(
    private val database: Database
) : NotificationRepository {

    override suspend fun save(notification: Notification): Notification = newSuspendedTransaction(Dispatchers.IO, database) {
        upsertNotification(notification)
        notification
    }

    override suspend fun saveWithOutbox(notification: Notification, topic: String): Notification =
        newSuspendedTransaction(Dispatchers.IO, database) {
            upsertNotification(notification)
            val now = Instant.now()
            OutboxTable.insert {
                it[id] = UUID.randomUUID()
                it[notificationId] = notification.id
                it[OutboxTable.topic] = topic
                it[status] = OutboxStatus.PENDING.name
                it[attempts] = 0
                it[lastError] = null
                it[nextAttemptAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }
            notification
        }

    private fun upsertNotification(notification: Notification) {
        val updatedRows = NotificationTable.update({ NotificationTable.id eq notification.id }) {
            it[type] = notification.type.name
            it[recipient] = notification.recipient
            it[clientId] = notification.clientId
            it[payload] = notification.payload
            it[idempotencyKey] = notification.idempotencyKey
            it[status] = notification.status.name
            it[retryCount] = notification.retryCount
            it[errorMessage] = notification.errorMessage
            it[errorCode] = notification.errorCode
            it[errorCategory] = notification.errorCategory
            it[retryable] = notification.retryable
            it[nextRetryAt] = notification.nextRetryAt
            it[updatedAt] = notification.updatedAt
        }

        if (updatedRows == 0) {
            NotificationTable.insert {
                it[id] = notification.id
                it[type] = notification.type.name
                it[recipient] = notification.recipient
                it[clientId] = notification.clientId
                it[payload] = notification.payload
                it[idempotencyKey] = notification.idempotencyKey
                it[status] = notification.status.name
                it[retryCount] = notification.retryCount
                it[errorMessage] = notification.errorMessage
                it[errorCode] = notification.errorCode
                it[errorCategory] = notification.errorCategory
                it[retryable] = notification.retryable
                it[nextRetryAt] = notification.nextRetryAt
                it[createdAt] = notification.createdAt
                it[updatedAt] = notification.updatedAt
            }
        }
    }

    override suspend fun findById(id: UUID): Notification? = newSuspendedTransaction(Dispatchers.IO, database) {
        NotificationTable.selectAll().where { NotificationTable.id eq id }.firstOrNull()?.toDomain()
    }

    override suspend fun findByIdempotencyKey(key: String): Notification? = newSuspendedTransaction(Dispatchers.IO, database) {
        NotificationTable.selectAll().where { NotificationTable.idempotencyKey eq key }.firstOrNull()?.toDomain()
    }

    override suspend fun findByStatus(status: NotificationStatus, limit: Int): List<Notification> = newSuspendedTransaction(Dispatchers.IO, database) {
        NotificationTable
            .selectAll()
            .where { NotificationTable.status eq status.name }
            .limit(limit)
            .map { it.toDomain() }
    }

    override suspend fun fetchPendingOutbox(limit: Int): List<OutboxEvent> = newSuspendedTransaction(Dispatchers.IO, database) {
        val now = Instant.now()
        OutboxTable
            .selectAll()
            .where {
                (OutboxTable.status eq OutboxStatus.PENDING.name) and
                    ((OutboxTable.nextAttemptAt.isNull()) or (OutboxTable.nextAttemptAt lessEq now))
            }
            .orderBy(OutboxTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toOutboxDomain() }
    }

    override suspend fun markOutboxPublished(outboxId: UUID) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            OutboxTable.update({ OutboxTable.id eq outboxId }) {
                it[status] = OutboxStatus.PUBLISHED.name
                it[updatedAt] = Instant.now()
                it[lastError] = null
            }
        }
    }

    override suspend fun markOutboxFailed(outboxId: UUID, errorMessage: String, nextAttemptAt: Instant) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            OutboxTable.update({ OutboxTable.id eq outboxId }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it.update(attempts, attempts + 1)
                }
                it[status] = OutboxStatus.PENDING.name
                it[lastError] = errorMessage
                it[OutboxTable.nextAttemptAt] = nextAttemptAt
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun ResultRow.toDomain() = Notification(
        id = this[NotificationTable.id],
        type = NotificationType.valueOf(this[NotificationTable.type]),
        recipient = this[NotificationTable.recipient],
        clientId = this[NotificationTable.clientId],
        payload = this[NotificationTable.payload],
        idempotencyKey = this[NotificationTable.idempotencyKey],
        status = NotificationStatus.valueOf(this[NotificationTable.status]),
        retryCount = this[NotificationTable.retryCount],
        errorMessage = this[NotificationTable.errorMessage],
        errorCode = this[NotificationTable.errorCode],
        errorCategory = this[NotificationTable.errorCategory],
        retryable = this[NotificationTable.retryable],
        nextRetryAt = this[NotificationTable.nextRetryAt],
        createdAt = this[NotificationTable.createdAt],
        updatedAt = this[NotificationTable.updatedAt]
    )

    private fun ResultRow.toOutboxDomain() = OutboxEvent(
        id = this[OutboxTable.id],
        notificationId = this[OutboxTable.notificationId],
        topic = this[OutboxTable.topic],
        status = OutboxStatus.valueOf(this[OutboxTable.status]),
        attempts = this[OutboxTable.attempts],
        lastError = this[OutboxTable.lastError],
        nextAttemptAt = this[OutboxTable.nextAttemptAt],
        createdAt = this[OutboxTable.createdAt],
        updatedAt = this[OutboxTable.updatedAt]
    )
}
