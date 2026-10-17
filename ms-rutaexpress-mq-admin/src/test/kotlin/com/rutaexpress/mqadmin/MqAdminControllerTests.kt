package com.rutaexpress.mqadmin

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MqAdminController::class)
@Import(SeguridadConfig::class)
class MqAdminControllerTests {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var monitor: MqAdminService
    @MockitoBean lateinit var admin: RabbitAdminService
    @MockitoBean lateinit var decoder: JwtDecoder

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `rechaza una cola sin nombre con 400 antes de llamar al broker`() {
        mvc.perform(post("/api/mq-admin/queues").contentType(MediaType.APPLICATION_JSON).content("""{"name":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(containsString("nombre de la cola")))
        verifyNoInteractions(admin)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `crea una cola y responde 201`() {
        whenever(admin.crearCola(any())).thenReturn(mapOf("name" to "q.prueba", "created" to true))

        mvc.perform(
            post("/api/mq-admin/queues").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"q.prueba","ttlMs":60000}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("q.prueba"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `responde 400 con el motivo cuando el servicio rechaza la configuracion`() {
        whenever(admin.crearExchange(any())).thenThrow(IllegalArgumentException("El tipo de exchange 'x' no es válido"))

        mvc.perform(
            post("/api/mq-admin/exchanges").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"ex.prueba","type":"x"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(containsString("no es válido")))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `responde 409 al eliminar la topologia protegida sin force`() {
        whenever(admin.eliminarCola("q.rutaexpress.command", false))
            .thenThrow(ConflictoException("protegida: use force=true"))

        mvc.perform(delete("/api/mq-admin/queues/q.rutaexpress.command"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value(containsString("force=true")))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `responde 503 cuando ningun nodo de RabbitMQ responde`() {
        whenever(monitor.colas()).thenThrow(BrokerNoDisponibleException("Ningún nodo responde"))

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/mq-admin/queues"))
            .andExpect(status().isServiceUnavailable)
    }
}
