package com.rutaexpress.shipments

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = ["DESPACHADOR"])
class EnvioControladorTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun crearEnvio(): String {
        val respuesta = mockMvc.perform(
            post("/api/shipments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "serviceId" to 1,
                            "zone" to "URBAN_CENTER",
                            "packagesCount" to 1,
                            "weightKg" to 2.5,
                            "volumeM3" to 0.02,
                            "recipientName" to "Ana Pérez",
                            "recipientEmail" to "ana@example.com",
                            "originAddress" to "Av. Siempre Viva 123",
                            "destinationAddress" to "Calle Falsa 456"
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("CREADO"))
            .andReturn()

        val json = objectMapper.readTree(respuesta.response.contentAsString)
        return json.path("id").asText()
    }

    @Test
    fun `crear envío y recorrer el flujo completo de estados`() {
        val id = crearEnvio()

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ACEPTADO"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("ACEPTADO"))

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"EN_BODEGA"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("EN_BODEGA"))

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"EN_RUTA"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("EN_RUTA"))

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ENTREGADO"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("ENTREGADO"))
    }

    @Test
    fun `no se puede pasar a en ruta sin aceptar`() {
        val id = crearEnvio()

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"EN_RUTA"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `listar envíos por estado`() {
        crearEnvio()

        mockMvc.perform(get("/api/shipments").param("status", "CREADO"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `estado inválido devuelve 400`() {
        val id = crearEnvio()

        mockMvc.perform(
            put("/api/shipments/$id/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"VOLANDO"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `envío inexistente devuelve 404`() {
        mockMvc.perform(get("/api/shipments/no-existe")).andExpect(status().isNotFound)
    }
}
