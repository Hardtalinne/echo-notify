package com.echonotify.core.infrastructure.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object OutboxTable : Table("notification_outbox") {
    val id = uuid("id")
    val notificationId = uuid("notification_id")
    val topic = varchar("topic", 100)
    val status = varchar("status", 30)
    val attempts = integer("attempts").default(0)
    val lastError = text("last_error").nullable()
    val nextAttemptAt = timestamp("next_attempt_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
