package com.rutaexpress.bff.controller

import com.rutaexpress.bff.service.MqAdminCliente
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
    private val kafka: RestClient? = kafkaUrl.takeIf { it.isNotBlank() }?.let { client(it) }
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
        requireResource(resource, setOf("cluster", "topics", "messages", "health"))
        return if (resource == "messages") {
            get(kafkaDisponible(), "/api/kafka/messages", jwt)
        } else if (resource == "health") {
            try {
                kafkaDisponible().get().uri("/api/kafka/health").retrieve().body(String::class.java) ?: ""
            } catch (ex: Exception) {
                mapOf("status" to "DOWN", "error" to (ex.message ?: "Unavailable"))
            }
        } else {
            get(kafkaDisponible(), "/api/kafka-admin/$resource", jwt)
        }
    }
    @GetMapping("/kafka/topics/{name}")
    fun topic(@PathVariable name: String, @AuthenticationPrincipal jwt: Jwt?) = get(kafkaDisponible(), "/api/kafka-admin/topics/{name}", jwt, name)
    @GetMapping("/kafka/groups/{group}/lag")
    fun lag(@PathVariable group: String, @AuthenticationPrincipal jwt: Jwt?) = get(kafkaDisponible(), "/api/kafka-admin/groups/{group}/lag", jwt, group)
    @GetMapping("/kafka/streams/state")
    fun streamsState(@AuthenticationPrincipal jwt: Jwt?) = get(kafkaDisponible(), "/api/kafka/streams/state", jwt)
    @PostMapping("/kafka/streams/simulate")
    fun simulateStreamsPost(
        @RequestParam(value = "scenario", required = false) scenario: String?,
        @RequestBody(required = false) body: Map<String, Any?>?,
        @AuthenticationPrincipal jwt: Jwt?
    ): Any {
        return try {
            val req = kafkaDisponible().post()
                .uri { uri ->
                    val b = uri.path("/api/kafka/streams/simulate")
                    if (!scenario.isNullOrBlank()) b.queryParam("scenario", scenario)
                    b.build()
                }
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON).body(body)
            }
            req.retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo simular evento en Kafka Streams: ${e.message}", e)
        }
    }
    @GetMapping("/kafka/streams/simulate")
    fun simulateStreamsGet(
        @RequestParam(value = "scenario", defaultValue = "rafaga") scenario: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): Any {
        return try {
            kafkaDisponible().get()
                .uri { uri -> uri.path("/api/kafka/streams/simulate").queryParam("scenario", scenario).build() }
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
                .retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo simular escenario en Kafka Streams: ${e.message}", e)
        }
    }
    @PostMapping("/kafka/send")
    fun sendKafkaPost(
        @RequestParam("message") message: String,
        @RequestParam(value = "key", required = false) key: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): Map<String, Any> {
        val respuesta = try {
            kafkaDisponible().post()
                .uri { uri ->
                    val b = uri.path("/api/kafka/send").queryParam("message", message)
                    if (!key.isNullOrBlank()) b.queryParam("key", key)
                    b.build()
                }
                .retrieve().body(String::class.java) ?: ""
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar mensaje a Kafka: ${e.message}", e)
        }
        return mapOf("success" to true, "message" to respuesta)
    }
    @GetMapping("/kafka/send")
    fun sendKafkaGet(
        @RequestParam("message") message: String,
        @RequestParam(value = "key", required = false) key: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): Map<String, Any> {
        val respuesta = try {
            kafkaDisponible().get()
                .uri { uri ->
                    val b = uri.path("/api/kafka/send").queryParam("message", message)
                    if (!key.isNullOrBlank()) b.queryParam("key", key)
                    b.build()
                }
                .retrieve().body(String::class.java) ?: ""
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar mensaje a Kafka: ${e.message}", e)
        }
        return mapOf("success" to true, "message" to respuesta)
    }
    @PostMapping("/kafka/topology")
    fun reconciliarKafkaTopology(@AuthenticationPrincipal jwt: Jwt?): Any {
        return try {
            kafkaDisponible().post().uri("/api/kafka-admin/topology")
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
                .retrieve().body(Any::class.java) ?: emptyList<Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo reconciliar topología de Kafka: ${e.message}", e)
        }
    }

    @GetMapping("/kafka/tuning/status")
    fun tuningStatus(@AuthenticationPrincipal jwt: Jwt?): Any = get(kafkaDisponible(), "/api/test/status", jwt)

    @PostMapping("/kafka/tuning/load-test")
    fun ejecutarPruebaCarga(
        @RequestParam(value = "count", defaultValue = "10000") count: Int,
        @AuthenticationPrincipal jwt: Jwt?
    ): Any {
        return try {
            kafkaDisponible().post()
                .uri { uri -> uri.path("/api/test/start").queryParam("count", count).build() }
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
                .retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo ejecutar prueba de carga Kafka: ${e.message}", e)
        }
    }

    @PostMapping("/kafka/tuning/init")
    fun initTuningTopics(@AuthenticationPrincipal jwt: Jwt?): Any {
        return try {
            kafkaDisponible().post().uri("/api/kafka/tuning/init")
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
                .retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron inicializar tópicos de tuning/errores: ${e.message}", e)
        }
    }

    @GetMapping("/kafka/errors/state")
    fun errorHandlingState(@AuthenticationPrincipal jwt: Jwt?): Any = get(kafkaDisponible(), "/api/kafka/errors/state", jwt)

    @PostMapping("/kafka/errors/simulate")
    fun simulateErrorScenario(
        @RequestParam(value = "scenario", required = false) scenario: String?,
        @RequestBody(required = false) body: Map<String, Any?>?,
        @AuthenticationPrincipal jwt: Jwt?
    ): Any {
        return try {
            val req = kafkaDisponible().post()
                .uri { uri ->
                    val b = uri.path("/api/kafka/errors/simulate")
                    if (!scenario.isNullOrBlank()) b.queryParam("scenario", scenario)
                    b.build()
                }
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON).body(body)
            }
            req.retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo simular escenario de error en Kafka: ${e.message}", e)
        }
    }

    @GetMapping("/kafka/dlt")
    fun listDlt(@AuthenticationPrincipal jwt: Jwt?): Any = get(kafkaDisponible(), "/api/kafka/dlt", jwt)

    @PostMapping("/kafka/dlt/reprocess")
    fun reprocessDlt(
        @RequestParam(value = "dltId", required = false) dltId: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): Any {
        return try {
            kafkaDisponible().post()
                .uri { uri ->
                    val b = uri.path("/api/kafka/dlt/reprocess")
                    if (!dltId.isNullOrBlank()) b.queryParam("dltId", dltId)
                    b.build()
                }
                .headers { if (jwt != null) it.setBearerAuth(jwt.tokenValue) }
                .retrieve().body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo reprocesar DLT de Kafka: ${e.message}", e)
        }
    }
    private fun kafkaDisponible(): RestClient = kafka
        ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Administrador de Kafka no desplegado")

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
