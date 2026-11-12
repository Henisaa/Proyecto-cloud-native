package com.rutaexpress.report.consumidor

import com.rutaexpress.report.dominio.EnvioEventoKafka
import com.rutaexpress.report.dominio.SnapshotEnvio
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.OffsetDateTime

@Component
class EnvioEventoConsumidor(
    private val repositorio: ReporteEnvioRepositorio,
    private val objectMapper: ObjectMapper,
    @Autowired(required = false)
    private val validator: Validator? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["shipments.events"], groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-report-group}")
    @Transactional
    fun consumirEventoEnvio(mensaje: String) {
        val previousMdc = MDC.get("correlationId")
        try {
            val evento = objectMapper.readValue(mensaje, EnvioEventoKafka::class.java)
            val correlationId = evento.correlationId ?: evento.eventId ?: evento.shipmentId
            MDC.put("correlationId", correlationId)

            if (validator != null) {
                val violations = validator.validate(evento)
                if (violations.isNotEmpty()) {
                    throw ConstraintViolationException(violations)
                }
            }

            logger.info("Evento recibido ID: {} (shipment={}, estado={})",
                evento.eventId ?: correlationId, evento.shipmentId, evento.status)

            val ahora = evento.timestamp ?: OffsetDateTime.now()

            val snapshot = repositorio.findById(evento.shipmentId).orElseGet {
                SnapshotEnvio(
                    shipmentId = evento.shipmentId,
                    serviceType = evento.serviceType ?: "ESTANDAR",
                    currentStatus = evento.status,
                    createdAt = ahora,
                    updatedAt = ahora
                )
            }

            if (ahora.isBefore(snapshot.updatedAt)) {
                logger.info("Evento duplicado o desordenado para ID: {}, ignorando (idempotencia)", evento.eventId ?: evento.shipmentId)
                return
            }
            snapshot.currentStatus = evento.status
            if (evento.serviceType != null) {
                snapshot.serviceType = evento.serviceType
            }
            snapshot.updatedAt = ahora

            if (evento.status.equals("ENTREGADO", ignoreCase = true) && snapshot.deliveredAt == null) {
                snapshot.deliveredAt = ahora
                val minutos = Duration.between(snapshot.createdAt, ahora).toMinutes().coerceAtLeast(0)
                snapshot.leadTimeMinutes = minutos.toBigDecimal()
            }

            repositorio.save(snapshot)
            logger.info("Snapshot actualizado para envio {}: estado {}", evento.shipmentId, evento.status)
        } catch (e: Exception) {
            logger.error("Error al procesar evento de envio Kafka: {}", e.message, e)
            throw e
        } finally {
            if (previousMdc != null) MDC.put("correlationId", previousMdc) else MDC.remove("correlationId")
        }
    }
}
