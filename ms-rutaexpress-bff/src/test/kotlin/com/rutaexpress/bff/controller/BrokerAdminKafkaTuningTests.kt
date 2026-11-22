package com.rutaexpress.bff.controller

import com.rutaexpress.bff.service.MqAdminCliente
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant

class BrokerAdminKafkaTuningTests {

    private fun sampleJwt(): Jwt = Jwt.withTokenValue("test-jwt-token")
        .header("alg", "none")
        .claim("roles", listOf("Admin"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build()

    @Test
    fun `BrokerAdminController reenvia endpoints de tuning y sustained hacia kafka-admin`() {
        val seenPaths = mutableListOf<String>()
        val seenAuths = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val query = ex.requestURI.rawQuery
            seenPaths.add(ex.requestURI.path + (if (query != null) "?$query" else ""))
            seenAuths.add(ex.requestHeaders.getFirst("Authorization"))
            val path = ex.requestURI.path
            val (code, contentType, body) = when {
                path == "/api/test/health" -> Triple(200, "text/plain", "✓ Load Test Controller está corriendo en puerto 8080")
                path == "/api/test/start" && query?.contains("topic=invalido") == true ->
                    Triple(400, "application/json", """{"error":"Tópico inválido"}""")
                path == "/api/kafka-admin/topics" && ex.requestMethod == "POST" ->
                    Triple(201, "application/json", """{"name":"rutaexpress.demo.ep5"}""")
                path == "/api/kafka-admin/topics/shipments.events" && ex.requestMethod == "DELETE" && (query == null || !query.contains("force=true")) ->
                    Triple(409, "application/problem+json", """{"status":409,"detail":"Tópico protegido"}""")
                else -> Triple(200, "application/json", """{"topic":"sensor-topic","lastCount":100}""")
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", contentType)
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val port = server.address.port
            val controller = BrokerAdminController(mock(MqAdminCliente::class.java), "http://127.0.0.1:$port")
            val jwt = sampleJwt()

            @Suppress("UNCHECKED_CAST")
            val health = controller.tuningHealth(jwt) as Map<String, Any>
            assertThat(health["status"]).isEqualTo("UP")
            assertThat(health["message"].toString()).contains("Load Test Controller")

            val status = controller.tuningStatus(jwt)
            assertThat(status).isInstanceOf(Map::class.java)

            val load = controller.ejecutarPruebaCarga(count = 250, topic = "sensor-topic", jwt = jwt)
            assertThat(load).isInstanceOf(Map::class.java)

            val sustained = controller.ejecutarCargaSostenida(messagesPerSecond = 100, seconds = 5, topic = "tutorial-particiones", jwt = jwt)
            assertThat(sustained).isInstanceOf(Map::class.java)

            val init = controller.initTuningTopics(jwt)
            assertThat(init).isInstanceOf(Map::class.java)

            val created = controller.crearTopicoKafka(mapOf("name" to "rutaexpress.demo.ep5"), jwt)
            assertThat(created.statusCode).isEqualTo(HttpStatus.CREATED)

            val conflict = controller.eliminarTopicoKafka("shipments.events", false, jwt)
            assertThat(conflict.statusCode).isEqualTo(HttpStatus.CONFLICT)

            val deleted = controller.eliminarTopicoKafka("rutaexpress.demo.ep5", false, jwt)
            assertThat(deleted.statusCode).isEqualTo(HttpStatus.OK)

            assertThat(controller.actualizarParticionesKafka("rutaexpress.demo.ep5", mapOf("partitions" to 6), jwt).statusCode).isEqualTo(HttpStatus.OK)
            assertThat(controller.actualizarRetencionKafka("rutaexpress.demo.ep5", mapOf("retentionMs" to 300000), jwt).statusCode).isEqualTo(HttpStatus.OK)
            assertThat(controller.actualizarConfigsKafka("rutaexpress.demo.ep5", mapOf("configs" to mapOf("retention.ms" to "300000")), jwt).statusCode).isEqualTo(HttpStatus.OK)
            assertThat(controller.eliminarConfigKafka("rutaexpress.demo.ep5", "retention.ms", jwt).statusCode).isEqualTo(HttpStatus.OK)
            assertThat(controller.gruposKafka(jwt).statusCode).isEqualTo(HttpStatus.OK)
            assertThat(controller.grupoKafka("ms-rutaexpress-audit-group", jwt).statusCode).isEqualTo(HttpStatus.OK)

            assertThatThrownBy {
                controller.ejecutarPruebaCarga(count = 10, topic = "invalido", jwt = jwt)
            }.isInstanceOf(ResponseStatusException::class.java)
                .satisfies({ ex ->
                    assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                })

            assertThat(seenPaths).contains(
                "/api/test/health",
                "/api/test/status",
                "/api/test/start?count=250&topic=sensor-topic",
                "/api/test/sustained?messagesPerSecond=100&seconds=5&topic=tutorial-particiones",
                "/api/kafka/tuning/init"
            )
            assertThat(seenAuths).allMatch { it == "Bearer test-jwt-token" }
        } finally {
            server.stop(0)
        }

        // Con servidor detenido, responde 503 SERVICE_UNAVAILABLE
        val downController = BrokerAdminController(mock(MqAdminCliente::class.java), "http://127.0.0.1:${server.address.port}")
        assertThatThrownBy { downController.tuningHealth(null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            })
        assertThatThrownBy { downController.ejecutarPruebaCarga(count = 10, topic = null, jwt = null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            })
    }
}
