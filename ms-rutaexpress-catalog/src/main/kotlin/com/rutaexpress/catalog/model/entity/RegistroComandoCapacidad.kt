package com.rutaexpress.catalog.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Registro de cada comando de capacidad procesado. Su clave (commandId) hace idempotente el consumo:
 * un comando duplicado devuelve el resultado guardado y no vuelve a modificar la capacidad.
 */
@Entity
@Table(name = "tb_capacity_commands")
class RegistroComandoCapacidad(
    @Id
    @Column(name = "command_id", length = 64, nullable = false)
    var commandId: String = "",

    @Column(name = "command_type", length = 20, nullable = false)
    var tipo: String = "",

    @Column(name = "shipment_id", length = 36, nullable = false)
    var shipmentId: String = "",

    @Column(name = "result", length = 20, nullable = false)
    var resultado: String = "",

    @Column(name = "vehicle_type", length = 30)
    var vehicleType: String? = null,

    @Column(name = "remaining_capacity", nullable = false)
    var capacidadRestante: Int = 0,

    @Column(name = "detail", length = 250)
    var detalle: String = "",

    @Column(name = "processed_at", nullable = false)
    var procesadoEn: Instant = Instant.now(),
)
