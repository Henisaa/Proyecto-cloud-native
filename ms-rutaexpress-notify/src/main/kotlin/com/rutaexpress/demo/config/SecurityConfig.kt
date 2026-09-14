package com.rutaexpress.demo.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
	@Bean
	@ConditionalOnProperty(name = ["rutaexpress.security.enabled"], havingValue = "true", matchIfMissing = true)
	fun securedFilterChain(http: HttpSecurity): SecurityFilterChain = http
		.csrf { it.disable() }
		.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		.authorizeHttpRequests { auth ->
			auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/actuator/prometheus").hasRole("ADMIN")
				.anyRequest().authenticated()
		}
		.oauth2ResourceServer { resource ->
			resource.jwt { jwt -> jwt.jwtAuthenticationConverter(rolesConverter()) }
		}
		.build()

	@Bean
	@ConditionalOnProperty(name = ["rutaexpress.security.enabled"], havingValue = "false")
	fun developmentFilterChain(http: HttpSecurity): SecurityFilterChain = http
		.csrf { it.disable() }
		.authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
		.build()

	/** Los roles de Azure AD se emiten como en el App Registration ("Admin"); Spring espera ROLE_ADMIN. */
	private fun rolesConverter(): Converter<Jwt, AbstractAuthenticationToken> = Converter { jwt ->
		val authorities = jwt.getClaimAsStringList(ROLES_CLAIM)
			.orEmpty()
			.map { SimpleGrantedAuthority("ROLE_" + it.uppercase()) }
		JwtAuthenticationToken(jwt, authorities, jwt.subject ?: jwt.tokenValue)
	}

	private companion object {
		const val ROLES_CLAIM = "roles"
	}
}
