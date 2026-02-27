package com.echonotify.core.infrastructure.resilience

import com.echonotify.core.application.port.IdempotencyLockPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class InMemoryIdempotencyLockAdapter : IdempotencyLockPort {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val lock = locks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            block()
        }
    }
}
