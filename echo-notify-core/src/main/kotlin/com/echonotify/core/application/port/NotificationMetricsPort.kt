package com.echonotify.core.application.port

interface NotificationMetricsPort {
    fun recordSendAttempt(type: String)

    fun recordSendResult(
        type: String,
        outcome: String,
        errorCategory: String?,
        durationNanos: Long
    )

    fun recordRetryScheduled(type: String, errorCategory: String?)

    fun recordDeadLettered(type: String, errorCategory: String?)
}

object NoOpNotificationMetrics : NotificationMetricsPort {
    override fun recordSendAttempt(type: String) = Unit

    override fun recordSendResult(type: String, outcome: String, errorCategory: String?, durationNanos: Long) = Unit

    override fun recordRetryScheduled(type: String, errorCategory: String?) = Unit

    override fun recordDeadLettered(type: String, errorCategory: String?) = Unit
}
