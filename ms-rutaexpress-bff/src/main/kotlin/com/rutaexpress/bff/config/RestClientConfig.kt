package com.rutaexpress.bff.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @Value("\${rutaexpress.services.catalog-url}") private val catalogUrl: String,
    @Value("\${rutaexpress.services.notify-url}") private val notifyUrl: String,
    @Value("\${rutaexpress.services.audit-url}") private val auditUrl: String,
    @Value("\${rutaexpress.services.report-url}") private val reportUrl: String
) {

    @Bean
    fun catalogRestClient(): RestClient = RestClient.builder().baseUrl(catalogUrl).build()

    @Bean
    fun notifyRestClient(): RestClient = RestClient.builder().baseUrl(notifyUrl).build()

    @Bean
    fun auditRestClient(): RestClient = RestClient.builder().baseUrl(auditUrl).build()

    @Bean
    fun reportRestClient(): RestClient = RestClient.builder().baseUrl(reportUrl).build()
}
