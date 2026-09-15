package com.rutaexpress.shipments.eventos

/**
 * Evento plano (sin envelope) publicado en `shipments.events` y `envios-events`.
 * El servicio de reportes espera: eventId, shipmentId, serviceType, status, timestamp.
 */
data class EventoEnvioKafka(
    val eventId: String,
    val shipmentId: String,
    val serviceType: String?,
    val status: String,
    val timestamp: String,
    val trackingCode: String?,
)
