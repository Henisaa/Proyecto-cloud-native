package com.rutaexpress.catalog.config
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name=["app.method-security.enabled"], havingValue="true", matchIfMissing=true)
class MethodSecurityConfig
