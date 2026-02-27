package com.echonotify.core.application.usecase

import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.domain.port.NotificationRepository
import java.time.Instant

class PublishOutboxUseCase(
    private val repository: NotificationRepository,
    private val publisher: NotificationPublisherPort
) {
    suspend fun execute(limit: Int = 100): Int {
        val pending = repository.fetchPendingOutbox(limit)
        pending.forEach { event ->
            runCatching {
                val notification = repository.findById(event.notificationId)
                    ?: error("notification not found for outbox ${event.id}")
                publisher.publish(event.topic, notification)
                repository.markOutboxPublished(event.id)
            }.onFailure {
                repository.markOutboxFailed(
                    outboxId = event.id,
                    errorMessage = it.message ?: "outbox publish failure",
                    nextAttemptAt = Instant.now().plusSeconds(5)
                )
            }
        }
        return pending.size
    }
}
