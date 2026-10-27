package com.rutaexpress.shipments

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
    @Test
    fun `JSON invalido responde ProblemDetail sin exponer excepciones`() {
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.instance").value("/api/shipments"))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.correlationId").isNotEmpty)
    }

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
            .andExpect(jsonPath("$.content").isArray)
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

    private fun cuerpoCon(vararg cambios: Pair<String, Any?>): String {
        val base = mutableMapOf<String, Any?>(
            "serviceId" to 1,
            "zone" to "URBAN_CENTER",
            "packagesCount" to 1,
            "weightKg" to 2.5,
            "volumeM3" to 0.02,
            "recipientName" to "Ana Pérez",
            "originAddress" to "Origen 1",
            "destinationAddress" to "Destino 2",
        )
        cambios.forEach { (clave, valor) -> base[clave] = valor }
        return objectMapper.writeValueAsString(base)
    }

    private fun crearConCuerpo(cuerpo: String) = mockMvc.perform(
        post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
    )

    @Test
    fun `una zona fuera del catalogo devuelve 400`() {
        crearConCuerpo(cuerpoCon("zone" to "MARTE")).andExpect(status().isBadRequest)
    }

    @Test
    fun `mas de 99999 paquetes devuelve 400`() {
        crearConCuerpo(cuerpoCon("packagesCount" to 100_000)).andExpect(status().isBadRequest)
    }

    @Test
    fun `un nombre mas largo que la columna devuelve 400 y no un 500 de la base`() {
        crearConCuerpo(cuerpoCon("recipientName" to "x".repeat(121)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.validationErrors.recipientName").exists())
    }

    @Test
    fun `un precio negativo devuelve 400`() {
        crearConCuerpo(cuerpoCon("price" to -1)).andExpect(status().isBadRequest)
    }

    @Test
    fun `un JSON mal formado devuelve 400`() {
        crearConCuerpo("{ esto no es json").andExpect(status().isBadRequest)
    }

    @Test
    fun `un parametro con tipo invalido devuelve 400`() {
        mockMvc.perform(get("/api/shipments").param("page", "abc")).andExpect(status().isBadRequest)
    }

    @Test
    fun `un correo vacio se guarda como nulo`() {
        crearConCuerpo(cuerpoCon("recipientEmail" to ""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.recipientEmail").doesNotExist())
    }

    @Test
    fun `la respuesta incluye las transiciones permitidas`() {
        crearConCuerpo(cuerpoCon())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.allowedTransitions[0]").value("ACEPTADO"))
    }

    @Test
    fun `el resumen devuelve los conteos`() {
        crearEnvio()
        mockMvc.perform(get("/api/shipments/summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.byStatus.CREADO").exists())
    }

    @Test
    fun `el tamano de pagina se limita a 100`() {
        mockMvc.perform(get("/api/shipments").param("size", "5000"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(100))
    }

}
