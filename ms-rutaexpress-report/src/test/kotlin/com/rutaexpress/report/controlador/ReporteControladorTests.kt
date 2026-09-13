package com.rutaexpress.report.controlador

import com.rutaexpress.report.configuracion.SeguridadConfig
import com.rutaexpress.report.dominio.EstadoConteo
import com.rutaexpress.report.dominio.ServicioConteo
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ReporteControlador::class)
@Import(SeguridadConfig::class)
class ReporteControladorTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repositorio: ReporteEnvioRepositorio

    private fun anyDate(): OffsetDateTime = any(OffsetDateTime::class.java) ?: OffsetDateTime.now()

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `obtenerKpis retorna metricas calculadas correctamente`() {
        `when`(repositorio.countByCreatedAtGreaterThanEqual(anyDate())).thenReturn(120L)
        `when`(repositorio.findAverageLeadTimeSince(anyDate())).thenReturn(45.5)
        `when`(repositorio.countShipmentsByStatusSince(anyDate())).thenReturn(
            listOf(
                EstadoConteo("EN_RUTA", 50L),
                EstadoConteo("ENTREGADO", 70L)
            )
        )

        mockMvc.perform(get("/api/report/kpis?range=last24h"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalShipments").value(120))
            .andExpect(jsonPath("$.shipmentsPerHour").value(5.0))
            .andExpect(jsonPath("$.avgLeadTimeMinutes").value(45.5))
            .andExpect(jsonPath("$.activeShipmentsByStatus.EN_RUTA").value(50))
            .andExpect(jsonPath("$.activeShipmentsByStatus.ENTREGADO").value(70))
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `obtenerTopServicios calcula distribucion y porcentajes`() {
        `when`(repositorio.countByCreatedAtGreaterThanEqual(anyDate())).thenReturn(100L)
        `when`(repositorio.countShipmentsByServiceSince(anyDate())).thenReturn(
            listOf(
                ServicioConteo("EXPRESS", 60L),
                ServicioConteo("ESTANDAR", 40L)
            )
        )

        mockMvc.perform(get("/api/report/top-services?range=last7d"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].serviceType").value("EXPRESS"))
            .andExpect(jsonPath("$[0].totalShipments").value(60))
            .andExpect(jsonPath("$[0].percentage").value(60.0))
            .andExpect(jsonPath("$[1].serviceType").value("ESTANDAR"))
            .andExpect(jsonPath("$[1].totalShipments").value(40))
            .andExpect(jsonPath("$[1].percentage").value(40.0))
    }

    @Test
    fun `peticion sin autenticar es rechazada`() {
        mockMvc.perform(get("/api/report/kpis"))
            .andExpect(status().isUnauthorized)
    }
}
