package com.rutaexpress.report.servicio

import com.rutaexpress.report.dominio.EnvioEventoKafka
import com.rutaexpress.report.dominio.SnapshotEnvio
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Lógica de dominio para construir y actualizar los snapshots de KPIs de envíos
 * manteniendo idempotencia por eventId y temporal ante eventos repetidos o desordenados.
 */
@Service
class ReporteSnapshotServicio(
    private val repositorio: ReporteEnvioRepositorio,
    @Autowired(required = false)
    private val validator: Validator? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @CacheEvict(value = ["kpis", "top-services"], allEntries = true)
    fun procesarSnapshot(evento: EnvioEventoKafka, correlationId: String): Boolean {
        if (validator != null) {
            val violations = validator.validate(evento)
            if (violations.isNotEmpty()) {
                throw ConstraintViolationException(violations)
            }
        }

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

        if (!evento.eventId.isNullOrBlank() && evento.eventId == snapshot.lastEventId) {
            logger.info(
                "Evento duplicado por eventId para ID: {}, ignorando (idempotencia, correlationId={})",
                evento.eventId, correlationId
            )
            return false
        }

        if (ahora.isBefore(snapshot.updatedAt)) {
            logger.info(
                "Evento duplicado o desordenado para ID: {}, ignorando (idempotencia, correlationId={})",
                evento.eventId ?: evento.shipmentId, correlationId
            )
            return false
        }

        snapshot.currentStatus = evento.status
        if (evento.serviceType != null) {
            snapshot.serviceType = evento.serviceType
        }
        snapshot.updatedAt = ahora
        if (!evento.eventId.isNullOrBlank()) {
            snapshot.lastEventId = evento.eventId
        }

        if (evento.status.equals("ENTREGADO", ignoreCase = true) && snapshot.deliveredAt == null) {
            snapshot.deliveredAt = ahora
            val minutos = Duration.between(snapshot.createdAt, ahora).toMinutes().coerceAtLeast(0)
            snapshot.leadTimeMinutes = minutos.toBigDecimal()
        }

        repositorio.save(snapshot)
        logger.info("Snapshot actualizado para envio {}: estado {}", evento.shipmentId, evento.status)
        return true
    }
}
