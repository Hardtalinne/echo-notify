package com.echonotify.core.application.service

import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationChannelPort

class NotificationChannelRegistry(
    private val channels: List<NotificationChannelPort>
) {
    fun find(type: NotificationType): NotificationChannelPort =
        channels.firstOrNull { it.supports(type) }
            ?: error("No notification channel strategy configured for type=$type")
}
