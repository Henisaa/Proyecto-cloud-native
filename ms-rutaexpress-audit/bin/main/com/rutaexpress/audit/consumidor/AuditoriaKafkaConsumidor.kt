package com.rutaexpress.audit.consumidor

import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AuditoriaKafkaConsumidor(
    private val auditoriaRepositorio: AuditoriaRepositorio
) {
    private val log = LoggerFactory.getLogger(AuditoriaKafkaConsumidor::class.java)

    @KafkaListener(topics = ["envios-events", "auditoria-topic"], groupId = "\${spring.kafka.consumer.group-id:audit-group}")
    fun consumirEvento(registro: ConsumerRecord<String, String>) {
        log.info("Evento recibido en topic [{}], clave [{}]: {}", registro.topic(), registro.key(), registro.value())

        val evento = EventoAuditoria(
            idEntidad = registro.key() ?: "SIN_CLAVE",
            tipoEntidad = registro.topic(),
            tipoEvento = "EVENTO_KAFKA",
            origen = registro.topic(),
            datosPayload = registro.value(),
            usuarioResponsable = "SISTEMA_KAFKA",
            fechaEvento = Instant.now()
        )

        auditoriaRepositorio.save(evento)
    }
}
