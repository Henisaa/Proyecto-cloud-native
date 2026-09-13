package com.rutaexpress.report.consumidor

import com.fasterxml.jackson.databind.ObjectMapper
import com.rutaexpress.report.dominio.EnvioEventoKafka
import com.rutaexpress.report.dominio.SnapshotEnvio
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime

@Component
class EnvioEventoConsumidor(
    private val repositorio: ReporteEnvioRepositorio,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["shipments.events"], groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-report-group}")
    @Transactional
    fun consumirEventoEnvio(mensaje: String) {
        try {
            val evento = objectMapper.readValue(mensaje, EnvioEventoKafka::class.java)
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

            snapshot.currentStatus = evento.status
            if (evento.serviceType != null) {
                snapshot.serviceType = evento.serviceType
            }
            snapshot.updatedAt = ahora

            if (evento.status.equals("ENTREGADO", ignoreCase = true) && snapshot.deliveredAt == null) {
                snapshot.deliveredAt = ahora
                val minutos = Duration.between(snapshot.createdAt, ahora).toMinutes().toDouble()
                snapshot.leadTimeMinutes = if (minutos >= 0) minutos else 0.0
            }

            repositorio.save(snapshot)
            logger.info("Snapshot actualizado para envio {}: estado {}", evento.shipmentId, evento.status)
        } catch (e: Exception) {
            logger.error("Error al procesar evento de envio Kafka: {}", e.message, e)
        }
    }
}
