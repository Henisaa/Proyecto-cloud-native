package com.rutaexpress.report.controlador

import com.rutaexpress.report.dominio.KpisRespuesta
import com.rutaexpress.report.dominio.TopServicioRespuesta
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import kotlin.math.round

@RestController
@RequestMapping("/api/report")
@Tag(name = "Reportes y KPIs", description = "Endpoints de analítica de envíos y métricas operacionales de RutaExpress")
class ReporteControlador(
    private val repositorio: ReporteEnvioRepositorio
) {

    @GetMapping("/kpis")
    @Operation(summary = "Obtener KPIs operacionales", description = "Calcula envíos totales, envíos por hora, lead time promedio y distribución de estados")
    @Cacheable(value = ["kpis"], key = "#range")
    fun obtenerKpis(
        @RequestParam(defaultValue = "last24h") range: String
    ): ResponseEntity<KpisRespuesta> {
        val (since, hours) = resolverRango(range)
        val total = repositorio.countByCreatedAtGreaterThanEqual(since)
        val enviosPorHora = if (hours > 0) redondear(total.toDouble() / hours) else 0.0
        val leadTimePromedio = redondear(repositorio.findAverageLeadTimeSince(since) ?: 0.0)
        val estados = repositorio.countShipmentsByStatusSince(since).associate { it.status to it.count }

        val respuesta = KpisRespuesta(
            range = range,
            totalShipments = total,
            shipmentsPerHour = enviosPorHora,
            avgLeadTimeMinutes = leadTimePromedio,
            activeShipmentsByStatus = estados
        )
        return ResponseEntity.ok(respuesta)
    }

    @GetMapping("/top-services")
    @Operation(summary = "Obtener servicios más demandados", description = "Lista los tipos de servicio ordenados por volumen con su porcentaje de uso")
    @Cacheable(value = ["top-services"], key = "#range")
    fun obtenerTopServicios(
        @RequestParam(defaultValue = "last7d") range: String
    ): ResponseEntity<List<TopServicioRespuesta>> {
        val (since, _) = resolverRango(range)
        val total = repositorio.countByCreatedAtGreaterThanEqual(since)
        val servicios = repositorio.countShipmentsByServiceSince(since)

        val respuesta = servicios.map {
            val porcentaje = if (total > 0) redondear((it.count.toDouble() / total.toDouble()) * 100.0) else 0.0
            TopServicioRespuesta(
                serviceType = it.serviceType,
                totalShipments = it.count,
                percentage = porcentaje
            )
        }
        return ResponseEntity.ok(respuesta)
    }

    private fun resolverRango(range: String): Pair<OffsetDateTime, Double> {
        val ahora = OffsetDateTime.now()
        return when (range.lowercase()) {
            "last1h" -> ahora.minusHours(1) to 1.0
            "last24h" -> ahora.minusHours(24) to 24.0
            "last7d" -> ahora.minusDays(7) to (7.0 * 24.0)
            "last30d" -> ahora.minusDays(30) to (30.0 * 24.0)
            else -> ahora.minusHours(24) to 24.0
        }
    }

    private fun redondear(valor: Double): Double {
        return round(valor * 100.0) / 100.0
    }
}
