package com.rutaexpress.catalog.config

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
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/common/v2.0}")
    private val issuerUri: String,

    @Value("\${app.security.azure.audience:api://rutaexpress-catalog}")
    private val audience: String,

    @Value("\${app.security.enabled:true}")
    private val securityEnabled: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints: Swagger / OpenAPI documentation
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/api-docs/**"
                    ).permitAll()

                    // Actuator health and metrics
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus"
                    ).permitAll()

                if (securityEnabled) {
                    // Protected catalog domain endpoints
                    auth.requestMatchers("/api/catalog/**").authenticated()
                    auth.anyRequest().authenticated()
                } else {
                    // Bypass in mock/test profile if explicitly disabled
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

    /**
     * Maps Azure AD claims ("roles", "scp", "scope") into Spring Security GrantedAuthority
     * Prepending 'ROLE_' for application roles defined in Azure App Registration.
     */
    @Bean
    fun grantedAuthoritiesExtractor(): AzureJwtAuthenticationConverter {
        return AzureJwtAuthenticationConverter()
    }

    /**
     * Validates Issuer, Expiration and Audience claim according to Azure AD specs
     */
    @Bean
    @ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true", matchIfMissing = true)
    fun jwtDecoder(): JwtDecoder {
        val jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder
        val audienceValidator = AudienceValidator(audience)
        val withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri)
        val validator = DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)
        jwtDecoder.setJwtValidator(validator)
        return jwtDecoder
    }
}

/**
 * Custom validator to verify the JWT 'aud' claim matches the Azure AD App Registration API URI
 */
class AudienceValidator(private val expectedAudience: String) : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val audiences = jwt.audience
        return if (audiences != null && (audiences.contains(expectedAudience) || expectedAudience.isBlank())) {
            OAuth2TokenValidatorResult.success()
        } else {
            val error = OAuth2Error(
                "invalid_token",
                "The required audience '$expectedAudience' is missing or not matched in the token: $audiences",
                null
            )
            OAuth2TokenValidatorResult.failure(error)
        }
    }
}

/**
 * Concrete Converter to preserve generic types <Jwt, AbstractAuthenticationToken>
 * for Spring's GenericConversionService inspection.
 */
class AzureJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = mutableListOf<GrantedAuthority>()

        // 1. Azure AD App Roles ("roles" claim array)
        val rolesClaim = jwt.claims["roles"]
        if (rolesClaim is List<*>) {
            rolesClaim.forEach { role ->
                if (role is String && role.isNotBlank()) {
                    val roleName = if (role.startsWith("ROLE_")) role else "ROLE_${role.uppercase()}"
                    authorities.add(SimpleGrantedAuthority(roleName))
                }
            }
        }

        // 2. Azure AD Delegated Scopes ("scp" or "scope" claim string or array)
        val scpClaim = jwt.claims["scp"] ?: jwt.claims["scope"]
        if (scpClaim is String) {
            scpClaim.split(" ")
                .filter { it.isNotBlank() }
                .forEach { scope -> authorities.add(SimpleGrantedAuthority("SCOPE_$scope")) }
        } else if (scpClaim is List<*>) {
            scpClaim.forEach { scope ->
                if (scope is String && scope.isNotBlank()) {
                    authorities.add(SimpleGrantedAuthority("SCOPE_$scope"))
                }
            }
        }

        return JwtAuthenticationToken(jwt, authorities, jwt.subject ?: "user")
    }
}

