package com.rutaexpress.shipments.mensajeria

import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaRepositorio
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Aplica en la base de shipments lo que llega por la cola de auditoría:
 *  - shipment.status.* -> traza de auditoría local (idempotente por eventId).
 *  - catalog.capacity.* -> actualiza el estado de la reserva del envío.
 * Las operaciones son idempotentes: entregar dos veces el mismo evento deja el mismo estado.
 */
@Service
class AuditoriaEnvioService(
    private val envios: EnvioRepositorio,
    private val registros: RegistroAuditoriaRepositorio,
) {

    @Transactional
    fun registrarEstado(sobre: JsonNode, clave: String) {
        val eventId = eventId(sobre)
        if (registros.existsById(eventId)) return
        val payload = sobre.path("payload")
        val envioId = payload.path("shipmentId").asText("")
        if (envioId.isBlank()) throw MensajeNoRecuperableException("El evento $eventId no indica el envío")
        registros.save(
            RegistroAuditoriaEnvio(
                eventId = eventId,
                shipmentId = envioId,
                tipoEvento = sobre.path("type").asText(clave),
                routingKey = clave,
                detalle = "estado=${payload.path("status").asText("")} actor=${payload.path("actor").asText("")}".take(250),
                ocurridoEn = instante(sobre.path("timestamp").asText("")),
                recibidoEn = Instant.now(),
            )
        )
    }

    /**
     * Devuelve el envío cuando su cupo quedó reservado pero ya fue cancelado: en ese caso el llamador
     * debe pedir la liberación (compensación). Devuelve null en cualquier otro caso.
     */
    @Transactional
    fun aplicarResultadoCapacidad(sobre: JsonNode, clave: String): Envio? {
        val eventId = eventId(sobre)
        val payload = sobre.path("payload")
        val envioId = payload.path("shipmentId").asText("")
        val resultado = payload.path("result").asText("")
        val envio = envios.findById(envioId).orElseThrow {
            MensajeNoRecuperableException("Resultado de capacidad ($clave) para un envío inexistente: '$envioId'")
        }
        when (resultado) {
            "RESERVADA" -> {
                envio.capacityStatus = EstadoCapacidad.RESERVADA
                envio.capacityReserved = true
                payload.get("vehicleType")?.takeIf { !it.isNull }?.asText()?.let { envio.vehicleType = it }
            }
            "RECHAZADA" -> {
                envio.capacityStatus = EstadoCapacidad.RECHAZADA
                envio.capacityReserved = false
            }
            "LIBERADA" -> {
                envio.capacityStatus = EstadoCapacidad.LIBERADA
                envio.capacityReserved = false
            }
            else -> throw MensajeNoRecuperableException("Resultado de capacidad desconocido: '$resultado'")
        }
        envios.save(envio)
        if (!registros.existsById(eventId)) {
            registros.save(
                RegistroAuditoriaEnvio(
                    eventId = eventId,
                    shipmentId = envioId,
                    tipoEvento = sobre.path("type").asText(clave),
                    routingKey = clave,
                    detalle = "capacidad=$resultado".take(250),
                    ocurridoEn = instante(sobre.path("timestamp").asText("")),
                    recibidoEn = Instant.now(),
                )
            )
        }
        return if (resultado == "RESERVADA" && envio.status == EstadoEnvio.CANCELADO) envio else null
    }

    private fun eventId(sobre: JsonNode): String {
        val id = sobre.path("eventId").asText("")
        if (id.isBlank()) throw MensajeNoRecuperableException("El evento no trae eventId")
        return id
    }

    private fun instante(texto: String): Instant = try {
        Instant.parse(texto)
    } catch (ex: DateTimeParseException) {
        Instant.now()
    }
}
