package com.rutaexpress.catalog.model.entity

import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "catalog_services",
    indexes = [
        Index(name = "idx_services_code", columnList = "code", unique = true),
        Index(name = "idx_services_status", columnList = "status"),
        Index(name = "idx_services_category", columnList = "category")
    ]
)
class CatalogService(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    var code: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: ServiceCategory,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ServiceStatus = ServiceStatus.ACTIVE,

    @Column(name = "estimated_delivery_hours", nullable = false)
    var estimatedDeliveryHours: Int = 24,

    @Column(name = "base_price", nullable = false)
    var basePrice: Double = 0.0,

    @Column(name = "price_per_km", nullable = false)
    var pricePerKm: Double = 0.0,

    @Column(name = "price_per_kg", nullable = false)
    var pricePerKg: Double = 0.0,

    @Column(nullable = false)
    var active: Boolean = true,

    @OneToMany(mappedBy = "service", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var capacities: MutableSet<FleetCapacity> = mutableSetOf(),

    @OneToMany(mappedBy = "service", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var tariffs: MutableSet<Tariff> = mutableSetOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    fun addCapacity(capacity: FleetCapacity) {
        capacities.add(capacity)
        capacity.service = this
    }

    fun addTariff(tariff: Tariff) {
        tariffs.add(tariff)
        tariff.service = this
    }
}
