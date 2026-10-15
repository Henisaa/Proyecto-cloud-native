package com.rutaexpress.shipments.mensajeria

import com.rabbitmq.client.Channel
import com.rutaexpress.shipments.configuracion.RabbitNombres
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Consumidor de la cola de auditoría (q.rutaexpress.audit). Recibe los eventos de estado de los envíos y los
 * resultados de capacidad que publica catalog. Reglas de confiabilidad:
 *  - ACK solo cuando el evento quedó aplicado. JSON inválido o datos imposibles -> NACK sin requeue -> DLQ.
 *  - Fallos técnicos (base de datos, broker) -> reintentos acotados; si se agotan -> NACK sin requeue -> DLQ.
 */
@Component
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class AuditoriaListener(
    private val auditoria: AuditoriaEnvioService,
    private val mensajeria: MensajeriaPublicador,
    private val nombres: RabbitNombres,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(AuditoriaListener::class.java)

    @RabbitListener(queues = ["\${app.rabbit.queues.audit.name}"])
    fun onEvento(message: Message, channel: Channel) {
        val propiedades = message.messageProperties
        val etiqueta = propiedades.deliveryTag
        val clave = propiedades.receivedRoutingKey.orEmpty()
        try {
            val sobre = leer(message.body)
            when {
                clave.startsWith(nombres.routing.shipmentStatusPrefix) ->
                    reintentar { auditoria.registrarEstado(sobre, clave) }
                clave.startsWith("catalog.capacity.") -> {
                    val compensar = reintentar { auditoria.aplicarResultadoCapacidad(sobre, clave) }
                    if (compensar != null) {
                        // La reserva llegó después de cancelar el envío: se devuelve el cupo.
                        // El commandId deriva del eventId del resultado, así un reenvío no libera dos veces.
                        val commandId = "compensacion:" + sobre.path("eventId").asText("")
                        reintentar { mensajeria.solicitarLiberacion(compensar, commandId) }
                    }
                }
                else -> throw MensajeNoRecuperableException("Routing key sin manejador en la auditoría: '$clave'")
            }
            channel.basicAck(etiqueta, false)
        } catch (ex: MensajeNoRecuperableException) {
            rechazar(channel, etiqueta, clave, propiedades.messageId, "no recuperable", ex)
        } catch (ex: RuntimeException) {
            rechazar(channel, etiqueta, clave, propiedades.messageId, "reintentos agotados", ex)
        }
    }

    private fun leer(cuerpo: ByteArray): JsonNode = try {
        jsonMapper.readTree(cuerpo)
    } catch (ex: JacksonException) {
        throw MensajeNoRecuperableException("El evento no es JSON válido: ${ex.message}", ex)
    }

    private fun <T> reintentar(accion: () -> T): T {
        val politica = nombres.consumer
        var ultimo: RuntimeException? = null
        for (intento in 0 until politica.maxAttempts) {
            try {
                return accion()
            } catch (ex: MensajeNoRecuperableException) {
                throw ex
            } catch (ex: IllegalArgumentException) {
                throw MensajeNoRecuperableException("Datos de evento no válidos: ${ex.message}", ex)
            } catch (ex: RuntimeException) {
                ultimo = ex
                log.warn("Intento {} de {} fallido: {}", intento + 1, politica.maxAttempts, ex.message)
                if (intento < politica.maxAttempts - 1) {
                    Thread.sleep(politica.initialIntervalMs * (1L shl intento))
                }
            }
        }
        throw ultimo ?: IllegalStateException("Sin intentos configurados")
    }

    private fun rechazar(
        channel: Channel,
        etiqueta: Long,
        clave: String,
        mensajeId: String?,
        motivo: String,
        causa: RuntimeException,
    ) {
        channel.basicNack(etiqueta, false, false)
        log.error("Evento de auditoría enviado a la DLQ ({}): routingKey={} messageId={} causa={}",
            motivo, clave, mensajeId ?: "-", causa.message, causa)
    }
}
