package com.echonotify.api.routes

import com.echonotify.api.dto.CreateNotificationRequest
import com.echonotify.api.dto.CreateNotificationResponse
import com.echonotify.api.dto.NotificationStatusResponse
import com.echonotify.core.application.usecase.CreateNotificationCommand
import com.echonotify.core.application.usecase.QueryNotificationStatusUseCase
import com.echonotify.core.application.usecase.ReprocessDlqUseCase
import com.echonotify.core.application.usecase.SendNotificationUseCase
import com.echonotify.core.domain.model.NotificationType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.notificationRoutes(
    sendNotificationUseCase: SendNotificationUseCase,
    queryNotificationStatusUseCase: QueryNotificationStatusUseCase,
    reprocessDlqUseCase: ReprocessDlqUseCase
) {
    route("/v1/notifications") {
        post {
            val request = call.receive<CreateNotificationRequest>()
            val created = sendNotificationUseCase.execute(
                CreateNotificationCommand(
                    type = NotificationType.valueOf(request.type.uppercase()),
                    recipient = request.recipient,
                    clientId = request.clientId,
                    payload = request.payload,
                    idempotencyKey = request.idempotencyKey
                )
            )
            call.respond(
                HttpStatusCode.Accepted,
                CreateNotificationResponse(created.id.toString(), created.status.name)
            )
        }

        get("/{id}") {
            val id = UUID.fromString(requireNotNull(call.parameters["id"]))
            val data = queryNotificationStatusUseCase.execute(id)
            if (data == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "notification not found"))
                return@get
            }
            call.respond(
                NotificationStatusResponse(
                    id = data.id.toString(),
                    status = data.status.name,
                    retryCount = data.retryCount,
                    errorMessage = data.errorMessage,
                    errorCode = data.errorCode,
                    errorCategory = data.errorCategory,
                    retryable = data.retryable
                )
            )
        }

        post("/dlq/reprocess") {
            val ids = reprocessDlqUseCase.execute()
            call.respond(HttpStatusCode.OK, mapOf("reprocessedCount" to ids.size, "ids" to ids.map { it.toString() }))
        }
    }
}
