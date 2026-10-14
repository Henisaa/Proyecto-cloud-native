package com.rutaexpress.demo

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant

/**
 * Reemplaza la validación criptográfica del IDaaS durante las pruebas.
 * El valor del token es el rol a simular: "admin", "operator" o "anonymous".
 */
@Configuration
class StubJwtDecoderConfiguration {
	@Bean
	fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
		val roles = when (token) {
			"admin" -> listOf("Admin")
			"operator" -> listOf("Operador del dominio")
			else -> emptyList()
		}
		Jwt.withTokenValue(token)
			.header("alg", "none")
			.subject("tester")
			.claim("roles", roles)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build()
	}
}
