package com.echonotify.core.infrastructure.persistence

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresNotificationRepositoryIT {

    @Container
    private val postgres = PostgreSQLContainer("postgres:16")

    @Test
    fun `container should start for integration tests`() {
        assert(postgres.isRunning)
    }
}
