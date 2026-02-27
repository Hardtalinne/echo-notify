package com.echonotify.core.application.usecase

import com.echonotify.core.application.port.RateLimiterPort
import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationRepository
import com.echonotify.core.infrastructure.resilience.InMemoryIdempotencyLockAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendNotificationUseCaseTest {

    @Test
    fun `should enqueue notification outbox when idempotency key does not exist`() = runBlocking {
        val repository = mockk<NotificationRepository>()
        val rateLimiter = mockk<RateLimiterPort>()
        val idempotencyLock = InMemoryIdempotencyLockAdapter()

        every { rateLimiter.isAllowed(any()) } returns true
        coEvery { repository.findByIdempotencyKey("idem-1") } returns null
        coEvery { repository.saveWithOutbox(any(), any()) } answers { firstArg() }

        val useCase = SendNotificationUseCase(repository, rateLimiter, idempotencyLock)
        val created = useCase.execute(
            CreateNotificationCommand(
                type = NotificationType.EMAIL,
                recipient = "user@example.com",
                clientId = "client-a",
                payload = "{}",
                idempotencyKey = "idem-1"
            )
        )

        assertEquals(NotificationStatus.PENDING, created.status)
        coVerify(exactly = 1) { repository.saveWithOutbox(any(), any()) }
    }

    @Test
    fun `should return existing notification for duplicated idempotency key`() = runBlocking {
        val repository = mockk<NotificationRepository>()
        val rateLimiter = mockk<RateLimiterPort>()
        val idempotencyLock = InMemoryIdempotencyLockAdapter()

        val existing = Notification(
            type = NotificationType.EMAIL,
            recipient = "user@example.com",
            clientId = "client-a",
            payload = "{}",
            idempotencyKey = "idem-1"
        )

        coEvery { repository.findByIdempotencyKey("idem-1") } returns existing

        val useCase = SendNotificationUseCase(repository, rateLimiter, idempotencyLock)
        val result = useCase.execute(
            CreateNotificationCommand(
                type = NotificationType.EMAIL,
                recipient = "user@example.com",
                clientId = "client-a",
                payload = "{}",
                idempotencyKey = "idem-1"
            )
        )

        assertEquals(existing.id, result.id)
        coVerify(exactly = 0) { repository.saveWithOutbox(any(), any()) }
    }
}
