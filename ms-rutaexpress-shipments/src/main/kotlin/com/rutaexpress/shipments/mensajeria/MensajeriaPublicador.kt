package com.rutaexpress.shipments.mensajeria

import com.rutaexpress.shipments.comandos.ComandoEnvelope
import com.rutaexpress.shipments.comandos.EmailNotification
import com.rutaexpress.shipments.comandos.ShippingLabel
import com.rutaexpress.shipments.comandos.WarehouseTicket
import com.rutaexpress.shipments.configuracion.RabbitNombres
import com.rutaexpress.shipments.dominio.Envio
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Productor de shipments. Cada publicación espera la confirmación del broker (publisher confirms).
 * Se llama dentro de la transacción de la operación: si el broker no confirma, MensajeriaNoDisponibleException
 * revierte el cambio de estado. Así un envío nunca queda cambiado sin que su comando o evento haya llegado.
 *
 *  - Comandos (direct, ex.<caso>.cmd): capacity.reserve / capacity.release hacia catalog.
 *  - Eventos (topic, ex.<caso>.events): shipment.status.<ESTADO> hacia la auditoría.
 *  - Solicitudes de notificación (topic): notification.* hacia notify.
 */
@Component
class MensajeriaPublicador(
    @Autowired(required = false)
    private val rabbitTemplate: RabbitTemplate?,
    @Value("\${app.rabbit.enabled:true}")
    private val habilitado: Boolean,
    @Value("\${app.notify.warehouse-email:bodega@rutaexpress.local}")
    private val warehouseEmail: String,
    private val nombres: RabbitNombres,
    @Value("\${app.rabbit.confirm-timeout-ms:3000}")
    private val esperaConfirmacionMs: Long = 3000,
) {
    private val logger = LoggerFactory.getLogger(MensajeriaPublicador::class.java)

    fun publicarEstado(envio: Envio, actor: String) {
        val clave = nombres.routing.shipmentStatusPrefix + envio.status.name
        publicar(nombres.exchanges.events, clave, envolver("shipment.status.changed", envio.id, EventoEstadoPayload(
            shipmentId = envio.id,
            trackingCode = envio.trackingCode,
            status = envio.status.name,
            serviceId = envio.serviceId,
            serviceCode = envio.serviceCode,
            vehicleType = envio.vehicleType,
            zone = envio.zone,
            packagesCount = envio.packagesCount,
            weightKg = envio.weightKg,
            actor = actor,
        )))
    }

    /** commandId: el que quedó guardado en el envío (lastReserveCommandId), para descartar resultados viejos. */
    fun solicitarReserva(envio: Envio, commandId: String): String {
        publicarComando(nombres.routing.capacityReserve, envolver("capacity.reserve.requested", commandId, comandoCapacidad(envio)))
        return commandId
    }

    /** commandId determinista (IdsComando): reenviar la liberación nunca descuenta dos veces. */
    fun solicitarLiberacion(envio: Envio, commandId: String): String {
        publicarComando(nombres.routing.capacityRelease, envolver("capacity.release.requested", commandId, comandoCapacidad(envio)))
        return commandId
    }

    fun enviarEmailEstado(envio: Envio, subject: String, mensaje: String, eventId: String = UUID.randomUUID().toString()) {
        if (envio.recipientEmail.isNullOrBlank()) {
            logger.info("Envío {} sin correo de destinatario; no se solicita email", envio.id)
            return
        }
        publicar(nombres.exchanges.events, nombres.routing.notificationEmail, envolver("notification.requested", eventId,
            EmailNotification(
                shipmentId = envio.id,
                recipientEmail = envio.recipientEmail,
                recipientName = envio.recipientName,
                subject = subject,
                message = mensaje,
            )))
    }

    fun generarTicketBodega(envio: Envio) {
        publicar(nombres.exchanges.events, nombres.routing.notificationTicket, envolver("warehouse.ticket.requested", UUID.randomUUID().toString(),
            WarehouseTicket(
                shipmentId = envio.id,
                warehouseEmail = warehouseEmail,
                recipientName = envio.recipientName,
                recipientAddress = envio.destinationAddress,
                serviceName = envio.serviceCode ?: "SERVICIO",
                packageDescription = "${envio.packagesCount} paquete(s) - ${envio.weightKg} kg - ${envio.volumeM3} m3",
            )))
    }

    fun generarEtiqueta(envio: Envio) {
        publicar(nombres.exchanges.events, nombres.routing.notificationLabel, envolver("label.requested", UUID.randomUUID().toString(),
            ShippingLabel(
                shipmentId = envio.id,
                warehouseEmail = warehouseEmail,
                recipientName = envio.recipientName,
                recipientAddress = envio.destinationAddress,
                trackingCode = envio.trackingCode,
            )))
    }

    private fun comandoCapacidad(envio: Envio) = ComandoCapacidadPayload(
        shipmentId = envio.id,
        serviceId = envio.serviceId,
        zone = envio.zone ?: "URBAN_CENTER",
        vehicleType = envio.vehicleType,
        packagesCount = envio.packagesCount,
        weightKg = envio.weightKg.toDouble(),
        volumeM3 = envio.volumeM3.toDouble(),
    )

    private fun publicarComando(clave: String, sobre: ComandoEnvelope<*>) {
        publicar(nombres.exchanges.command, clave, sobre)
    }

    private fun <T> envolver(tipo: String, eventId: String, payload: T): ComandoEnvelope<T> = ComandoEnvelope(
        type = tipo,
        eventId = eventId,
        timestamp = Instant.now(),
        traceId = UUID.randomUUID().toString(),
        correlationId = eventId,
        payload = payload,
    )

    private fun publicar(exchange: String, clave: String, sobre: ComandoEnvelope<*>) {
        if (!habilitado || rabbitTemplate == null) {
            logger.debug("RabbitMQ deshabilitado; se omite {} ({})", sobre.type, clave)
            return
        }
        val identificar = MessagePostProcessor { mensaje ->
            mensaje.messageProperties.messageId = sobre.eventId
            mensaje
        }
        val correlacion = CorrelationData(sobre.eventId)
        try {
            rabbitTemplate.convertAndSend(exchange, clave, sobre, identificar, correlacion)
            val confirmacion = correlacion.future.get(esperaConfirmacionMs, TimeUnit.MILLISECONDS)
            check(confirmacion.isAck && correlacion.returned == null) {
                "el broker no confirmó el mensaje (ack=${confirmacion.isAck}, sinRuta=${correlacion.returned != null})"
            }
            logger.info("Mensaje {} publicado en {} con routing key {}", sobre.type, exchange, clave)
        } catch (ex: Exception) {
            logger.warn("Error publicando {} en {}: {}", clave, exchange, ex.message)
            throw MensajeriaNoDisponibleException("No se pudo confirmar ${sobre.type} en RabbitMQ; la operación se revierte", ex)
        }
    }
}
