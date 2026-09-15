package com.rutaexpress.bff.controller

import com.rutaexpress.bff.config.AudienceValidator
import com.rutaexpress.bff.config.AzureJwtAuthenticationConverter
import com.rutaexpress.bff.config.SecurityConfig
import com.rutaexpress.bff.model.dto.UserProfileDto
import com.rutaexpress.bff.service.BffGatewayService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(BffController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.enabled=true"])
class BffControllerSecurityTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var bffService: BffGatewayService

    @MockitoBean
    private lateinit var jwtDecoder: org.springframework.security.oauth2.jwt.JwtDecoder

    @Test
    fun `peticion sin autenticacion a api bff es rechazada con 401 Unauthorized`() {
        mockMvc.perform(get("/api/bff/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
    }

    @Test
    @WithMockUser(username = "cliente@correo.cl", roles = ["CLIENTE"])
    fun `usuario con rol CLIENTE no puede acceder a reportes y recibe 403 Forbidden`() {
        mockMvc.perform(get("/api/bff/report/kpis"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
    }

    @Test
    @WithMockUser(username = "admin@rutaexpress.cl", roles = ["ADMIN"])
    fun `usuario con rol ADMIN puede solicitar kpis de reporteria`() {
        `when`(bffService.fetchKpis("last24h", "")).thenReturn(mapOf("totalShipments" to 150))

        mockMvc.perform(get("/api/bff/report/kpis"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "auditor@rutaexpress.cl", roles = ["AUDITOR"])
    fun `usuario con rol AUDITOR puede solicitar timeline de auditoria`() {
        `when`(bffService.fetchTimeline("ENV-123", "")).thenReturn(listOf<Any>())

        mockMvc.perform(get("/api/bff/audit/timeline/ENV-123"))
            .andExpect(status().isOk)
    }

    @Test
    fun `AudienceValidator acepta audience correcto y rechaza audience incorrecto`() {
        val expected = "api://rutaexpress-api"
        val validator = AudienceValidator(expected)

        val validJwt = Jwt.withTokenValue("mock-valid")
            .header("alg", "RS256")
            .claim("aud", listOf(expected))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val invalidJwt = Jwt.withTokenValue("mock-invalid")
            .header("alg", "RS256")
            .claim("aud", listOf("api://otro-servicio-ajeno"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        assertFalse(validator.validate(validJwt).hasErrors(), "El token válido debe pasar sin errores")
        assertTrue(validator.validate(invalidJwt).hasErrors(), "El token con audience incorrecto debe fallar")
    }

    @Test
    fun `AzureJwtAuthenticationConverter mapea roles de Azure AD a GrantedAuthorities con prefijo ROLE_`() {
        val converter = AzureJwtAuthenticationConverter()

        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("preferred_username", "admin@rutaexpress.cl")
            .claim("roles", listOf("Admin", "Despachador"))
            .claim("scp", "read:orders write:orders")
            .build()

        val auth = converter.convert(jwt)

        val authorityNames = auth.authorities.map { it.authority }.toSet()

        assertTrue(authorityNames.contains("ROLE_ADMIN"), "Debe contener ROLE_ADMIN")
        assertTrue(authorityNames.contains("ROLE_DESPACHADOR"), "Debe contener ROLE_DESPACHADOR")
        assertTrue(authorityNames.contains("SCOPE_read:orders"), "Debe contener SCOPE_read:orders")
        assertTrue(authorityNames.contains("SCOPE_write:orders"), "Debe contener SCOPE_write:orders")
        assertEquals("admin@rutaexpress.cl", auth.name)
    }
}
