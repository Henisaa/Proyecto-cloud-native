package com.rutaexpress.bff.controller
import com.rutaexpress.bff.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
@WebMvcTest(BrokerAdminController::class)
@Import(SecurityConfig::class)
class BrokerAdminSecurityTests {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var decoder: JwtDecoder
    @Test fun `requiere token para monitoreo`() { mvc.perform(get("/api/bff/admin/mq/queues")).andExpect(status().isUnauthorized) }
    @Test @WithMockUser(roles=["DESPACHADOR"])
    fun `reserva monitoreo para administradores`() { mvc.perform(get("/api/bff/admin/kafka/topics")).andExpect(status().isForbidden) }
    @Test @WithMockUser(roles=["ADMIN"])
    fun `impide proxy a recursos no autorizados`() { mvc.perform(get("/api/bff/admin/mq/users")).andExpect(status().isNotFound) }
}
