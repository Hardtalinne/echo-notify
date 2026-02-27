package com.echonotify.core.infrastructure.observability

import com.echonotify.core.application.port.NotificationMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class NotificationMetrics(
    private val meterRegistry: MeterRegistry
) : NotificationMetricsPort {
    private val sendTotal: Counter = Counter.builder("echo_notify_send_total").register(meterRegistry)
    private val retryTotal: Counter = Counter.builder("echo_notify_retry_total").register(meterRegistry)
    private val dlqTotal: Counter = Counter.builder("echo_notify_dlq_total").register(meterRegistry)

    override fun recordSendAttempt(type: String) {
        sendTotal.increment()
        meterRegistry.counter(
            "echo_notify_send_total",
            "type",
            type,
            "outcome",
            "attempt"
        ).increment()
    }

    override fun recordSendResult(type: String, outcome: String, errorCategory: String?, durationNanos: Long) {
        meterRegistry.counter(
            "echo_notify_send_total",
            "type",
            type,
            "outcome",
            outcome
        ).increment()

        if (!errorCategory.isNullOrBlank()) {
            meterRegistry.counter(
                "echo_notify_error_total",
                "type",
                type,
                "error_category",
                errorCategory,
                "outcome",
                outcome
            ).increment()
        }

        Timer.builder("echo_notify_send_latency")
            .tag("type", type)
            .tag("outcome", outcome)
            .tag("error_category", errorCategory ?: "none")
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }

    override fun recordRetryScheduled(type: String, errorCategory: String?) {
        retryTotal.increment()
        meterRegistry.counter(
            "echo_notify_retry_total",
            "type",
            type,
            "error_category",
            errorCategory ?: "unknown"
        ).increment()
    }

    override fun recordDeadLettered(type: String, errorCategory: String?) {
        dlqTotal.increment()
        meterRegistry.counter(
            "echo_notify_dlq_total",
            "type",
            type,
            "error_category",
            errorCategory ?: "unknown"
        ).increment()
    }
}
