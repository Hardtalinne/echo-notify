package com.echonotify.core.application.usecase

import com.echonotify.core.domain.model.Notification
import java.time.Instant

class RetryNotificationUseCase(
    private val processNotificationUseCase: ProcessNotificationUseCase
) {
    suspend fun execute(notification: Notification, now: Instant = Instant.now()): Boolean {
        val next = notification.nextRetryAt
        if (next != null && next.isAfter(now)) {
            return false
        }
        processNotificationUseCase.execute(notification)
        return true
    }
}
