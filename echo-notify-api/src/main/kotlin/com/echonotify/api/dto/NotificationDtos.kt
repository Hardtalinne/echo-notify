package com.echonotify.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateNotificationRequest(
    val type: String,
    val recipient: String,
    val clientId: String,
    val payload: String,
    val idempotencyKey: String
)

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
    val errorMessage: String? = null
)
