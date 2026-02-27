package com.echonotify.core.application.port

interface RateLimiterPort {
    fun isAllowed(key: String): Boolean
}
