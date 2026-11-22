package com.rutaexpress.audit.consumidor

import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import com.rutaexpress.audit.repositorio.ProcessedMessageRepositorio
import com.rutaexpress.audit.servicio.AuditoriaEventoServicio
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

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
class AuditoriaKafkaConsumidor @Autowired constructor(
    private val auditoriaEventoServicio: AuditoriaEventoServicio,
    private val mapper: ObjectMapper
) {
    constructor(
        auditoriaRepositorio: AuditoriaRepositorio,
        mapper: ObjectMapper,
        processedMessageRepositorio: ProcessedMessageRepositorio? = null,
        validator: Validator? = null
    ) : this(
        AuditoriaEventoServicio(auditoriaRepositorio, processedMessageRepositorio, validator),
        mapper
    )

    @KafkaListener(
        topics = ["shipments.events", "audit.timeline"],
        groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-audit-group}",
        concurrency = "\${app.kafka.listener.concurrency:3}"
    )
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
            auditoriaEventoServicio.registrarEvento(
                dto = dto,
                rawPayload = registro.value(),
                topic = registro.topic(),
                partition = registro.partition(),
                offset = registro.offset(),
                key = registro.key(),
                correlationId = correlationId
            )
        } finally {
            if (previousMdc != null) MDC.put("correlationId", previousMdc) else MDC.remove("correlationId")
        }
    }
}
