package com.rutaexpress.shipments.dominio

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface EnvioRepositorio : JpaRepository<Envio, String> {

    @Query(
        """
        SELECT e FROM Envio e
        WHERE (:status IS NULL OR e.status = :status)
          AND (:desde IS NULL OR e.createdAt >= :desde)
          AND (:hasta IS NULL OR e.createdAt <= :hasta)
        ORDER BY e.createdAt DESC
        """
    )
    fun buscar(
        @Param("status") status: EstadoEnvio?,
        @Param("desde") desde: Instant?,
        @Param("hasta") hasta: Instant?
    ): List<Envio>
}
