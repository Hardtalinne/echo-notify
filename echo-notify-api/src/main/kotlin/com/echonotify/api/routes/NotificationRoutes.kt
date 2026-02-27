package com.echonotify.api.routes

import com.echonotify.api.dto.CreateNotificationRequest
import com.echonotify.api.dto.CreateNotificationResponse
import com.echonotify.api.dto.EmailPayloadContract
import com.echonotify.api.dto.NotificationStatusResponse
import com.echonotify.api.dto.NotificationPayloadContract
import com.echonotify.api.dto.WebhookPayloadContract
import com.echonotify.api.security.ApiScopes
import com.echonotify.api.security.ApiSecurity
import com.echonotify.api.security.AuthorizationResult
import com.echonotify.api.security.apiKeyHeader
import com.echonotify.api.security.clientIdHeader
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Route.notificationRoutes(
    sendNotificationUseCase: SendNotificationUseCase,
    queryNotificationStatusUseCase: QueryNotificationStatusUseCase,
    reprocessDlqUseCase: ReprocessDlqUseCase,
    apiSecurity: ApiSecurity
) {
    route("/v1/notifications") {
        post {
            val request = call.receive<CreateNotificationRequest>()
            when (
                apiSecurity.authorize(
                    apiKey = call.apiKeyHeader(),
                    requiredScope = ApiScopes.CREATE,
                    expectedClientId = request.clientId
                )
            ) {
                AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing or invalid api key"))
                    return@post
                }

                AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "insufficient scope or client mismatch"))
                    return@post
                }

                is AuthorizationResult.Authorized -> Unit
            }

            val type = NotificationType.valueOf(request.type.uppercase())
            val payloadContract: NotificationPayloadContract = when (type) {
                NotificationType.EMAIL -> Json.decodeFromString<EmailPayloadContract>(request.payload.toString())
                NotificationType.WEBHOOK -> Json.decodeFromString<WebhookPayloadContract>(request.payload.toString())
            }

            val created = sendNotificationUseCase.execute(
                CreateNotificationCommand(
                    type = type,
                    recipient = request.recipient,
                    clientId = request.clientId,
                    payload = Json.encodeToString(payloadContract),
                    idempotencyKey = request.idempotencyKey
                )
            )
            call.respond(
                HttpStatusCode.Accepted,
                CreateNotificationResponse(created.id.toString(), created.status.name)
            )
        }

        get("/{id}") {
            val callerClientId = call.clientIdHeader()
            if (callerClientId.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing X-Client-Id header"))
                return@get
            }
            when (
                apiSecurity.authorize(
                    apiKey = call.apiKeyHeader(),
                    requiredScope = ApiScopes.READ,
                    expectedClientId = callerClientId
                )
            ) {
                AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing or invalid api key"))
                    return@get
                }

                AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "insufficient scope or client mismatch"))
                    return@get
                }

                is AuthorizationResult.Authorized -> Unit
            }

            val id = UUID.fromString(requireNotNull(call.parameters["id"]))
            val data = queryNotificationStatusUseCase.execute(id)
            if (data == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "notification not found"))
                return@get
            }
            if (data.clientId != callerClientId) {
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
            when (
                apiSecurity.authorize(
                    apiKey = call.apiKeyHeader(),
                    requiredScope = ApiScopes.DLQ_REPROCESS,
                    expectedClientId = null
                )
            ) {
                AuthorizationResult.Unauthorized -> {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing or invalid api key"))
                    return@post
                }

                AuthorizationResult.Forbidden -> {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "insufficient scope"))
                    return@post
                }

                is AuthorizationResult.Authorized -> Unit
            }

            val ids = reprocessDlqUseCase.execute()
            call.respond(HttpStatusCode.OK, mapOf("reprocessedCount" to ids.size, "ids" to ids.map { it.toString() }))
        }
    }
}
