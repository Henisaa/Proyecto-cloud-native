package com.rutaexpress.audit.consumidor

import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.dominio.ProcessedMessage
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import com.rutaexpress.audit.repositorio.ProcessedMessageRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Instant

data class EventoAuditoriaPayloadDto(
    @field:NotBlank(message = "eventId es obligatorio")
    val eventId: String,
    @field:NotBlank(message = "shipmentId es obligatorio")
    val shipmentId: String,
    @field:NotBlank(message = "timestamp es obligatorio")
    val timestamp: String,
    val type: String = "shipment.status.changed",
    val source: String = "ms-rutaexpress-shipments",
    val actor: String = "SISTEMA",
    val correlationId: String? = null
)

@Component
class AuditoriaKafkaConsumidor(
    private val auditoriaRepositorio: AuditoriaRepositorio,
    private val mapper: ObjectMapper,
    @Autowired(required = false)
    private val processedMessageRepositorio: ProcessedMessageRepositorio? = null,
    @Autowired(required = false)
    private val validator: Validator? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["shipments.events", "audit.timeline"], groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-audit-group}")
    @Transactional
    fun consumirEvento(registro: ConsumerRecord<String, String>) {
        val headerCorrelation = registro.headers().lastHeader("X-Correlation-Id")?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }

        val data = mapper.readTree(registro.value())
        val eventId = data.path("eventId").asText("")
        require(eventId.isNotBlank()) { "Evento sin eventId" }

        val entity = data.path("shipmentId").asText(registro.key() ?: "")
        require(entity.isNotBlank()) { "Evento sin shipmentId" }

        val timestampRaw = data.path("timestamp").asText("")
        val dto = EventoAuditoriaPayloadDto(
            eventId = eventId,
            shipmentId = entity,
            timestamp = timestampRaw,
            type = data.path("type").asText("shipment.status.changed"),
            source = data.path("source").asText("ms-rutaexpress-shipments"),
            actor = data.path("actor").asText("SISTEMA"),
            correlationId = headerCorrelation ?: data.path("correlationId").asText(entity)
        )

        val correlationId = dto.correlationId ?: entity
        val previousMdc = MDC.get("correlationId")
        MDC.put("correlationId", correlationId)
        try {
            if (validator != null) {
                val violations = validator.validate(dto)
                if (violations.isNotEmpty()) {
                    throw ConstraintViolationException(violations)
                }
            }

            logger.info("Evento {} recibido ID: {} (tópico={}, partición={}, offset={})",
                dto.type, eventId, registro.topic(), registro.partition(), registro.offset())

            System.out.printf(
                "[AUDITORIA KAFKA] 📍Partición: %d | Offset: %d | Key: %s | Tópico: %s | Evento: %s | CorrelationId: %s%n",
                registro.partition(), registro.offset(), registro.key() ?: "(sin key)", registro.topic(), eventId, correlationId
            )

            // Idempotencia del Consumidor (Check-then-Act): tabla processed_messages + verificación de negocio
            if (processedMessageRepositorio?.existsById(eventId) == true || auditoriaRepositorio.existsByEventId(eventId)) {
                logger.info("Evento duplicado detectado en processed_messages ID: {}, ignorando", eventId)
                return
            }

            if (processedMessageRepositorio != null) {
                try {
                    processedMessageRepositorio.save(ProcessedMessage(messageId = eventId, topic = registro.topic()))
                } catch (_: DataIntegrityViolationException) {
                    logger.info("Unique Constraint Violation en processed_messages para ID: {}, ignorando duplicado", eventId)
                    return
                }
            }

            val timestamp = Instant.parse(dto.timestamp)
            auditoriaRepositorio.save(
                EventoAuditoria(
                    idEntidad = dto.shipmentId,
                    tipoEntidad = "ENVIO",
                    tipoEvento = dto.type,
                    origen = dto.source,
                    datosPayload = registro.value(),
                    usuarioResponsable = dto.actor,
                    fechaEvento = timestamp,
                    eventId = dto.eventId
                )
            )
        } finally {
            if (previousMdc != null) MDC.put("correlationId", previousMdc) else MDC.remove("correlationId")
        }
    }
}
