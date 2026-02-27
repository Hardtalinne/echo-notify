package com.echonotify.core.infrastructure.resilience

import com.echonotify.core.application.port.RateLimiterPort
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ResilienceRateLimiterAdapter : RateLimiterPort {
    private data class WindowCounter(
        val count: AtomicInteger,
        @Volatile var windowStartMillis: Long
    )

    private val limitPerSecond = 100
    private val counters = ConcurrentHashMap<String, WindowCounter>()

    override fun isAllowed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val state = counters.computeIfAbsent(key) {
            WindowCounter(AtomicInteger(0), now)
        }

        synchronized(state) {
            if (now - state.windowStartMillis >= 1_000) {
                state.windowStartMillis = now
                state.count.set(0)
            }

            val current = state.count.incrementAndGet()
            return current <= limitPerSecond
        }
    }
}
