package com.echonotify.core.application.service

import com.echonotify.core.application.config.RetryPolicy
import com.echonotify.core.application.config.RetryPolicyByType
import com.echonotify.core.domain.model.NotificationType
import java.time.Instant
import kotlin.math.pow
import kotlin.random.Random

class BackoffCalculator(
    private val retryPolicyByType: RetryPolicyByType
) {
    fun nextRetryAt(type: NotificationType, retryCount: Int, now: Instant = Instant.now()): Instant {
        val retryPolicy: RetryPolicy = retryPolicyByType.forType(type)
        val exponential = (retryPolicy.baseDelayMillis.toDouble() * 2.0.pow(retryCount.toDouble())).toLong()
        val jitter = Random.nextLong(0, 250)
        val delay = (exponential + jitter).coerceAtMost(retryPolicy.maxDelayMillis)
        return now.plusMillis(delay)
    }

    fun maxAttempts(type: NotificationType): Int = retryPolicyByType.forType(type).maxAttempts
}
