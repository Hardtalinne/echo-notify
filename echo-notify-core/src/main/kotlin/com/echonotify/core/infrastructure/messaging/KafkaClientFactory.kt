package com.echonotify.core.infrastructure.messaging

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

object KafkaClientFactory {
    fun producerProps(bootstrapServers: String): Map<String, Any> = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.COMPRESSION_TYPE_CONFIG to "lz4",
        ProducerConfig.LINGER_MS_CONFIG to 10,
        ProducerConfig.BATCH_SIZE_CONFIG to 32_768,
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 120_000,
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 30_000
    )

    fun consumerProps(bootstrapServers: String, groupId: String): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "200")
        put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1")
        put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500")
    }
}
