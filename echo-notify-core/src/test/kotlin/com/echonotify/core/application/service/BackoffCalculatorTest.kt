package com.echonotify.core.application.service

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.RetryPolicyByType
import com.echonotify.core.domain.model.NotificationType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BackoffCalculatorTest {

    @Test
    fun `should increase retry delay exponentially`() {
        val calc = BackoffCalculator(
            RetryPolicyByType(
                defaultPolicy = RetryPolicy(baseDelayMillis = 100, maxDelayMillis = 10_000)
            )
        )
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val first = calc.nextRetryAt(NotificationType.EMAIL, 1, now)
        val second = calc.nextRetryAt(NotificationType.EMAIL, 2, now)

        assertTrue(second.isAfter(first))
    }
}
