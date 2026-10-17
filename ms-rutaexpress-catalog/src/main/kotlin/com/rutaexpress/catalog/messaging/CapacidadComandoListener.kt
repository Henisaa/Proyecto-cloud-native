package com.rutaexpress.catalog.messaging

import com.rabbitmq.client.Channel
import com.rutaexpress.catalog.config.RabbitNombres
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Consumidor de la cola de comandos de capacidad (q.rutaexpress.command).
 * Flujo por mensaje:
 *   1. Interpretar y validar el sobre. Si es inválido -> NACK sin requeue (DLQ) de inmediato.
 *   2. Aplicar el comando en la base de datos (idempotente) con reintentos acotados si falla por causa técnica.
 *   3. Publicar el resultado en el exchange de eventos, también con reintentos acotados.
 *   4. ACK solo cuando el resultado quedó registrado y publicado. Si algo se agota -> NACK sin requeue (DLQ).
 */
@Component
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class CapacidadComandoListener(
    private val procesador: CapacidadComandoProcesador,
    private val publicador: CapacidadEventoPublicador,
    private val nombres: RabbitNombres,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(CapacidadComandoListener::class.java)

    @RabbitListener(queues = ["\${app.rabbit.queues.command.name}"])
    fun onComando(message: Message, channel: Channel) {
        val propiedades = message.messageProperties
        val etiqueta = propiedades.deliveryTag
        val clave = propiedades.receivedRoutingKey.orEmpty()
        try {
            val comando = interpretar(clave, message.body)
            val resultado = reintentar { procesador.procesar(comando) }
            reintentar { publicador.publicar(resultado) }
            channel.basicAck(etiqueta, false)
        } catch (ex: MensajeNoRecuperableException) {
            rechazar(channel, etiqueta, clave, propiedades.messageId, "no recuperable", ex)
        } catch (ex: RuntimeException) {
            rechazar(channel, etiqueta, clave, propiedades.messageId, "reintentos agotados", ex)
        }
    }

    private fun interpretar(clave: String, cuerpo: ByteArray): ComandoCapacidad {
        val tipo = when (clave) {
            nombres.routing.capacityReserve -> TipoComando.RESERVAR
            nombres.routing.capacityRelease -> TipoComando.LIBERAR
            else -> throw MensajeNoRecuperableException("Routing key de comando desconocida: '$clave'")
        }
        val sobre = try {
            jsonMapper.readTree(cuerpo)
        } catch (ex: JacksonException) {
            throw MensajeNoRecuperableException("El comando no es JSON válido: ${ex.message}", ex)
        }
        val commandId = sobre.path("eventId").asText("")
        if (commandId.isBlank()) throw MensajeNoRecuperableException("El comando no trae eventId")
        val payload = sobre.get("payload") ?: throw MensajeNoRecuperableException("El comando no trae payload")
        val datos = try {
            jsonMapper.readValue(payload.toString(), PayloadComandoCapacidad::class.java)
        } catch (ex: JacksonException) {
            throw MensajeNoRecuperableException("El payload del comando $commandId no es válido: ${ex.message}", ex)
        }
        if (datos.shipmentId.isBlank() || datos.zone.isBlank() || datos.serviceId <= 0 ||
            datos.packagesCount < 1 || datos.weightKg <= 0.0 || datos.volumeM3 <= 0.0
        ) {
            throw MensajeNoRecuperableException("Comando $commandId con datos fuera de rango para el envío '${datos.shipmentId}'")
        }
        return ComandoCapacidad(
            commandId = commandId,
            tipo = tipo,
            shipmentId = datos.shipmentId,
            serviceId = datos.serviceId,
            zona = datos.zone,
            vehicleType = datos.vehicleType,
            packagesCount = datos.packagesCount,
            weightKg = datos.weightKg,
            volumeM3 = datos.volumeM3,
        )
    }

    /** Solo se reintentan fallos recuperables; los datos inválidos se rechazan al primer intento. */
    private fun <T> reintentar(accion: () -> T): T {
        val politica = nombres.consumer
        var ultimo: RuntimeException? = null
        for (intento in 0 until politica.maxAttempts) {
            try {
                return accion()
            } catch (ex: MensajeNoRecuperableException) {
                throw ex
            } catch (ex: IllegalArgumentException) {
                throw MensajeNoRecuperableException("Datos no válidos para catalog: ${ex.message}", ex)
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
        log.error("Comando de capacidad enviado a la DLQ ({}): routingKey={} messageId={} causa={}",
            motivo, clave, mensajeId ?: "-", causa.message, causa)
    }
}
