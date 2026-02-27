package com.echonotify.core.application.usecase

import com.echonotify.core.domain.model.Notification
import com.echonotify.core.domain.port.NotificationRepository
import java.util.UUID

class QueryNotificationStatusUseCase(
    private val repository: NotificationRepository
) {
    suspend fun execute(id: UUID): Notification? = repository.findById(id)
}
