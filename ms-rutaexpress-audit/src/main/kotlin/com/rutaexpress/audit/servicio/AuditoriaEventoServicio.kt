package com.rutaexpress.audit.servicio

import com.rutaexpress.audit.consumidor.EventoAuditoriaPayloadDto
import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.dominio.ProcessedMessage
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import com.rutaexpress.audit.repositorio.ProcessedMessageRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Lógica de dominio de auditoría desacoplada del transporte Kafka.
 * Valida payloads, aplica idempotencia Check-then-Act sobre PROCESSED_MESSAGES
 * y persiste la traza histórica en AUDITORIA_EVENTOS.
 */
@Service
class AuditoriaEventoServicio(
    private val auditoriaRepositorio: AuditoriaRepositorio,
    @Autowired(required = false)
    private val processedMessageRepositorio: ProcessedMessageRepositorio? = null,
    @Autowired(required = false)
    private val validator: Validator? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registrarEvento(
        dto: EventoAuditoriaPayloadDto,
        rawPayload: String,
        topic: String,
        partition: Int,
        offset: Long,
        key: String?,
        correlationId: String
    ): Boolean {
        if (validator != null) {
            val violations = validator.validate(dto)
            if (violations.isNotEmpty()) {
                throw ConstraintViolationException(violations)
            }
        }

        logger.info(
            "Evento {} recibido ID: {} (tópico={}, partición={}, offset={}, key={}, correlationId={})",
            dto.type, dto.eventId, topic, partition, offset, key ?: "(sin key)", correlationId
        )

        if (processedMessageRepositorio?.existsById(dto.eventId) == true || auditoriaRepositorio.existsByEventId(dto.eventId)) {
            logger.info("Evento duplicado detectado en processed_messages ID: {}, ignorando", dto.eventId)
            return false
        }

        if (processedMessageRepositorio != null) {
            try {
                processedMessageRepositorio.save(ProcessedMessage(messageId = dto.eventId, topic = topic))
            } catch (_: DataIntegrityViolationException) {
                logger.info("Unique Constraint Violation en processed_messages para ID: {}, ignorando duplicado", dto.eventId)
                return false
            }
        }

        val timestamp = try {
            Instant.parse(dto.timestamp)
        } catch (_: DateTimeParseException) {
            OffsetDateTime.parse(dto.timestamp).toInstant()
        }
        auditoriaRepositorio.save(
            EventoAuditoria(
                idEntidad = dto.shipmentId,
                tipoEntidad = dto.entityType,
                tipoEvento = dto.type,
                origen = dto.source,
                datosPayload = rawPayload,
                usuarioResponsable = dto.actor,
                fechaEvento = timestamp,
                eventId = dto.eventId
            )
        )
        return true
    }
}
