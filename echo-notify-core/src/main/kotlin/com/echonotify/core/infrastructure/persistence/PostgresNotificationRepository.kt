package com.echonotify.core.infrastructure.persistence

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class PostgresNotificationRepository(
    private val database: Database
) : NotificationRepository {

    override suspend fun save(notification: Notification): Notification = newSuspendedTransaction(Dispatchers.IO, database) {
        val updatedRows = NotificationTable.update({ NotificationTable.id eq notification.id }) {
            it[type] = notification.type.name
            it[recipient] = notification.recipient
            it[clientId] = notification.clientId
            it[payload] = notification.payload
            it[idempotencyKey] = notification.idempotencyKey
            it[status] = notification.status.name
            it[retryCount] = notification.retryCount
            it[errorMessage] = notification.errorMessage
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
                it[nextRetryAt] = notification.nextRetryAt
                it[createdAt] = notification.createdAt
                it[updatedAt] = notification.updatedAt
            }
        }
        notification
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
        nextRetryAt = this[NotificationTable.nextRetryAt],
        createdAt = this[NotificationTable.createdAt],
        updatedAt = this[NotificationTable.updatedAt]
    )
}
