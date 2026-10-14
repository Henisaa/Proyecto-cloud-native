package com.rutaexpress.report.repositorio

import com.rutaexpress.report.dominio.EstadoConteo
import com.rutaexpress.report.dominio.ServicioConteo
import com.rutaexpress.report.dominio.SnapshotEnvio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ReporteEnvioRepositorio : JpaRepository<SnapshotEnvio, String> {

    fun countByCreatedAtGreaterThanEqual(since: OffsetDateTime): Long

    @Query("SELECT AVG(s.leadTimeMinutes) FROM SnapshotEnvio s WHERE s.createdAt >= :since AND s.leadTimeMinutes IS NOT NULL")
    fun findAverageLeadTimeSince(@Param("since") since: OffsetDateTime): Double?

    @Query("SELECT new com.rutaexpress.report.dominio.EstadoConteo(s.currentStatus, COUNT(s)) FROM SnapshotEnvio s WHERE s.createdAt >= :since GROUP BY s.currentStatus")
    fun countShipmentsByStatusSince(@Param("since") since: OffsetDateTime): List<EstadoConteo>

    @Query("SELECT new com.rutaexpress.report.dominio.ServicioConteo(s.serviceType, COUNT(s)) FROM SnapshotEnvio s WHERE s.createdAt >= :since GROUP BY s.serviceType ORDER BY COUNT(s) DESC")
    fun countShipmentsByServiceSince(@Param("since") since: OffsetDateTime): List<ServicioConteo>
}
