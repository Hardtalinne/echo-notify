package com.echonotify.core.infrastructure.messaging

import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.domain.model.Notification
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class KafkaNotificationPublisher(
    private val producer: KafkaProducer<String, String>,
    private val json: Json = Json
) : NotificationPublisherPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(topic: String, notification: Notification) {
        val message = NotificationMessage(
            eventType = "NOTIFICATION",
            notification = notification.toRecord(),
            timestamp = System.currentTimeMillis()
        )
        producer.send(ProducerRecord(topic, notification.id.toString(), json.encodeToString(message))).get()
        log.info("Published notification {} to topic {}", notification.id, topic)
    }
}
