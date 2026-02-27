package com.echonotify.core.infrastructure.persistence

import com.echonotify.core.infrastructure.config.DatabaseFactory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresNotificationRepositoryIT {

    @Container
    private val postgres = PostgreSQLContainer("postgres:16")

    @Test
    fun `should run flyway migrations and create required tables`() {
        assumeTrue(Runtime.version().feature() >= 17)
        assertTrue(postgres.isRunning)

        val db: Database = DatabaseFactory.create(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        transaction(db) {
            val notificationsExists = exec(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'notifications')"
            ) { rs ->
                rs.next()
                rs.getBoolean(1)
            } ?: false

            val outboxExists = exec(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'notification_outbox')"
            ) { rs ->
                rs.next()
                rs.getBoolean(1)
            } ?: false

            assertTrue(notificationsExists)
            assertTrue(outboxExists)
        }
    }
}
