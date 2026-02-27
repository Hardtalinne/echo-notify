package com.echonotify.api.error

import io.opentelemetry.api.trace.Span
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val problemJsonContentType = ContentType.parse("application/problem+json")

@Serializable
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val code: String,
    val traceId: String? = null
)

suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    title: String,
    detail: String,
    code: String,
    type: String = "about:blank"
) {
    val currentSpan = Span.current().spanContext
    val traceId = when {
        currentSpan.isValid -> currentSpan.traceId
        else -> null
    }

    respondText(
        text = Json.encodeToString(
            ProblemDetails(
                type = type,
                title = title,
                status = status.value,
                detail = detail,
                instance = request.path(),
                code = code,
                traceId = traceId
            )
        ),
        contentType = problemJsonContentType,
        status = status
    )
}
