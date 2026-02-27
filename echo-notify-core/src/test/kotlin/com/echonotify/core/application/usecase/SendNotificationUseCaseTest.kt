package com.echonotify.core.application.usecase

import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.application.port.RateLimiterPort
import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendNotificationUseCaseTest {

    @Test
    fun `should publish notification when idempotency key does not exist`() = runBlocking {
        val repository = mockk<NotificationRepository>()
        val publisher = mockk<NotificationPublisherPort>(relaxed = true)
        val rateLimiter = mockk<RateLimiterPort>()

        every { rateLimiter.isAllowed(any()) } returns true
        coEvery { repository.findByIdempotencyKey("idem-1") } returns null
        coEvery { repository.save(any()) } answers { firstArg() }

        val useCase = SendNotificationUseCase(repository, publisher, rateLimiter)
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
        coVerify(exactly = 1) { publisher.publish(any(), any()) }
    }

    @Test
    fun `should return existing notification for duplicated idempotency key`() = runBlocking {
        val repository = mockk<NotificationRepository>()
        val publisher = mockk<NotificationPublisherPort>(relaxed = true)
        val rateLimiter = mockk<RateLimiterPort>()

        val existing = Notification(
            type = NotificationType.EMAIL,
            recipient = "user@example.com",
            clientId = "client-a",
            payload = "{}",
            idempotencyKey = "idem-1"
        )

        coEvery { repository.findByIdempotencyKey("idem-1") } returns existing

        val useCase = SendNotificationUseCase(repository, publisher, rateLimiter)
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
        coVerify(exactly = 0) { publisher.publish(any(), any()) }
    }
}
