package com.rutaexpress.shipments.configuracion

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class CatalogoConfig(
    @Value("\${app.catalogo.url:http://localhost:8081}")
    private val catalogoUrl: String,
    @Value("\${app.catalogo.connect-timeout-ms:2000}")
    private val conexionMs: Long,
    @Value("\${app.catalogo.read-timeout-ms:5000}")
    private val lecturaMs: Long,
) {

    /** Con timeouts: un catálogo lento nunca retiene la petición más allá de los 30 s del API Gateway. */
    @Bean
    fun catalogoRestClient(builder: RestClient.Builder): RestClient {
        val fabrica = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(conexionMs))
            setReadTimeout(Duration.ofMillis(lecturaMs))
        }
        return builder.baseUrl(catalogoUrl).requestFactory(fabrica).build()
    }
}
