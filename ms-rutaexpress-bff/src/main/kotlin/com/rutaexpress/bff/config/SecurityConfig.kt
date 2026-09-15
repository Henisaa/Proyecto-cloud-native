package com.rutaexpress.bff.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import java.time.Instant

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/common/v2.0}")
    private val issuerUri: String,
    @Value("\${rutaexpress.security.audience:api://rutaexpress-api}")
    private val expectedAudience: String,
    @Value("\${app.security.enabled:true}")
    private val securityEnabled: Boolean,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint())
                    .accessDeniedHandler(customAccessDeniedHandler())
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Endpoints públicos de diagnóstico y documentación
                    .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info/**",
                        "/actuator/prometheus/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()

                if (securityEnabled) {
                    // Autorización granular por roles de negocio de Azure AD
                    auth
                        .requestMatchers("/api/bff/report/**").hasRole("ADMIN")
                        .requestMatchers("/api/bff/audit/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers("/api/bff/catalog/manage/**").hasRole("ADMIN")
                        .requestMatchers("/api/bff/catalog/**").hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE")
                        .requestMatchers("/api/bff/dashboard/**").authenticated()
                        .requestMatchers("/api/bff/auth/**").authenticated()
                        .requestMatchers("/api/bff/**").authenticated()
                        .anyRequest().authenticated()
                } else {
                    auth.anyRequest().permitAll()
                }
            }

        if (securityEnabled) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())
                }
            }
        }

        return http.build()
    }

    @Bean
    fun grantedAuthoritiesExtractor(): AzureJwtAuthenticationConverter {
        return AzureJwtAuthenticationConverter()
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    @ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true", matchIfMissing = true)
    fun jwtDecoder(): JwtDecoder {
        val nimbusJwtDecoder = if (issuerUri.contains("common") || issuerUri.contains("{tenantid}")) {
            NimbusJwtDecoder.withJwkSetUri("https://login.microsoftonline.com/common/discovery/v2.0/keys").build()
        } else {
            try {
                JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder
            } catch (e: Exception) {
                NimbusJwtDecoder.withJwkSetUri("https://login.microsoftonline.com/common/discovery/v2.0/keys").build()
            }
        }
        val audienceValidator = AudienceValidator(expectedAudience)
        val withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri)
        val validator = DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)
        nimbusJwtDecoder.setJwtValidator(validator)
        return nimbusJwtDecoder
    }

    @Bean
    fun customAuthenticationEntryPoint(): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { request: HttpServletRequest, response: HttpServletResponse, authException ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            val body = mapOf(
                "timestamp" to Instant.now().toString(),
                "status" to HttpStatus.UNAUTHORIZED.value(),
                "error" to "Unauthorized",
                "message" to (authException.message ?: "Token JWT no proporcionado o inválido"),
                "path" to request.requestURI
            )
            objectMapper.writeValue(response.outputStream, body)
        }
    }

    @Bean
    fun customAccessDeniedHandler(): AccessDeniedHandler {
        return AccessDeniedHandler { request: HttpServletRequest, response: HttpServletResponse, accessDeniedException ->
            response.status = HttpStatus.FORBIDDEN.value()
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            val body = mapOf(
                "timestamp" to Instant.now().toString(),
                "status" to HttpStatus.FORBIDDEN.value(),
                "error" to "Forbidden",
                "message" to "No tiene los roles requeridos en Azure AD para acceder a este recurso",
                "path" to request.requestURI
            )
            objectMapper.writeValue(response.outputStream, body)
        }
    }
}

/**
 * Validador de claim 'aud' para tokens de Azure AD
 */
class AudienceValidator(private val expectedAudience: String) : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val audiences = jwt.audience ?: emptyList()
        return if (audiences.contains(expectedAudience) || expectedAudience.isBlank()) {
            OAuth2TokenValidatorResult.success()
        } else {
            val error = OAuth2Error(
                "invalid_token",
                "El audience del token no coincide con el configurado para RutaExpress: $audiences",
                null
            )
            OAuth2TokenValidatorResult.failure(error)
        }
    }
}

/**
 * Extrae roles de Azure AD (claim 'roles') y scopes ('scp') normalizándolos a GrantedAuthority
 */
class AzureJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = mutableListOf<GrantedAuthority>()

        // 1. Roles asignados en Azure AD App Registration (claim 'roles')
        val rolesClaim = jwt.claims["roles"]
        if (rolesClaim is List<*>) {
            rolesClaim.filterIsInstance<String>().forEach { role ->
                if (role.isNotBlank()) {
                    val roleName = if (role.startsWith("ROLE_")) role else "ROLE_${role.trim().uppercase()}"
                    authorities.add(SimpleGrantedAuthority(roleName))
                }
            }
        }

        // 2. Scopes delegados (claim 'scp' o 'scope')
        val scpClaim = jwt.claims["scp"] ?: jwt.claims["scope"]
        if (scpClaim is String) {
            scpClaim.split(" ").filter { it.isNotBlank() }.forEach { scope ->
                authorities.add(SimpleGrantedAuthority("SCOPE_${scope.trim()}"))
            }
        } else if (scpClaim is List<*>) {
            scpClaim.filterIsInstance<String>().forEach { scope ->
                authorities.add(SimpleGrantedAuthority("SCOPE_${scope.trim()}"))
            }
        }

        val principalName = jwt.getClaimAsString("preferred_username")
            ?: jwt.getClaimAsString("upn")
            ?: jwt.getClaimAsString("name")
            ?: jwt.subject
            ?: "usuario-rutaexpress"

        return JwtAuthenticationToken(jwt, authorities, principalName)
    }
}
