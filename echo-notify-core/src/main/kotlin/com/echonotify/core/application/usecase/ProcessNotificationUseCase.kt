package com.echonotify.core.application.usecase

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.port.NoOpNotificationMetrics
import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.application.port.NotificationMetricsPort
import com.echonotify.core.application.service.BackoffCalculator
import com.echonotify.core.application.service.NotificationChannelRegistry
import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationStatus
import com.echonotify.core.domain.port.NotificationRepository

class ProcessNotificationUseCase(
    private val repository: NotificationRepository,
    private val registry: NotificationChannelRegistry,
    private val publisher: NotificationPublisherPort,
    private val backoffCalculator: BackoffCalculator,
    private val metrics: NotificationMetricsPort = NoOpNotificationMetrics
) {
    suspend fun execute(notification: Notification) {
        val type = notification.type.name
        val startedAt = System.nanoTime()
        metrics.recordSendAttempt(type)

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
            metrics.recordSendResult(
                type = type,
                outcome = "success",
                errorCategory = null,
                durationNanos = System.nanoTime() - startedAt
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
            metrics.recordSendResult(
                type = type,
                outcome = "dead_letter",
                errorCategory = "DELIVERY",
                durationNanos = System.nanoTime() - startedAt
            )
            metrics.recordDeadLettered(type, "DELIVERY")
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
        metrics.recordSendResult(
            type = type,
            outcome = "retry",
            errorCategory = "DELIVERY",
            durationNanos = System.nanoTime() - startedAt
        )
        metrics.recordRetryScheduled(type, "DELIVERY")
    }
}
