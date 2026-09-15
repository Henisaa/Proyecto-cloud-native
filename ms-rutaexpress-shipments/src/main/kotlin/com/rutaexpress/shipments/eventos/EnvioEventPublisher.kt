package com.rutaexpress.shipments.eventos

import com.rutaexpress.shipments.dominio.Envio
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class EnvioEventPublisher(
    @Autowired(required = false)
    private val kafkaTemplate: KafkaTemplate<String, Any>?,
    @Value("\${app.kafka.topics.shipments-events:shipments.events}")
    private val topicShipmentsEvents: String = "shipments.events",
    @Value("\${app.kafka.topics.envios-events:envios-events}")
    private val topicEnviosEvents: String = "envios-events",
    @Value("\${app.kafka.enabled:true}")
    private val kafkaEnabled: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(EnvioEventPublisher::class.java)

    fun publicarCambioEstado(envio: Envio) {
        val evento = EventoEnvioKafka(
            eventId = UUID.randomUUID().toString(),
            shipmentId = envio.id,
            serviceType = envio.serviceType ?: envio.serviceCode,
            status = envio.status.name,
            timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            trackingCode = envio.trackingCode
        )

        enviar(topicShipmentsEvents, envio.id, evento)
        enviar(topicEnviosEvents, envio.id, evento)
    }

    private fun enviar(topic: String, key: String, evento: EventoEnvioKafka) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            logger.debug("Kafka deshabilitado; se omite evento {} del envío {}", evento.status, key)
            return
        }

        try {
            kafkaTemplate.send(topic, key, evento).whenComplete { _, ex ->
                if (ex != null) {
                    logger.warn("No se pudo publicar en {}: {}", topic, ex.message)
                } else {
                    logger.info("Evento de envío {} publicado en {} (key={})", evento.status, topic, key)
                }
            }
        } catch (ex: Exception) {
            logger.warn("Error publicando evento en {}: {}", topic, ex.message)
        }
    }
}
