package com.echonotify.core.infrastructure.messaging

import com.echonotify.core.application.port.NotificationPublisherPort
import com.echonotify.core.domain.model.Notification
import io.opentelemetry.api.trace.Span
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

        val record = ProducerRecord(topic, notification.id.toString(), json.encodeToString(message))
        val spanContext = Span.current().spanContext
        if (spanContext.isValid) {
            val traceparent = "00-${spanContext.traceId}-${spanContext.spanId}-01"
            record.headers().add("traceparent", traceparent.toByteArray())
        }

        producer.send(record).get()
        log.info("Published notification {} to topic {}", notification.id, topic)
    }
}
