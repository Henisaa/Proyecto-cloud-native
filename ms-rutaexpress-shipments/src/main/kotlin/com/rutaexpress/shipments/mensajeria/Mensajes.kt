package com.rutaexpress.shipments.mensajeria

import java.math.BigDecimal

/** Payload del comando capacity.reserve / capacity.release. Los valores numéricos viajan como Double, igual que en catalog. */
data class ComandoCapacidadPayload(
    val shipmentId: String,
    val serviceId: Long,
    val zone: String,
    val vehicleType: String?,
    val packagesCount: Int,
    val weightKg: Double,
    val volumeM3: Double,
)

/** Payload del evento shipment.status.<ESTADO>, consumido por la auditoría. */
data class EventoEstadoPayload(
    val shipmentId: String,
    val trackingCode: String,
    val status: String,
    val serviceId: Long,
    val serviceCode: String?,
    val vehicleType: String?,
    val zone: String?,
    val packagesCount: Int,
    val weightKg: BigDecimal,
    val actor: String,
)
