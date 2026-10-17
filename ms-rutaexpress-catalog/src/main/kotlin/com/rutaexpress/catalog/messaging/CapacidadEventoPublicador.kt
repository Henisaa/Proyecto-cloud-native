package com.rutaexpress.catalog.messaging

import com.rutaexpress.catalog.config.RabbitNombres
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Productor de catalog: publica el resultado de cada comando en el exchange de eventos (topic)
 * con la clave catalog.capacity.<resultado>. Espera la confirmación del broker; si no llega,
 * lanza MensajeriaNoDisponibleException para que el consumidor reintente o envíe el comando a la DLQ.
 */
@Component
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class CapacidadEventoPublicador(
    private val rabbitTemplate: RabbitTemplate,
    private val nombres: RabbitNombres,
) {
    private val log = LoggerFactory.getLogger(CapacidadEventoPublicador::class.java)

    fun publicar(resultado: ResultadoCapacidad) {
        val clave = when (resultado.estado) {
            EstadoCapacidad.RESERVADA -> nombres.routing.capacityReserved
            EstadoCapacidad.RECHAZADA -> nombres.routing.capacityRejected
            EstadoCapacidad.LIBERADA -> nombres.routing.capacityReleased
        }
        // eventId determinista: si el comando se reprocesa, el mismo evento se identifica igual.
        val eventId = "${resultado.commandId}:resultado"
        val sobre = mapOf(
            "type" to "catalog.capacity.${resultado.estado.name.lowercase()}",
            "eventId" to eventId,
            "timestamp" to Instant.now().toString(),
            "traceId" to resultado.commandId,
            "correlationId" to resultado.shipmentId,
            "payload" to mapOf(
                "shipmentId" to resultado.shipmentId,
                "serviceId" to resultado.serviceId,
                "result" to resultado.estado.name,
                "vehicleType" to resultado.vehicleType,
                "remainingCapacity" to resultado.capacidadRestante,
                "detail" to resultado.detalle,
            ),
        )
        val identificar = MessagePostProcessor { mensaje ->
            mensaje.messageProperties.messageId = eventId
            mensaje
        }
        val correlacion = CorrelationData(eventId)
        try {
            rabbitTemplate.convertAndSend(nombres.exchanges.events, clave, sobre, identificar, correlacion)
            val confirmacion = correlacion.future.get(10, TimeUnit.SECONDS)
            check(confirmacion.isAck && correlacion.returned == null) {
                "el broker no confirmó el evento (ack=${confirmacion.isAck}, sinRuta=${correlacion.returned != null})"
            }
            log.info("Evento {} publicado en {} con routing key {}", eventId, nombres.exchanges.events, clave)
        } catch (ex: Exception) {
            throw MensajeriaNoDisponibleException("No se pudo publicar el resultado del comando ${resultado.commandId}: ${ex.message}", ex)
        }
    }
}
