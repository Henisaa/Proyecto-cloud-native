package com.rutaexpress.catalog.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @Value("\${server.port:8081}")
    private val serverPort: String
) {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "BearerAuth"

        return OpenAPI()
            .info(
                Info()
                    .title("RutaExpress - Catalog Microservice API")
                    .description("Microservicio de dominio de Catálogo de Servicios, Tarifas y Capacidad de Flota para la plataforma de última milla RutaExpress.")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Equipo de Arquitectura Cloud Native - RutaExpress")
                            .email("devops@rutaexpress.cl")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .servers(
                listOf(
                    Server().url("http://localhost:$serverPort").description("Servidor Local de Desarrollo"),
                    Server().url("https://api.rutaexpress.cl").description("AWS API Gateway / Producción")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components().addSecuritySchemes(
                    securitySchemeName,
                    SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT emitido por Azure AD (IDaaS) con claims 'roles' y audience 'api://<API_CLIENT_ID>'")
                )
            )
    }
}
