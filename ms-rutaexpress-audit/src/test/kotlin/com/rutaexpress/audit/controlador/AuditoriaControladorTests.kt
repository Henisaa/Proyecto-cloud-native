package com.rutaexpress.audit.controlador

import com.rutaexpress.audit.configuracion.SeguridadConfig
import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuditoriaControlador::class)
@Import(SeguridadConfig::class)
class AuditoriaControladorTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var auditoriaRepositorio: AuditoriaRepositorio

    private fun mockEvento(id: Long = 1L, idEntidad: String = "ENV-001", tipoEntidad: String = "ENVIO") = EventoAuditoria(
        id = id,
        idEntidad = idEntidad,
        tipoEntidad = tipoEntidad,
        tipoEvento = "ESTADO_ACTUALIZADO",
        origen = "ms-rutaexpress-shipments",
        datosPayload = "{\"estado\":\"EN_RUTA\"}",
        usuarioResponsable = "admin@rutaexpress.cl",
        fechaEvento = Instant.now()
    )

    @Test
    @WithMockUser(username = "auditor", roles = ["AUDITOR"])
    fun `listarTodos retorna lista de eventos de auditoria`() {
        val eventos = listOf(mockEvento(1L, "ENV-001"), mockEvento(2L, "ENV-002"))
        `when`(auditoriaRepositorio.findAll()).thenReturn(eventos)

        mockMvc.perform(get("/api/audit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].idEntidad").value("ENV-001"))
            .andExpect(jsonPath("$[1].idEntidad").value("ENV-002"))
    }

    @Test
    @WithMockUser(username = "auditor", roles = ["AUDITOR"])
    fun `obtenerPorEntidad retorna timeline filtrado por idEntidad`() {
        val eventos = listOf(mockEvento(1L, "ENV-999"))
        `when`(auditoriaRepositorio.findByIdEntidadOrderByFechaEventoDesc("ENV-999")).thenReturn(eventos)

        mockMvc.perform(get("/api/audit/entidad/ENV-999"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].idEntidad").value("ENV-999"))
            .andExpect(jsonPath("$[0].tipoEvento").value("ESTADO_ACTUALIZADO"))
    }

    @Test
    @WithMockUser(username = "auditor", roles = ["AUDITOR"])
    fun `obtenerPorTipo retorna eventos filtrados por tipo de entidad`() {
        val eventos = listOf(mockEvento(1L, "ENV-001", "ENVIO"))
        `when`(auditoriaRepositorio.findByTipoEntidadOrderByFechaEventoDesc("ENVIO")).thenReturn(eventos)

        mockMvc.perform(get("/api/audit/tipo/ENVIO"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tipoEntidad").value("ENVIO"))
    }

    @Test
    fun `peticion sin autenticacion a api audit retorna 401 Unauthorized`() {
        mockMvc.perform(get("/api/audit"))
            .andExpect(status().isUnauthorized)
    }
}
