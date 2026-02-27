package com.echonotify.core.application.service

import com.echonotify.core.application.config.RetryPolicy
import java.time.Instant
import kotlin.math.pow
import kotlin.random.Random

class BackoffCalculator(
    private val retryPolicy: RetryPolicy
) {
    fun nextRetryAt(retryCount: Int, now: Instant = Instant.now()): Instant {
        val exponential = (retryPolicy.baseDelayMillis.toDouble() * 2.0.pow(retryCount.toDouble())).toLong()
        val jitter = Random.nextLong(0, 250)
        val delay = (exponential + jitter).coerceAtMost(retryPolicy.maxDelayMillis)
        return now.plusMillis(delay)
    }
}
