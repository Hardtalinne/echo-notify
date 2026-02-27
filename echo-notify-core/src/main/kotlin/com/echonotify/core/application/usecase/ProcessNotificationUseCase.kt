package com.echonotify.core.application.usecase

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.port.NotificationRepository

class ProcessNotificationUseCase(
    private val repository: NotificationRepository,
    private val registry: NotificationChannelRegistry,
    private val publisher: NotificationPublisherPort,
    private val backoffCalculator: BackoffCalculator
) {
    suspend fun execute(notification: Notification) {
        val channel = registry.find(notification.type)
        val result = channel.send(notification)

        if (result.isSuccess) {
            repository.save(
                notification.copy(
                    status = NotificationStatus.SENT,
                    updatedAt = java.time.Instant.now(),
                    errorMessage = null,
                    errorCode = null,
                    errorCategory = null,
                    retryable = null,
                    nextRetryAt = null
                )
            )
            return
        }

        val nextCount = notification.retryCount + 1
        val causeMessage = result.exceptionOrNull()?.message
        val retryable = true
        if (nextCount >= backoffCalculator.maxAttempts(notification.type)) {
            val dlqItem = repository.save(
                notification.copy(
                    status = NotificationStatus.DEAD_LETTERED,
                    retryCount = nextCount,
                    errorMessage = causeMessage,
                    errorCode = "DELIVERY_FAILED_MAX_ATTEMPTS",
                    errorCategory = "DELIVERY",
                    retryable = false,
                    updatedAt = java.time.Instant.now()
                )
            )
            publisher.publish(TopicNames.DLQ, dlqItem)
            return
        }

        val retryItem = repository.save(
            notification.copy(
                status = NotificationStatus.FAILED,
                retryCount = nextCount,
                errorMessage = causeMessage,
                errorCode = "DELIVERY_FAILED_RETRYABLE",
                errorCategory = "DELIVERY",
                retryable = retryable,
                nextRetryAt = backoffCalculator.nextRetryAt(notification.type, nextCount),
                updatedAt = java.time.Instant.now()
            )
        )
        publisher.publish(TopicNames.RETRY, retryItem)
    }
}
