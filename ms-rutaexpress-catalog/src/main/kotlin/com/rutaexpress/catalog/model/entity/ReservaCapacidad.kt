package com.rutaexpress.catalog.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant

enum class EstadoReserva { ACTIVA, LIBERADA }

/**
 * Reserva de capacidad de un envío. shipment_id es la clave primaria: un envío no puede tener dos reservas
 * ni liberar dos veces, y la liberación siempre actúa sobre la línea (capacityId) que se reservó.
 */
@Entity
@Table(name = "tb_capacity_reservations")
class ReservaCapacidad(
    @Id
    @Column(name = "shipment_id", length = 36, nullable = false)
    var shipmentId: String = "",

    @Column(name = "capacity_id", nullable = false)
    var capacityId: Long = 0,

    @Column(name = "packages", nullable = false)
    var paquetes: Int = 0,

    @Column(name = "vehicle_type", length = 30)
    var vehicleType: String? = null,

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    var estado: EstadoReserva = EstadoReserva.ACTIVA,

    @Column(name = "reserve_command_id", length = 64, nullable = false)
    var reservaCommandId: String = "",

    @Column(name = "release_command_id", length = 64)
    var liberacionCommandId: String? = null,

    @Column(name = "created_at", nullable = false)
    var creadaEn: Instant = Instant.now(),

    @Column(name = "released_at")
    var liberadaEn: Instant? = null,
) : Persistable<String> {
    // La clave UUID viene del envío, no la genera JPA. Un registro nuevo debe usar INSERT;
    // merge podría actualizar la reserva que ganó otra transacción y descontar dos veces.
    @Transient
    private var nueva: Boolean = true

    override fun getId(): String = shipmentId
    override fun isNew(): Boolean = nueva

    @PostLoad
    @PostPersist
    fun marcarPersistida() { nueva = false }
}
