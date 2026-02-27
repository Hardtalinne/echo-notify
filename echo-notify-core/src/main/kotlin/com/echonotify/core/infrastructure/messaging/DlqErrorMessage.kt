package com.echonotify.core.infrastructure.messaging

import kotlinx.serialization.Serializable

@Serializable
data class DlqErrorMessage(
    val reason: String,
    val sourceTopic: String,
    val sourcePartition: Int,
    val sourceOffset: Long,
    val rawPayload: String,
    val timestamp: Long = System.currentTimeMillis()
)
