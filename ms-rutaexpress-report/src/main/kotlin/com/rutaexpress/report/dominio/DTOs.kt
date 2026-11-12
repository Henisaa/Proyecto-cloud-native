package com.rutaexpress.report.dominio

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class EnvioEventoKafka(
    @field:Size(max = 100)
    val eventId: String? = null,
    @field:NotBlank(message = "shipmentId es obligatorio")
    val shipmentId: String,
    val serviceType: String? = null,
    @field:NotBlank(message = "status es obligatorio")
    val status: String,
    val timestamp: OffsetDateTime? = null,
    val correlationId: String? = null
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
