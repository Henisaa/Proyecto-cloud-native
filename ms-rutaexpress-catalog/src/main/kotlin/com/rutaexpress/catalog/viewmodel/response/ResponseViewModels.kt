package com.rutaexpress.catalog.viewmodel.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CapacityDetailViewModel(
    val id: Long,
    val zone: ZoneType,
    val vehicleType: VehicleType,
    val maxDailyPackages: Int,
    val currentBookedPackages: Int,
    val availablePackages: Int,
    val utilizationPercentage: Double,
    val isFull: Boolean,
    val maxWeightKg: Double,
    val maxVolumeM3: Double,
    val active: Boolean,
    val updatedAt: LocalDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TariffDetailViewModel(
    val id: Long,
    val originZone: ZoneType,
    val destinationZone: ZoneType,
    val baseFare: Double,
    val perKmRate: Double,
    val perKgRate: Double,
    val minFare: Double,
    val active: Boolean,
    val updatedAt: LocalDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServiceDetailViewModel(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val category: ServiceCategory,
    val status: ServiceStatus,
    val estimatedDeliveryHours: Int,
    val basePrice: Double,
    val pricePerKm: Double,
    val pricePerKg: Double,
    val active: Boolean,
    val isAvailable: Boolean,
    val totalDailyCapacity: Int,
    val totalBookedPackages: Int,
    val overallUtilizationPercentage: Double,
    val capacities: List<CapacityDetailViewModel> = emptyList(),
    val tariffs: List<TariffDetailViewModel> = emptyList(),
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServiceSummaryViewModel(
    val id: Long,
    val code: String,
    val name: String,
    val category: ServiceCategory,
    val status: ServiceStatus,
    val estimatedDeliveryHours: Int,
    val basePrice: Double,
    val active: Boolean,
    val availableCapacitiesCount: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CapacityCheckResultViewModel(
    val serviceId: Long,
    val serviceCode: String,
    val zone: ZoneType,
    val available: Boolean,
    val vehicleTypeSelected: VehicleType?,
    val remainingSlots: Int,
    val maxWeightAllowedKg: Double,
    val maxVolumeAllowedM3: Double,
    val message: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CapacityReservationResultViewModel(
    val serviceId: Long,
    val capacityId: Long,
    val zone: ZoneType,
    val vehicleType: VehicleType,
    val packagesReserved: Int,
    val remainingCapacity: Int,
    val success: Boolean,
    val message: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponseViewModel<T>(
    val success: Boolean = true,
    val message: String,
    val data: T? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val traceId: String = UUID.randomUUID().toString()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponseViewModel(
    val success: Boolean = false,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val validationErrors: Map<String, String>? = null
)
