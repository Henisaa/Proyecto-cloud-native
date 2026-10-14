package com.rutaexpress.catalog.model.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "fleet_capacities",
    indexes = [
        Index(name = "idx_capacity_service", columnList = "service_id"),
        Index(name = "idx_capacity_zone", columnList = "zone"),
        Index(name = "idx_capacity_vehicle", columnList = "vehicle_type")
    ]
)
class FleetCapacity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    var service: CatalogService? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var zone: ZoneType,

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    var vehicleType: VehicleType,

    @Column(name = "max_daily_packages", nullable = false)
    var maxDailyPackages: Int,

    @Column(name = "current_booked_packages", nullable = false)
    var currentBookedPackages: Int = 0,

    @Column(name = "max_weight_kg", nullable = false)
    var maxWeightKg: Double,

    @Column(name = "max_volume_m3", nullable = false)
    var maxVolumeM3: Double,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    val availablePackages: Int
        get() = (maxDailyPackages - currentBookedPackages).coerceAtLeast(0)

    val isFull: Boolean
        get() = currentBookedPackages >= maxDailyPackages

    val utilizationPercentage: Double
        get() = if (maxDailyPackages > 0) {
            (currentBookedPackages.toDouble() / maxDailyPackages.toDouble()) * 100.0
        } else 0.0
}
