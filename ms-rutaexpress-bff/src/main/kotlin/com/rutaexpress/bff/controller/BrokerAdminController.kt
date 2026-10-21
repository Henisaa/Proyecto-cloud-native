package com.rutaexpress.bff.controller

import com.rutaexpress.bff.service.MqAdminCliente
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

/**
 * Administración (solo ADMIN). Responde en /api/bff/admin (y subrutas) y también en /api/admin, la ruta que usa el frontend
 * y que exige la guía en el API Gateway: funciona con o sin reescritura de ruta en el Gateway y en modo local.
 */
@RestController
@RequestMapping("/api/bff/admin", "/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class BrokerAdminController(
    private val mqAdmin: MqAdminCliente,
    @Value("\${rutaexpress.services.kafka-admin-url}") kafkaUrl: String
) {
    private fun client(url: String) = RestClient.builder().baseUrl(url)
        .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()).apply { setReadTimeout(Duration.ofSeconds(15)) }).build()
    private val kafka = client(kafkaUrl)
    @GetMapping("/mq/{resource}")
    fun mq(@PathVariable("resource") resource: String, @AuthenticationPrincipal jwt: Jwt?): ResponseEntity<String> {
        requireResource(resource, setOf("cluster", "queues", "alerts", "metrics", "exchanges", "bindings", "policies"))
        return mqAdmin.enviar(HttpMethod.GET, "/api/mq-admin/$resource", jwt?.tokenValue)
    }

    @PostMapping("/mq/queues")
    fun crearCola(@RequestBody body: Any, @AuthenticationPrincipal jwt: Jwt?) =
        mqAdmin.enviar(HttpMethod.POST, "/api/mq-admin/queues", jwt?.tokenValue, body)

    @DeleteMapping("/mq/queues/{name}")
    fun eliminarCola(
        @PathVariable("name") name: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
        @AuthenticationPrincipal jwt: Jwt?,
    ) = mqAdmin.enviar(
        HttpMethod.DELETE,
        "/api/mq-admin/queues/{name}?force={force}",
        jwt?.tokenValue,
        uriVariables = arrayOf<Any>(name, force),
    )

    @PostMapping("/mq/exchanges")
    fun crearExchange(@RequestBody body: Any, @AuthenticationPrincipal jwt: Jwt?) =
        mqAdmin.enviar(HttpMethod.POST, "/api/mq-admin/exchanges", jwt?.tokenValue, body)

    @DeleteMapping("/mq/exchanges/{name}")
    fun eliminarExchange(
        @PathVariable("name") name: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
        @AuthenticationPrincipal jwt: Jwt?,
    ) = mqAdmin.enviar(
        HttpMethod.DELETE,
        "/api/mq-admin/exchanges/{name}?force={force}",
        jwt?.tokenValue,
        uriVariables = arrayOf<Any>(name, force),
    )

    @PostMapping("/mq/bindings")
    fun crearEnlace(@RequestBody body: Any, @AuthenticationPrincipal jwt: Jwt?) =
        mqAdmin.enviar(HttpMethod.POST, "/api/mq-admin/bindings", jwt?.tokenValue, body)

    @DeleteMapping("/mq/bindings")
    fun eliminarEnlace(
        @RequestParam("source") source: String,
        @RequestParam("destination") destination: String,
        @RequestParam("destinationType", defaultValue = "queue") destinationType: String,
        @RequestParam("routingKey", defaultValue = "") routingKey: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
        @AuthenticationPrincipal jwt: Jwt?,
    ) = mqAdmin.enviar(
        HttpMethod.DELETE,
        "/api/mq-admin/bindings?source={source}&destination={destination}&destinationType={destinationType}&routingKey={routingKey}&force={force}",
        jwt?.tokenValue,
        uriVariables = arrayOf<Any>(source, destination, destinationType, routingKey, force),
    )

    @PostMapping("/mq/topology")
    fun reconciliarTopologia(@AuthenticationPrincipal jwt: Jwt?) =
        mqAdmin.enviar(HttpMethod.POST, "/api/mq-admin/topology", jwt?.tokenValue)

    @PutMapping("/mq/retention")
    fun actualizarRetencion(@RequestBody body: Any, @AuthenticationPrincipal jwt: Jwt?) =
        mqAdmin.enviar(HttpMethod.PUT, "/api/mq-admin/retention", jwt?.tokenValue, body)

    @PostMapping("/mq/exchanges/{name}/messages")
    fun publicarMensaje(
        @PathVariable("name") name: String,
        @RequestBody body: Any,
        @AuthenticationPrincipal jwt: Jwt?,
    ) = mqAdmin.enviar(
        HttpMethod.POST,
        "/api/mq-admin/exchanges/{name}/messages",
        jwt?.tokenValue,
        body,
        arrayOf(name),
    )
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
