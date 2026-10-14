package com.rutaexpress.shipments.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tb_shipments")
class Envio(
    @Id
    @Column(name = "id", length = 36, nullable = false)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "tracking_code", length = 30, nullable = false, unique = true)
    var trackingCode: String = "",

    @Column(name = "service_id", nullable = false)
    var serviceId: Long = 0,

    @Column(name = "service_code", length = 30)
    var serviceCode: String? = null,

    @Column(name = "service_type", length = 30)
    var serviceType: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: EstadoEnvio = EstadoEnvio.CREADO,

    @Column(name = "zone", length = 30)
    var zone: String? = null,

    @Column(name = "vehicle_type", length = 30)
    var vehicleType: String? = null,

    @Column(name = "recipient_name", length = 120, nullable = false)
    var recipientName: String = "",

    @Column(name = "recipient_email", length = 120)
    var recipientEmail: String? = null,

    @Column(name = "recipient_phone", length = 30)
    var recipientPhone: String? = null,

    @Column(name = "origin_address", length = 250, nullable = false)
    var originAddress: String = "",

    @Column(name = "destination_address", length = 250, nullable = false)
    var destinationAddress: String = "",

    @Column(name = "weight_kg", precision = 10, scale = 2, nullable = false)
    var weightKg: BigDecimal = BigDecimal.ZERO,

    @Column(name = "volume_m3", precision = 10, scale = 3, nullable = false)
    var volumeM3: BigDecimal = BigDecimal.ZERO,

    @Column(name = "packages_count", nullable = false)
    var packagesCount: Int = 1,

    @Column(name = "price", precision = 12, scale = 2)
    var price: BigDecimal? = null,

    @Column(name = "customer_email", length = 120)
    var customerEmail: String? = null,

    @Column(name = "capacity_reserved", nullable = false)
    var capacityReserved: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
