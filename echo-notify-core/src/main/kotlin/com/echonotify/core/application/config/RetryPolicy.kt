package com.echonotify.core.application.config

data class RetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 300_000
)
