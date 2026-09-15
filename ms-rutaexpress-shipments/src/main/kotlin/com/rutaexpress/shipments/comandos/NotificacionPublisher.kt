package com.rutaexpress.shipments.comandos

import com.rutaexpress.shipments.dominio.Envio
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class NotificacionPublisher(
    @Autowired(required = false)
    private val rabbitTemplate: RabbitTemplate?,
    @Value("\${app.rabbit.enabled:true}")
    private val rabbitEnabled: Boolean = true,
    @Value("\${app.rabbit.exchange:cmd.direct}")
    private val exchange: String = "cmd.direct",
    @Value("\${app.notify.warehouse-email:bodega@rutaexpress.local}")
    private val warehouseEmail: String = "bodega@rutaexpress.local"
) {
    private val logger = LoggerFactory.getLogger(NotificacionPublisher::class.java)

    fun enviarEmailEstado(envio: Envio, subject: String, mensaje: String) {
        if (envio.recipientEmail.isNullOrBlank()) {
            logger.info("Envío {} sin correo de destinatario; no se envía email", envio.id)
            return
        }
        publicar(
            routingKey = "email.send",
            envelope = envolver(
                "notification.requested",
                EmailNotification(
                    shipmentId = envio.id,
                    recipientEmail = envio.recipientEmail,
                    recipientName = envio.recipientName,
                    subject = subject,
                    message = mensaje
                )
            )
        )
    }

    fun generarTicketBodega(envio: Envio) {
        publicar(
            routingKey = "warehouse.ticket",
            envelope = envolver(
                "warehouse.ticket.requested",
                WarehouseTicket(
                    shipmentId = envio.id,
                    warehouseEmail = warehouseEmail,
                    recipientName = envio.recipientName,
                    recipientAddress = envio.destinationAddress,
                    serviceName = envio.serviceCode ?: "SERVICIO",
                    packageDescription = "${envio.packagesCount} paquete(s) - ${envio.weightKg} kg - ${envio.volumeM3} m3"
                )
            )
        )
    }

    fun generarEtiqueta(envio: Envio) {
        publicar(
            routingKey = "label.gen",
            envelope = envolver(
                "label.requested",
                ShippingLabel(
                    shipmentId = envio.id,
                    warehouseEmail = warehouseEmail,
                    recipientName = envio.recipientName,
                    recipientAddress = envio.destinationAddress,
                    trackingCode = envio.trackingCode
                )
            )
        )
    }

    private fun <T> envolver(type: String, payload: T): ComandoEnvelope<T> = ComandoEnvelope(
        type = type,
        eventId = UUID.randomUUID().toString(),
        timestamp = Instant.now(),
        traceId = UUID.randomUUID().toString(),
        correlationId = UUID.randomUUID().toString(),
        payload = payload
    )

    private fun publicar(routingKey: String, envelope: ComandoEnvelope<*>) {
        if (!rabbitEnabled || rabbitTemplate == null) {
            logger.debug("RabbitMQ deshabilitado; se omite comando {} ({})", envelope.type, routingKey)
            return
        }

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, envelope)
            logger.info("Comando {} publicado en {} con routing key {}", envelope.type, exchange, routingKey)
        } catch (ex: Exception) {
            logger.warn("Error publicando comando en RabbitMQ ({}): {}", routingKey, ex.message)
        }
    }
}
