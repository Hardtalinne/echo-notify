package com.echonotify.core.domain.port

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationType

interface NotificationChannelPort {
    fun supports(type: NotificationType): Boolean
    suspend fun send(notification: Notification): Result<Unit>
}
