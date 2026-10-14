package com.rutaexpress.audit.consumidor
import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class AuditoriaKafkaConsumidor(private val auditoriaRepositorio: AuditoriaRepositorio, private val mapper: ObjectMapper) {
    @KafkaListener(topics = ["shipments.events", "audit.timeline"], groupId = "\${spring.kafka.consumer.group-id:ms-rutaexpress-audit-group}")
    @Transactional
    fun consumirEvento(registro: ConsumerRecord<String, String>) {
        val data = mapper.readTree(registro.value())
        val eventId = data.path("eventId").asText("")
        require(eventId.isNotBlank()) { "Evento sin eventId" }
        if (auditoriaRepositorio.existsByEventId(eventId)) return
        val entity = data.path("shipmentId").asText(registro.key() ?: "")
        require(entity.isNotBlank()) { "Evento sin shipmentId" }
        val timestamp = Instant.parse(data.path("timestamp").asText())
        auditoriaRepositorio.save(EventoAuditoria(idEntidad=entity, tipoEntidad="ENVIO",
            tipoEvento=data.path("type").asText("shipment.status.changed"),
            origen=data.path("source").asText("ms-rutaexpress-shipments"),
            datosPayload=registro.value(), usuarioResponsable=data.path("actor").asText("SISTEMA"),
            fechaEvento=timestamp, eventId=eventId))
    }
}
