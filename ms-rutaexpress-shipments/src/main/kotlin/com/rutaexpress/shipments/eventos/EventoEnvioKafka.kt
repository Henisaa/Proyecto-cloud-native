package com.rutaexpress.shipments.eventos

/**
 * Evento publicado en `shipments.events` y `audit.timeline`, con identidad y trazabilidad.
 * El servicio de reportes espera: eventId, shipmentId, serviceType, status, timestamp.
 */
data class EventoEnvioKafka(
    val eventId: String,
    val shipmentId: String,
    val serviceType: String?,
    val status: String,
    val timestamp: String,
    val trackingCode: String?,
    val type: String = "shipment.status.changed",
    val actor: String = "SISTEMA",
    val source: String = "ms-rutaexpress-shipments",
    val traceId: String = eventId,
    val correlationId: String = shipmentId,
)
