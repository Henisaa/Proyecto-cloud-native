package com.rutaexpress.catalog.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class SecurityAudienceAndRolesTests {

    private val securityConfig = SecurityConfig(
        issuerUri = "https://login.microsoftonline.com/common/v2.0",
        audience = "api://rutaexpress-catalog",
        securityEnabled = true
    )

    @Test
    fun `AudienceValidator should accept token with matching audience`() {
        val validator = AudienceValidator("api://rutaexpress-catalog")

        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            .audience(listOf("api://rutaexpress-catalog"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val result = validator.validate(jwt)
        assertFalse(result.hasErrors(), "Validation should succeed when audience matches")
    }

    @Test
    fun `AudienceValidator should reject token with invalid or missing audience`() {
        val validator = AudienceValidator("api://rutaexpress-catalog")

        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            .audience(listOf("api://different-service"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val result = validator.validate(jwt)
        assertTrue(result.hasErrors(), "Validation should fail when audience does not match")
        assertEquals("invalid_token", result.errors.iterator().next().errorCode)
    }

    @Test
    fun `grantedAuthoritiesExtractor should map Azure AD roles claim to Spring GrantedAuthority`() {
        val converter = securityConfig.grantedAuthoritiesExtractor()

        val jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", "courier-admin-01")
            .claim("roles", listOf("Admin", "Operador"))
            .claim("scp", "Catalog.Read Catalog.Write")
            .audience(listOf("api://rutaexpress-catalog"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val authToken = converter.convert(jwt)
        assertNotNull(authToken)

        val authorities = authToken!!.authorities
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_ADMIN")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_OPERADOR")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("SCOPE_Catalog.Read")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("SCOPE_Catalog.Write")))
        assertEquals("courier-admin-01", authToken.name)
    }
}
