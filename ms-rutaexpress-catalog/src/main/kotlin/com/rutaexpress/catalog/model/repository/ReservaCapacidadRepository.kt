package com.rutaexpress.catalog.model.repository

import com.rutaexpress.catalog.model.entity.ReservaCapacidad
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ReservaCapacidadRepository : JpaRepository<ReservaCapacidad, String> {

    /** Bloquea la reserva del envío: dos liberaciones simultáneas no pueden descontar dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservaCapacidad r WHERE r.shipmentId = :shipmentId")
    fun findLockedByShipmentId(@Param("shipmentId") shipmentId: String): Optional<ReservaCapacidad>
}
