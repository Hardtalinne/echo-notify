package com.echonotify.core.application.port

interface IdempotencyLockPort {
    suspend fun <T> withLock(key: String, block: suspend () -> T): T
}
