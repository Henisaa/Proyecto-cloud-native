package com.rutaexpress.mqadmin

import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MqAdminController::class)
@Import(SeguridadConfig::class)
class AdminSecurityTests {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var monitor: MqAdminService
    @MockitoBean lateinit var admin: RabbitAdminService
    @MockitoBean lateinit var decoder: JwtDecoder

    @Test
    fun `rechaza acceso anonimo`() {
        mvc.perform(get("/api/mq-admin/queues")).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["CLIENTE"])
    fun `rechaza acceso sin rol administrador`() {
        mvc.perform(get("/api/mq-admin/queues")).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `rechaza crear colas sin rol administrador`() {
        mvc.perform(post("/api/mq-admin/queues").contentType(MediaType.APPLICATION_JSON).content("""{"name":"q.prueba"}"""))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `permite consultas de administrador`() {
        whenever(monitor.colas()).thenReturn(emptyList())
        mvc.perform(get("/api/mq-admin/queues")).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `rechaza retencion invalida antes de consultar el broker`() {
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/mq-admin/retention")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"commandTtlMs":0,"dlqTtlMs":0,"maxLength":0}""")
        ).andExpect(status().isBadRequest)
    }
}
