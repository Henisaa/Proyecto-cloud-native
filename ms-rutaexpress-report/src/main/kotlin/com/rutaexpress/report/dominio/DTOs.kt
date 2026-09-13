package com.rutaexpress.report.dominio

import java.time.OffsetDateTime

data class EnvioEventoKafka(
    val eventId: String? = null,
    val shipmentId: String,
    val serviceType: String? = null,
    val status: String,
    val timestamp: OffsetDateTime? = null
)

data class KpisRespuesta(
    val range: String,
    val totalShipments: Long,
    val shipmentsPerHour: Double,
    val avgLeadTimeMinutes: Double,
    val activeShipmentsByStatus: Map<String, Long>
)

data class TopServicioRespuesta(
    val serviceType: String,
    val totalShipments: Long,
    val percentage: Double
)

data class ServicioConteo(
    val serviceType: String,
    val count: Long
)

data class EstadoConteo(
    val status: String,
    val count: Long
)
