package com.echonotify.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateNotificationRequest(
    val type: String,
    val recipient: String,
    val clientId: String,
    val payload: JsonObject,
    val idempotencyKey: String
)

sealed interface NotificationPayloadContract

@Serializable
@SerialName("EMAIL")
data class EmailPayloadContract(
    val subject: String,
    val body: String,
    val from: String
) : NotificationPayloadContract

@Serializable
@SerialName("WEBHOOK")
data class WebhookPayloadContract(
    val url: String,
    val body: String
) : NotificationPayloadContract

@Serializable
data class CreateNotificationResponse(
    val id: String,
    val status: String
)

@Serializable
data class NotificationStatusResponse(
    val id: String,
    val status: String,
    val retryCount: Int,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val errorCategory: String? = null,
    val retryable: Boolean? = null
)
