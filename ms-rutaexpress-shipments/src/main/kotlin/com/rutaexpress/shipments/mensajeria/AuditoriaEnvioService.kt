package com.rutaexpress.shipments.mensajeria

import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaEnvio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaRepositorio
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.format.DateTimeParseException

/** Qué debe hacer el consumidor después de aplicar un resultado de capacidad (fuera de la transacción). */
enum class AccionPosterior { NINGUNA, COMPENSAR, NOTIFICAR_RECHAZO }

data class ResultadoAplicado(val accion: AccionPosterior, val envio: Envio? = null, val eventId: String = "")

/**
 * Aplica en la base de shipments lo que llega por la cola de auditoría:
 *  - shipment.status.* -> traza de auditoría local (idempotente por eventId).
 *  - catalog.capacity.* -> actualiza el estado de la reserva del envío.
 *
 * Los resultados de capacidad son idempotentes y tolerantes al desorden:
 *  1. Se comprueba primero el eventId: un evento ya registrado termina sin cambios.
 *  2. Solo se aceptan transiciones válidas (PENDIENTE -> RESERVADA/RECHAZADA; RESERVADA o LIBERACION_PENDIENTE
 *     -> LIBERADA). El resto se registra en la auditoría como ignorado.
 *  3. Un resultado de reserva de un comando anterior al último (lastReserveCommandId) se descarta.
 */
@Service
class AuditoriaEnvioService(
    private val envios: EnvioRepositorio,
    private val registros: RegistroAuditoriaRepositorio,
) {
    private val log = LoggerFactory.getLogger(AuditoriaEnvioService::class.java)

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

    @Transactional
    fun aplicarResultadoCapacidad(sobre: JsonNode, clave: String): ResultadoAplicado {
        val eventId = eventId(sobre)
        if (registros.existsById(eventId)) {
            log.info("Resultado de capacidad {} ya procesado: se ignora", eventId)
            return ResultadoAplicado(AccionPosterior.NINGUNA)
        }
        val payload = sobre.path("payload")
        val envioId = payload.path("shipmentId").asText("")
        val resultado = payload.path("result").asText("")
        val envio = envios.findById(envioId).orElseThrow {
            MensajeNoRecuperableException("Resultado de capacidad ($clave) para un envío inexistente: '$envioId'")
        }
        val comandoOrigen = IdsComando.commandIdDeResultado(eventId)

        var accion = AccionPosterior.NINGUNA
        var detalle = "capacidad=$resultado"
        when (resultado) {
            "RESERVADA", "RECHAZADA" -> {
                val vigente = envio.lastReserveCommandId == null || envio.lastReserveCommandId == comandoOrigen
                if (envio.capacityStatus != EstadoCapacidad.PENDIENTE || !vigente) {
                    detalle += " ignorado (capacidad actual ${envio.capacityStatus}${if (vigente) "" else ", comando anterior"})"
                } else if (resultado == "RESERVADA") {
                    envio.capacityReserved = true
                    payload.get("vehicleType")?.takeIf { !it.isNull }?.asText()?.let { envio.vehicleType = it }
                    if (envio.status == EstadoEnvio.CANCELADO) {
                        // La reserva llegó después de cancelar: el cupo se devuelve. El estado se guarda en esta misma
                        // transacción, así la compensación no se pierde aunque falle la publicación posterior.
                        envio.capacityStatus = EstadoCapacidad.LIBERACION_PENDIENTE
                        accion = AccionPosterior.COMPENSAR
                    } else {
                        envio.capacityStatus = EstadoCapacidad.RESERVADA
                    }
                    envio.updatedAt = Instant.now()
                } else {
                    envio.capacityStatus = EstadoCapacidad.RECHAZADA
                    envio.capacityReserved = false
                    envio.updatedAt = Instant.now()
                    if (envio.status != EstadoEnvio.CANCELADO) accion = AccionPosterior.NOTIFICAR_RECHAZO
                }
            }

            "LIBERADA" -> {
                if (envio.capacityStatus == EstadoCapacidad.RESERVADA || envio.capacityStatus == EstadoCapacidad.LIBERACION_PENDIENTE) {
                    envio.capacityStatus = EstadoCapacidad.LIBERADA
                    envio.capacityReserved = false
                    envio.updatedAt = Instant.now()
                } else {
                    detalle += " ignorado (capacidad actual ${envio.capacityStatus})"
                }
            }

            else -> throw MensajeNoRecuperableException("Resultado de capacidad desconocido: '$resultado'")
        }
        envios.save(envio)
        registros.save(
            RegistroAuditoriaEnvio(
                eventId = eventId,
                shipmentId = envioId,
                tipoEvento = sobre.path("type").asText(clave),
                routingKey = clave,
                detalle = detalle.take(250),
                ocurridoEn = instante(sobre.path("timestamp").asText("")),
                recibidoEn = Instant.now(),
            )
        )
        return ResultadoAplicado(accion, envio.takeIf { accion != AccionPosterior.NINGUNA }, eventId)
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
