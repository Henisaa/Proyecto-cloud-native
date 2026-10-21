package com.rutaexpress.shipments.configuracion

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonSerializer

@Configuration
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaProducerConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonJsonSerializer::class.java
        configProps[ProducerConfig.ACKS_CONFIG] = "all"
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configProps[ProducerConfig.RETRIES_CONFIG] = 3
        // delivery.timeout.ms >= linger.ms + request.timeout.ms, o el productor no arranca.
        configProps[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 3000
        configProps[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 5000
        configProps[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 10000
        configProps[JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS] = false
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())

    /**
     * Tópicos que publica shipments. El broker no los crea al publicar y nada más los provisiona: KafkaAdmin
     * los crea al arrancar si faltan, así los eventos llegan a audit tanto en CI como en el EC2.
     */
    @Bean
    fun shipmentsEventsTopic(): NewTopic = TopicBuilder.name("shipments.events").partitions(3).replicas(3).build()

    @Bean
    fun auditTimelineTopic(): NewTopic = TopicBuilder.name("audit.timeline").partitions(3).replicas(3).build()
}
