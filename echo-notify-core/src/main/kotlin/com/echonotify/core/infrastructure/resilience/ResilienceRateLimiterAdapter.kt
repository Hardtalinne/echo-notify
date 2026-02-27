package com.echonotify.core.infrastructure.resilience

import com.echonotify.core.application.port.RateLimiterPort
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ResilienceRateLimiterAdapter(
    private val limitPerSecondByPrefix: Map<String, Int> = mapOf(
        "type" to 100,
        "recipient" to 60,
        "client" to 200
    )
) : RateLimiterPort {
    private data class WindowCounter(
        val count: AtomicInteger,
        @Volatile var windowStartMillis: Long
    )

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
            val prefix = key.substringBefore(':', missingDelimiterValue = "client")
            val limit = limitPerSecondByPrefix[prefix] ?: 100
            return current <= limit
        }
    }
}
