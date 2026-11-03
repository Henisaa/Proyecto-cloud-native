package com.rutaexpress.bff.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Clientes hacia los microservicios internos, siempre con timeouts: conexión de 2 s y lectura de 8 s por defecto,
 * por debajo de los 30 s que el API Gateway espera a la integración.
 */
@Configuration
class RestClientConfig(
    @Value("\${rutaexpress.services.catalog-url}") private val catalogUrl: String,
    @Value("\${rutaexpress.services.notify-url}") private val notifyUrl: String,
    @Value("\${rutaexpress.services.audit-url}") private val auditUrl: String,
    @Value("\${rutaexpress.services.report-url}") private val reportUrl: String,
    @Value("\${rutaexpress.services.shipments-url:http://localhost:8085}") private val shipmentsUrl: String,
    @Value("\${rutaexpress.http.connect-timeout-ms:2000}") private val conexionMs: Long,
    @Value("\${rutaexpress.http.read-timeout-ms:8000}") private val lecturaMs: Long,
) {

    private fun cliente(url: String): RestClient = RestClient.builder()
        .baseUrl(url)
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(conexionMs)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(lecturaMs)) },
        )
        .build()

    @Bean
    fun catalogRestClient(): RestClient = cliente(catalogUrl)

    @Bean
    fun notifyRestClient(): RestClient = cliente(notifyUrl)

    @Bean
    fun auditRestClient(): RestClient = cliente(auditUrl)

    @Bean
    fun reportRestClient(): RestClient = cliente(reportUrl)

    @Bean
    fun shipmentsRestClient(): RestClient = cliente(shipmentsUrl)

    /** Sin URL base: se usa para consultar el estado de salud de cualquier servicio con un timeout corto. */
    @Bean
    fun healthRestClient(): RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(2500)) },
        )
        .build()
}
