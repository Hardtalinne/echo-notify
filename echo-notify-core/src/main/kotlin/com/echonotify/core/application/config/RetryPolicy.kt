package com.echonotify.core.application.config

import com.echonotify.core.domain.model.NotificationType

data class RetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 300_000
)

class RetryPolicyByType(
    private val defaultPolicy: RetryPolicy,
    private val perTypePolicy: Map<NotificationType, RetryPolicy> = emptyMap()
) {
    fun forType(type: NotificationType): RetryPolicy = perTypePolicy[type] ?: defaultPolicy
}
