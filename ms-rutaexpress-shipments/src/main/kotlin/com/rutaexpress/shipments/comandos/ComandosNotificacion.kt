package com.rutaexpress.shipments.comandos

import java.time.Instant

/**
 * Envelope común exigido por ms-rutaexpress-notify (validación estricta).
 */
data class ComandoEnvelope<T>(
    val type: String,
    val eventId: String,
    val timestamp: Instant,
    val traceId: String,
    val correlationId: String,
    val payload: T,
)

data class EmailNotification(
    val shipmentId: String,
    val recipientEmail: String?,
    val recipientName: String,
    val subject: String,
    val message: String,
    val deviceToken: String? = null,
)

data class WarehouseTicket(
    val shipmentId: String,
    val warehouseEmail: String,
    val recipientName: String,
    val recipientAddress: String,
    val serviceName: String,
    val packageDescription: String,
)

data class ShippingLabel(
    val shipmentId: String,
    val warehouseEmail: String,
    val recipientName: String,
    val recipientAddress: String,
    val trackingCode: String,
)
