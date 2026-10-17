package com.rutaexpress.bff.controller

import com.rutaexpress.bff.config.SecurityConfig
import com.rutaexpress.bff.service.MqAdminCliente
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(BrokerAdminController::class)
@Import(SecurityConfig::class)
class BrokerAdminSecurityTests {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var decoder: JwtDecoder
    @MockitoBean lateinit var mqAdmin: MqAdminCliente

    @Test fun `requiere token para monitoreo`() { mvc.perform(get("/api/bff/admin/mq/queues")).andExpect(status().isUnauthorized) }
    @Test @WithMockUser(roles=["DESPACHADOR"])
    fun `reserva monitoreo para administradores`() { mvc.perform(get("/api/bff/admin/kafka/topics")).andExpect(status().isForbidden) }
    @Test @WithMockUser(roles=["ADMIN"])
    fun `impide proxy a recursos no autorizados`() { mvc.perform(get("/api/bff/admin/mq/users")).andExpect(status().isNotFound) }

    @Test
    fun `rechaza crear una cola sin token`() {
        mvc.perform(
            post("/api/bff/admin/mq/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"q.demo"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `rechaza crear una cola para un rol que no es Admin`() {
        mvc.perform(
            post("/api/bff/admin/mq/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"q.demo"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `Admin crea una cola y recibe el estado del administrador`() {
        whenever(
            mqAdmin.enviar(
                eq(HttpMethod.POST),
                eq("/api/mq-admin/queues"),
                eq("admin-token"),
                any(),
                any(),
            ),
        ).thenReturn(ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).body("""{"name":"q.demo"}"""))

        val response = mvc.perform(
            post("/api/bff/admin/mq/queues")
                .with(jwt().jwt { it.tokenValue("admin-token") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"q.demo"}"""),
        ).andExpect(status().isCreated)
            .andExpect(content().json("""{"name":"q.demo"}"""))
            .andReturn().response

        assertEquals(MediaType.APPLICATION_JSON, MediaType.parseMediaType(response.contentType!!))
    }

    @Test
    fun `conserva el 400 y el detalle de mq-admin`() {
        val detalle = """{"type":"about:blank","title":"Bad Request","status":400,"detail":"El nombre de la cola es obligatorio"}"""
        whenever(
            mqAdmin.enviar(
                eq(HttpMethod.POST),
                eq("/api/mq-admin/queues"),
                eq("admin-token"),
                any(),
                any(),
            ),
        ).thenReturn(ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detalle))

        mvc.perform(
            post("/api/bff/admin/mq/queues")
                .with(jwt().jwt { it.tokenValue("admin-token") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().json(detalle))
    }
}
