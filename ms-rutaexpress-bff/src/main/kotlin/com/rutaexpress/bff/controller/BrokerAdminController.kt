package com.rutaexpress.bff.controller
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.net.http.HttpClient
import java.time.Duration

@RestController
@RequestMapping("/api/bff/admin")
@PreAuthorize("hasRole('ADMIN')")
class BrokerAdminController(
    @Value("\${rutaexpress.services.mq-admin-url}") mqUrl: String,
    @Value("\${rutaexpress.services.kafka-admin-url}") kafkaUrl: String
) {
    private fun client(url: String) = RestClient.builder().baseUrl(url)
        .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()).apply { setReadTimeout(Duration.ofSeconds(15)) }).build()
    private val mq = client(mqUrl)
    private val kafka = client(kafkaUrl)
    @GetMapping("/mq/{resource}")
    fun mq(@PathVariable resource: String, @AuthenticationPrincipal jwt: Jwt?): Any {
        requireResource(resource, setOf("cluster", "queues", "alerts"))
        return get(mq, "/api/mq-admin/$resource", jwt)
    }
    @GetMapping("/kafka/{resource}")
    fun kafka(@PathVariable resource: String, @AuthenticationPrincipal jwt: Jwt?): Any {
        requireResource(resource, setOf("cluster", "topics"))
        return get(kafka, "/api/kafka-admin/$resource", jwt)
    }
    @GetMapping("/kafka/topics/{name}")
    fun topic(@PathVariable name: String, @AuthenticationPrincipal jwt: Jwt?) = get(kafka, "/api/kafka-admin/topics/{name}", jwt, name)
    @GetMapping("/kafka/groups/{group}/lag")
    fun lag(@PathVariable group: String, @AuthenticationPrincipal jwt: Jwt?) = get(kafka, "/api/kafka-admin/groups/{group}/lag", jwt, group)
    private fun requireResource(value: String, allowed: Set<String>) {
        if(value !in allowed) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso desconocido")
    }
    private fun get(client: RestClient, path: String, jwt: Jwt?, vararg params: Any): Any = try {
        client.get().uri(path, *params).headers { if(jwt != null) it.setBearerAuth(jwt.tokenValue) }
            .retrieve().body(Any::class.java) ?: emptyList<Any>()
    } catch(ex: RestClientResponseException) {
        throw ResponseStatusException(ex.statusCode, "El administrador no pudo completar la consulta", ex)
    } catch(ex: org.springframework.web.client.RestClientException) {
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Administrador no disponible", ex)
    }
}
