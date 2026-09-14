package com.rutaexpress.report.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "TB_REPORT_SHIPMENTS")
class SnapshotEnvio(
    @Id
    @Column(name = "shipment_id", nullable = false, length = 100)
    var shipmentId: String,

    @Column(name = "service_type", nullable = false, length = 50)
    var serviceType: String,

    @Column(name = "current_status", nullable = false, length = 50)
    var currentStatus: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime,

    @Column(name = "delivered_at")
    var deliveredAt: OffsetDateTime? = null,

    @Column(name = "lead_time_minutes")
    var leadTimeMinutes: Double? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
