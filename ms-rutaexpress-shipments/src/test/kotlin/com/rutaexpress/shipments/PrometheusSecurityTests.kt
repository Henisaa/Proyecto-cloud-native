package com.rutaexpress.shipments

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.security.enabled=true"])
class PrometheusSecurityTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `prometheus rechaza solicitudes anónimas`() {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["DESPACHADOR"])
    fun `prometheus permite solamente el rol Admin`() {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden)
    }
}
