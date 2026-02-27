package com.echonotify.core.application.port

import com.echonotify.core.domain.model.Notification

interface NotificationPublisherPort {
    suspend fun publish(topic: String, notification: Notification)
}
