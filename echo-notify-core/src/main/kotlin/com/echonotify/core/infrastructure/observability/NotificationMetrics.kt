package com.echonotify.core.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class NotificationMetrics(
    meterRegistry: MeterRegistry
) {
    private val sendTotal: Counter = Counter.builder("echo_notify_send_total").register(meterRegistry)
    private val retryTotal: Counter = Counter.builder("echo_notify_retry_total").register(meterRegistry)
    private val dlqTotal: Counter = Counter.builder("echo_notify_dlq_total").register(meterRegistry)

    fun incrementSend() = sendTotal.increment()
    fun incrementRetry() = retryTotal.increment()
    fun incrementDlq() = dlqTotal.increment()
}
