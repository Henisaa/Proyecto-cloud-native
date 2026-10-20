package com.rutaexpress.bff.controller

import com.rutaexpress.bff.config.SecurityConfig
import com.rutaexpress.bff.model.dto.GatewayHealthDto
import com.rutaexpress.bff.service.BffGatewayService
import com.rutaexpress.bff.service.DownstreamException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Rutas nuevas por rol (matriz 10.2) y propagación del mensaje exacto del backend (P1-10, P1-16). */
@WebMvcTest(BffController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.enabled=true"])
class BffRoutesTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var bffService: BffGatewayService

    @MockitoBean
    private lateinit var jwtDecoder: org.springframework.security.oauth2.jwt.JwtDecoder

    @Test
    @WithMockUser(roles = ["CLIENTE"])
    fun `un cliente puede ver el resumen pero no la linea de tiempo ni reintentar`() {
        `when`(bffService.fetchShipmentsSummary("")).thenReturn(mapOf("total" to 1))

        mockMvc.perform(get("/api/bff/shipments/summary")).andExpect(status().isOk)
        mockMvc.perform(get("/api/bff/shipments/s-1/timeline")).andExpect(status().isForbidden)
        mockMvc.perform(post("/api/bff/shipments/s-1/capacity/retry")).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["AUDITOR"])
    fun `un auditor ve la linea de tiempo pero no cambia estados`() {
        `when`(bffService.fetchShipmentTimeline("s-1", "")).thenReturn(mapOf("events" to emptyList<Any>()))

        mockMvc.perform(get("/api/bff/shipments/s-1/timeline")).andExpect(status().isOk)
        mockMvc.perform(
            put("/api/bff/shipments/s-1/status").contentType(MediaType.APPLICATION_JSON).content("""{"status":"ACEPTADO"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `un despachador reintenta la reserva pero no edita capacidades`() {
        `when`(bffService.retryShipmentCapacity("s-1", "")).thenReturn(mapOf("id" to "s-1"))

        mockMvc.perform(post("/api/bff/shipments/s-1/capacity/retry")).andExpect(status().isOk)
        mockMvc.perform(
            put("/api/bff/catalog/manage/services/1/capacities/2")
                .contentType(MediaType.APPLICATION_JSON).content("""{"maxDailyPackages":10}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `un admin edita capacidades y consulta el estado de los servicios`() {
        `when`(bffService.updateCapacity(1, 2, mapOf("maxDailyPackages" to 10), "")).thenReturn(mapOf("id" to 2))
        `when`(bffService.getStatus()).thenReturn(GatewayHealthDto(azureIdaasConfigured = true, services = mapOf("catalog" to "UP")))

        mockMvc.perform(
            put("/api/bff/catalog/manage/services/1/capacities/2")
                .contentType(MediaType.APPLICATION_JSON).content("""{"maxDailyPackages":10}""")
        ).andExpect(status().isOk)
        mockMvc.perform(get("/api/bff/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.services.catalog").value("UP"))
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `el estado de los servicios es solo para admin`() {
        mockMvc.perform(get("/api/bff/status")).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `un 409 del backend llega al cliente con su mensaje exacto`() {
        `when`(bffService.changeShipmentStatus("s-1", "EN_BODEGA", "")).thenThrow(
            DownstreamException(
                HttpStatus.CONFLICT,
                """{"status":409,"message":"Capacidad rechazada: cancele el envío o reintente la reserva"}""",
                "ms-rutaexpress-shipments",
            )
        )

        mockMvc.perform(
            put("/api/bff/shipments/s-1/status").contentType(MediaType.APPLICATION_JSON).content("""{"status":"EN_BODEGA"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Capacidad rechazada: cancele el envío o reintente la reserva"))
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `un servicio caido responde 503 con un mensaje claro`() {
        `when`(bffService.fetchShipmentsSummary("")).thenThrow(
            DownstreamException(HttpStatus.SERVICE_UNAVAILABLE, null, "ms-rutaexpress-shipments")
        )

        mockMvc.perform(get("/api/bff/shipments/summary"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.detail").exists())
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `un JSON mal formado responde 400 y no 500`() {
        mockMvc.perform(
            post("/api/bff/shipments").contentType(MediaType.APPLICATION_JSON).content("{ no es json")
        ).andExpect(status().isBadRequest)
    }
}
