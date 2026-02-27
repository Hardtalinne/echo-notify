package com.echonotify.core.infrastructure.resilience

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationChannelPort
import java.util.concurrent.atomic.AtomicInteger

class CircuitBreakerNotificationChannel(
    private val delegate: NotificationChannelPort
) : NotificationChannelPort {
    private val consecutiveFailures = AtomicInteger(0)
    @Volatile
    private var openUntilMillis: Long = 0
    private val failureThreshold = 5
    private val openWindowMillis = 30_000L

    override fun supports(type: NotificationType): Boolean = delegate.supports(type)

    override suspend fun send(notification: Notification): Result<Unit> {
        val now = System.currentTimeMillis()
        if (now < openUntilMillis) {
            return Result.failure(IllegalStateException("Circuit breaker open for ${notification.type}"))
        }

        return try {
            val result = delegate.send(notification)
            if (result.isSuccess) {
                consecutiveFailures.set(0)
            } else {
                registerFailure()
            }
            result
        } catch (ex: Throwable) {
            registerFailure()
            Result.failure(ex)
        }
    }

    private fun registerFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= failureThreshold) {
            openUntilMillis = System.currentTimeMillis() + openWindowMillis
            consecutiveFailures.set(0)
        }
    }
}
