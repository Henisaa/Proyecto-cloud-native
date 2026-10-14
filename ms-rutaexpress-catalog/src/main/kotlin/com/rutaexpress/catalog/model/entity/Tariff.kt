package com.rutaexpress.catalog.model.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.rutaexpress.catalog.model.enums.ZoneType
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "tariffs",
    indexes = [
        Index(name = "idx_tariff_service", columnList = "service_id"),
        Index(name = "idx_tariff_zones", columnList = "origin_zone, destination_zone")
    ]
)
class Tariff(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    var service: CatalogService? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_zone", nullable = false, length = 30)
    var originZone: ZoneType,

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_zone", nullable = false, length = 30)
    var destinationZone: ZoneType,

    @Column(name = "base_fare", nullable = false)
    var baseFare: Double,

    @Column(name = "per_km_rate", nullable = false)
    var perKmRate: Double,

    @Column(name = "per_kg_rate", nullable = false)
    var perKgRate: Double,

    @Column(name = "min_fare", nullable = false)
    var minFare: Double,

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

    fun calculateEstimate(distanceKm: Double, weightKg: Double): Double {
        val total = baseFare + (distanceKm * perKmRate) + (weightKg * perKgRate)
        return total.coerceAtLeast(minFare)
    }
}
