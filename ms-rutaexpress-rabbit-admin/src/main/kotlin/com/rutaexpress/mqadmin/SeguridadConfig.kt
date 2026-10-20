package com.rutaexpress.mqadmin

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SeguridadConfig {
    @Bean
    @ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true", matchIfMissing = true)
    fun secured(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("ADMIN")
                .anyRequest().denyAll()
        }
        .oauth2ResourceServer { resource -> resource.jwt { config ->
            config.jwtAuthenticationConverter { jwt ->
                val roles = jwt.getClaimAsStringList("roles").orEmpty().map {
                    SimpleGrantedAuthority("ROLE_" + normalizeRole(it))
                }
                JwtAuthenticationToken(jwt, roles, jwt.subject ?: "usuario")
            }
        } }
        .build()

    @Bean
    @ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "false")
    fun local(http: HttpSecurity): SecurityFilterChain = http.csrf { it.disable() }
        .authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

private fun normalizeRole(role: String): String = when(val value = role.trim().removePrefix("ROLE_").uppercase()) {
    "OPERADOR DEL DOMINIO", "OPERADOR" -> "DESPACHADOR"
    "CLIENTE DEL DOMINIO" -> "CLIENTE"
    else -> value
}
