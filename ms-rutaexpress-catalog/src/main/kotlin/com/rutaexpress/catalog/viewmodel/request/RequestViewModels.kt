package com.rutaexpress.catalog.viewmodel.request

import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import jakarta.validation.constraints.*

data class CreateServiceViewModel(
    @field:NotBlank(message = "Service code is mandatory")
    @field:Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    val code: String,

    @field:NotBlank(message = "Service name is mandatory")
    @field:Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    val name: String,

    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String? = null,

    val category: ServiceCategory = ServiceCategory.STANDARD,

    @field:Min(value = 1, message = "Estimated delivery hours must be at least 1")
    val estimatedDeliveryHours: Int = 24,

    @field:PositiveOrZero(message = "Base price must be positive or zero")
    val basePrice: Double = 0.0,

    @field:PositiveOrZero(message = "Price per km must be positive or zero")
    val pricePerKm: Double = 0.0,

    @field:PositiveOrZero(message = "Price per kg must be positive or zero")
    val pricePerKg: Double = 0.0
)

data class UpdateServiceViewModel(
    @field:Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    val name: String? = null,

    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String? = null,

    val category: ServiceCategory? = null,

    @field:Min(value = 1, message = "Estimated delivery hours must be at least 1")
    val estimatedDeliveryHours: Int? = null,

    @field:PositiveOrZero(message = "Base price must be positive or zero")
    val basePrice: Double? = null,

    @field:PositiveOrZero(message = "Price per km must be positive or zero")
    val pricePerKm: Double? = null,

    @field:PositiveOrZero(message = "Price per kg must be positive or zero")
    val pricePerKg: Double? = null,

    val status: ServiceStatus? = null,

    val active: Boolean? = null
)

data class UpdateStatusViewModel(
    val status: ServiceStatus,
    val active: Boolean? = null
)

data class CreateCapacityViewModel(
    val zone: ZoneType,
    val vehicleType: VehicleType,

    @field:Min(value = 1, message = "Max daily packages must be at least 1")
    val maxDailyPackages: Int,

    @field:Positive(message = "Max weight in kg must be greater than 0")
    val maxWeightKg: Double,

    @field:Positive(message = "Max volume in m3 must be greater than 0")
    val maxVolumeM3: Double
)

data class UpdateCapacityViewModel(
    @field:Min(value = 1, message = "Max daily packages must be at least 1")
    val maxDailyPackages: Int? = null,

    @field:Min(value = 0, message = "Current booked packages cannot be negative")
    val currentBookedPackages: Int? = null,

    @field:Positive(message = "Max weight in kg must be greater than 0")
    val maxWeightKg: Double? = null,

    @field:Positive(message = "Max volume in m3 must be greater than 0")
    val maxVolumeM3: Double? = null,

    val active: Boolean? = null
)

data class CreateTariffViewModel(
    val originZone: ZoneType,
    val destinationZone: ZoneType,

    @field:PositiveOrZero(message = "Base fare must be positive or zero")
    val baseFare: Double,

    @field:PositiveOrZero(message = "Per km rate must be positive or zero")
    val perKmRate: Double,

    @field:PositiveOrZero(message = "Per kg rate must be positive or zero")
    val perKgRate: Double,

    @field:PositiveOrZero(message = "Min fare must be positive or zero")
    val minFare: Double
)

data class UpdateTariffViewModel(
    @field:PositiveOrZero(message = "Base fare must be positive or zero")
    val baseFare: Double? = null,

    @field:PositiveOrZero(message = "Per km rate must be positive or zero")
    val perKmRate: Double? = null,

    @field:PositiveOrZero(message = "Per kg rate must be positive or zero")
    val perKgRate: Double? = null,

    @field:PositiveOrZero(message = "Min fare must be positive or zero")
    val minFare: Double? = null,

    val active: Boolean? = null
)

data class CapacityCheckRequestViewModel(
    val zone: ZoneType,

    @field:Positive(message = "Weight must be positive")
    val weightKg: Double,

    @field:Positive(message = "Volume must be positive")
    val volumeM3: Double,

    val vehicleType: VehicleType? = null,

    @field:Min(value = 1, message = "Packages count must be at least 1")
    val packagesCount: Int = 1
)

data class CapacityReservationViewModel(
    val zone: ZoneType,
    val vehicleType: VehicleType,

    @field:Min(value = 1, message = "Packages count must be at least 1")
    val packagesCount: Int = 1,

    @field:Positive(message = "Weight must be positive")
    val weightKg: Double = 1.0,

    @field:Positive(message = "Volume must be positive")
    val volumeM3: Double = 0.01
)

data class CapacityReleaseViewModel(
    val zone: ZoneType,
    val vehicleType: VehicleType,

    @field:Min(value = 1, message = "Packages count must be at least 1")
    val packagesCount: Int = 1
)
