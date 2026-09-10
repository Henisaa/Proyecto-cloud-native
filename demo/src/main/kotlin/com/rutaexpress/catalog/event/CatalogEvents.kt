package com.rutaexpress.catalog.event

import java.time.Instant
import java.util.UUID

data class EventEnvelope<T>(
    val eventId: String = UUID.randomUUID().toString(),
    val type: String,
    val timestamp: Instant = Instant.now(),
    val traceId: String = UUID.randomUUID().toString(),
    val correlationId: String? = null,
    val source: String = "ms-rutaexpress-catalog",
    val payload: T
)

data class ServiceCreatedPayload(
    val serviceId: Long,
    val code: String,
    val name: String,
    val category: String,
    val basePrice: Double,
    val estimatedHours: Int
)

data class ServiceUpdatedPayload(
    val serviceId: Long,
    val code: String,
    val status: String,
    val active: Boolean
)

data class CapacityUpdatedPayload(
    val serviceId: Long,
    val capacityId: Long,
    val zone: String,
    val vehicleType: String,
    val maxDailyPackages: Int,
    val currentBookedPackages: Int,
    val availablePackages: Int
)

data class TariffUpdatedPayload(
    val serviceId: Long,
    val tariffId: Long,
    val originZone: String,
    val destinationZone: String,
    val baseFare: Double,
    val perKmRate: Double,
    val perKgRate: Double,
    val minFare: Double
)
