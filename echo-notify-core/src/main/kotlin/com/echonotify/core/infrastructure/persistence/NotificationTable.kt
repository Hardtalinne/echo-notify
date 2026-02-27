package com.echonotify.core.infrastructure.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object NotificationTable : Table("notifications") {
    val id = uuid("id")
    val type = varchar("type", 20)
    val recipient = varchar("recipient", 255)
    val clientId = varchar("client_id", 100)
    val payload = text("payload")
    val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
    val status = varchar("status", 30)
    val retryCount = integer("retry_count").default(0)
    val errorMessage = text("error_message").nullable()
    val nextRetryAt = timestamp("next_retry_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
