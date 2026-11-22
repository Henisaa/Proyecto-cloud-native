package com.rutaexpress.report.consumidor

import com.rutaexpress.report.dominio.EnvioEventoKafka
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import com.rutaexpress.report.servicio.ReporteSnapshotServicio
import jakarta.validation.Validator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

@Component
class EnvioEventoConsumidor @Autowired constructor(
    private val reporteSnapshotServicio: ReporteSnapshotServicio,
    private val objectMapper: ObjectMapper
) {
    constructor(
        repositorio: ReporteEnvioRepositorio,
        objectMapper: ObjectMapper,
        validator: Validator? = null
    ) : this(
        ReporteSnapshotServicio(repositorio, validator),
        objectMapper
    )

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["shipments.events"],
        groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-report-group}",
        concurrency = "\${app.kafka.listener.concurrency:3}"
    )
    fun consumirRegistro(registro: ConsumerRecord<String, String>) {
        val headerCorrelation = registro.headers().lastHeader("X-Correlation-Id")?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }
        procesarPayload(registro.value(), headerCorrelation, registro.partition(), registro.offset())
    }

    fun consumirEventoEnvio(mensaje: String) {
        procesarPayload(mensaje, null, null, null)
    }

    private fun procesarPayload(mensaje: String, headerCorrelation: String?, partition: Int?, offset: Long?) {
        val previousMdc = MDC.get("correlationId")
        try {
            val evento = objectMapper.readValue(mensaje, EnvioEventoKafka::class.java)
            val correlationId = headerCorrelation ?: evento.correlationId ?: evento.eventId ?: evento.shipmentId
            MDC.put("correlationId", correlationId)

            logger.info(
                "Evento recibido ID: {} (shipment={}, estado={}, particion={}, offset={})",
                evento.eventId ?: correlationId, evento.shipmentId, evento.status, partition ?: -1, offset ?: -1L
            )

            reporteSnapshotServicio.procesarSnapshot(evento, correlationId)
        } catch (e: Exception) {
            logger.error("Error al procesar evento de envio Kafka: {}", e.message, e)
            throw e
        } finally {
            if (previousMdc != null) MDC.put("correlationId", previousMdc) else MDC.remove("correlationId")
        }
    }
}
