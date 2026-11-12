package com.rutaexpress.shipments.eventos

import com.rutaexpress.shipments.dominio.Envio
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Publica los cambios de estado en Kafka (auditoría y reportes de la EP1).
 *  - Se envía después del commit: si la transacción se revierte no quedan eventos de cambios que nunca ocurrieron.
 *  - Se envía en un hilo aparte: un Kafka caído nunca retrasa la respuesta HTTP (P1-13).
 *  - Con app.kafka.enabled=false no se hace nada.
 */
@Component
class EnvioEventPublisher(
    @Autowired(required = false)
    private val kafkaTemplate: KafkaTemplate<String, Any>?,
    @Value("\${app.kafka.topics.shipments-events:shipments.events}")
    private val topicShipmentsEvents: String = "shipments.events",
    @Value("\${app.kafka.topics.envios-events:audit.timeline}")
    private val topicEnviosEvents: String = "audit.timeline",
    @Value("\${app.kafka.enabled:true}")
    private val kafkaEnabled: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(EnvioEventPublisher::class.java)

    private val ejecutor = ThreadPoolExecutor(
        1, 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue(1000),
        { tarea -> Thread(tarea, "kafka-eventos").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )

    fun publicarCambioEstado(envio: Envio, actor: String = "SISTEMA") {
        if (!kafkaEnabled || kafkaTemplate == null) {
            logger.debug("Kafka deshabilitado; se omite el evento {} del envío {}", envio.status, envio.id)
            return
        }
        val evento = EventoEnvioKafka(
            eventId = UUID.randomUUID().toString(),
            shipmentId = envio.id,
            serviceType = envio.serviceType ?: envio.serviceCode,
            status = envio.status.name,
            timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            trackingCode = envio.trackingCode,
            zone = envio.zone ?: "CENTRO",
            weightKg = envio.weightKg.toDouble(),
            packagesCount = envio.packagesCount,
            price = envio.price?.toDouble() ?: 0.0,
            actor = actor
        )

        despuesDelCommit {
            enviar(topicShipmentsEvents, envio.id, evento)
            enviar(topicEnviosEvents, evento.eventId, evento)
        }
    }

    private fun despuesDelCommit(accion: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = ejecutor.execute(accion)
            })
        } else {
            ejecutor.execute(accion)
        }
    }

    private fun enviar(topic: String, key: String, evento: EventoEnvioKafka) {
        val prevCorrelation = MDC.get("correlationId")
        MDC.put("correlationId", evento.correlationId)
        try {
            val record = ProducerRecord<String, Any>(topic, key, evento)
            record.headers().add("X-Correlation-Id", evento.correlationId.toByteArray(StandardCharsets.UTF_8))
            record.headers().add("X-Trace-Id", evento.traceId.toByteArray(StandardCharsets.UTF_8))
            record.headers().add("X-Event-Id", evento.eventId.toByteArray(StandardCharsets.UTF_8))
            kafkaTemplate?.send(record)?.whenComplete { _, ex ->
                MDC.put("correlationId", evento.correlationId)
                try {
                    if (ex != null) {
                        logger.warn("No se pudo publicar en {} [correlationId={}]: {}", topic, evento.correlationId, ex.message)
                    } else {
                        logger.info("Evento de envío {} publicado en {} (key={}, correlationId={})", evento.status, topic, key, evento.correlationId)
                    }
                } finally {
                    MDC.remove("correlationId")
                }
            }
        } catch (ex: Exception) {
            logger.warn("Error publicando evento en {} [correlationId={}]: {}", topic, evento.correlationId, ex.message)
        } finally {
            if (prevCorrelation != null) MDC.put("correlationId", prevCorrelation) else MDC.remove("correlationId")
        }
    }
}
