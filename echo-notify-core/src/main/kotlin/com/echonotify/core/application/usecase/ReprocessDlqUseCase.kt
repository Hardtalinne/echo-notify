package com.echonotify.core.application.usecase

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.port.NotificationRepository
import java.util.UUID

class ReprocessDlqUseCase(
    private val repository: NotificationRepository,
    private val publisher: NotificationPublisherPort
) {
    suspend fun execute(limit: Int = 100): List<UUID> {
        val items = repository.findByStatus(NotificationStatus.DEAD_LETTERED, limit)
        items.forEach { publisher.publish(TopicNames.SEND, it) }
        return items.map { it.id }
    }
}
