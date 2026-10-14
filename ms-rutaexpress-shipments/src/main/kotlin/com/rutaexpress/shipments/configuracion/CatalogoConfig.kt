package com.rutaexpress.shipments.configuracion

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class CatalogoConfig(
    @Value("\${app.catalogo.url:http://localhost:8081}")
    private val catalogoUrl: String
) {

    @Bean
    fun catalogoRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(catalogoUrl).build()
}
