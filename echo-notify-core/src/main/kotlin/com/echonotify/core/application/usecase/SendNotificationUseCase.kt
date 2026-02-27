package com.echonotify.core.application.usecase

import com.echonotify.core.application.config.TopicNames
import com.echonotify.core.application.port.IdempotencyLockPort
import com.echonotify.core.application.port.RateLimiterPort
import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.model.NotificationType
import com.echonotify.core.domain.port.NotificationRepository

data class CreateNotificationCommand(
    val type: NotificationType,
    val recipient: String,
    val clientId: String,
    val payload: String,
    val idempotencyKey: String
)

class SendNotificationUseCase(
    private val repository: NotificationRepository,
    private val rateLimiter: RateLimiterPort,
    private val idempotencyLock: IdempotencyLockPort
) {
    suspend fun execute(command: CreateNotificationCommand): Notification {
        return idempotencyLock.withLock(command.idempotencyKey) {
            val existing = repository.findByIdempotencyKey(command.idempotencyKey)
            if (existing != null) {
                return@withLock existing
            }

            check(rateLimiter.isAllowed("type:${command.type}")) { "Rate limit exceeded for type" }
            check(rateLimiter.isAllowed("recipient:${command.recipient}")) { "Rate limit exceeded for recipient" }
            check(rateLimiter.isAllowed("client:${command.clientId}")) { "Rate limit exceeded for client" }

            repository.saveWithOutbox(
                Notification(
                    type = command.type,
                    recipient = command.recipient,
                    clientId = command.clientId,
                    payload = command.payload,
                    idempotencyKey = command.idempotencyKey
                ),
                TopicNames.SEND
            )
        }
    }
}
