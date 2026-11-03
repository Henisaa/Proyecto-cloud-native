package com.rutaexpress.shipments.dominio

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface EnvioRepositorio : JpaRepository<Envio, String>, JpaSpecificationExecutor<Envio> {
    fun findByTrackingCodeIgnoreCase(trackingCode: String): java.util.Optional<Envio>


    @Query("SELECT e.status, COUNT(e) FROM Envio e GROUP BY e.status")
    fun contarPorEstado(): List<Array<Any>>

    @Query("SELECT e.status, COUNT(e) FROM Envio e WHERE e.ownerOid = :owner GROUP BY e.status")
    fun contarPorEstadoDe(@Param("owner") owner: String): List<Array<Any>>

    @Query("SELECT e.capacityStatus, COUNT(e) FROM Envio e GROUP BY e.capacityStatus")
    fun contarPorCapacidad(): List<Array<Any>>

    @Query("SELECT e.capacityStatus, COUNT(e) FROM Envio e WHERE e.ownerOid = :owner GROUP BY e.capacityStatus")
    fun contarPorCapacidadDe(@Param("owner") owner: String): List<Array<Any>>

    /** Envíos cancelados que aún tienen el cupo tomado: la compensación de la liberación se reenvía. */
    @Query(
        """
        SELECT e FROM Envio e
        WHERE e.status = :cancelado
          AND e.capacityStatus IN :pendientes
          AND e.updatedAt < :antesDe
        """
    )
    fun canceladosConCupoTomado(
        @Param("cancelado") cancelado: EstadoEnvio,
        @Param("pendientes") pendientes: Collection<EstadoCapacidad>,
        @Param("antesDe") antesDe: Instant,
    ): List<Envio>

    /** Envíos aceptados cuya reserva no ha recibido respuesta: el comando se reenvía. */
    @Query(
        """
        SELECT e FROM Envio e
        WHERE e.status = :aceptado
          AND e.capacityStatus = :pendiente
          AND e.updatedAt < :antesDe
        """
    )
    fun reservasSinRespuesta(
        @Param("aceptado") aceptado: EstadoEnvio,
        @Param("pendiente") pendiente: EstadoCapacidad,
        @Param("antesDe") antesDe: Instant,
    ): List<Envio>
}

/** Filtros del listado de envíos. Se arman con Specification para no depender de parámetros nulos en JPQL. */
object EnvioFiltros {
    fun construir(
        estado: EstadoEnvio?,
        capacidad: EstadoCapacidad?,
        desde: Instant?,
        hasta: Instant?,
        texto: String?,
        dueno: String?,
        soloDelDueno: Boolean,
    ): Specification<Envio> = Specification { root, _, cb ->
        val condiciones = mutableListOf<Predicate>()
        estado?.let { condiciones += cb.equal(root.get<EstadoEnvio>("status"), it) }
        capacidad?.let { condiciones += cb.equal(root.get<EstadoCapacidad>("capacityStatus"), it) }
        desde?.let { condiciones += cb.greaterThanOrEqualTo(root.get<Instant>("createdAt"), it) }
        hasta?.let { condiciones += cb.lessThanOrEqualTo(root.get<Instant>("createdAt"), it) }
        texto?.takeIf { it.isNotBlank() }?.let {
            condiciones += cb.like(cb.upper(root.get<String>("trackingCode")), "%" + it.trim().uppercase() + "%")
        }
        if (soloDelDueno) {
            // Un cliente sin oid identificable no coincide con ningún envío.
            condiciones += cb.equal(root.get<String>("ownerOid"), dueno ?: "")
        }
        cb.and(*condiciones.toTypedArray())
    }
}
