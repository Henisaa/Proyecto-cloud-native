package com.rutaexpress.shipments.configuracion

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SeguridadConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/common/v2.0}")
    private val issuerUri: String,

    @Value("\${app.security.azure.audience:api://rutaexpress-shipments}")
    private val audience: String,

    @Value("\${app.security.enabled:true}")
    private val securityEnabled: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/api-docs/**"
                    ).permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus"
                    ).permitAll()

                if (securityEnabled) {
                    auth.requestMatchers("/api/shipments/**").authenticated()
                    auth.anyRequest().authenticated()
                } else {
                    auth.anyRequest().permitAll()
                }
            }

        if (securityEnabled) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(AzureRolesConverter())
                }
            }
        }

        return http.build()
    }

    @Bean
    @ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true", matchIfMissing = true)
    fun jwtDecoder(): JwtDecoder {
        val decoder = JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder
        val validator = DelegatingOAuth2TokenValidator(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            AudienceValidator(audience)
        )
        decoder.setJwtValidator(validator)
        return decoder
    }
}

class AudienceValidator(private val expectedAudience: String) : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val audiences = jwt.audience
        return if (expectedAudience.isBlank() || (audiences != null && audiences.contains(expectedAudience))) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    "invalid_token",
                    "El audience del token $audiences no coincide con el esperado: $expectedAudience",
                    null
                )
            )
        }
    }
}

class AzureRolesConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = mutableListOf<GrantedAuthority>()

        val rolesClaim = jwt.claims["roles"]
        if (rolesClaim is List<*>) {
            rolesClaim.forEach { role ->
                if (role is String && role.isNotBlank()) {
                    val roleName = if (role.startsWith("ROLE_")) role else "ROLE_${role.uppercase()}"
                    authorities.add(SimpleGrantedAuthority(roleName))
                }
            }
        }

        val scpClaim = jwt.claims["scp"] ?: jwt.claims["scope"]
        if (scpClaim is String) {
            scpClaim.split(" ").filter { it.isNotBlank() }
                .forEach { authorities.add(SimpleGrantedAuthority("SCOPE_$it")) }
        } else if (scpClaim is List<*>) {
            scpClaim.filterIsInstance<String>().filter { it.isNotBlank() }
                .forEach { authorities.add(SimpleGrantedAuthority("SCOPE_$it")) }
        }

        return JwtAuthenticationToken(jwt, authorities, jwt.subject ?: "usuario")
    }
}
