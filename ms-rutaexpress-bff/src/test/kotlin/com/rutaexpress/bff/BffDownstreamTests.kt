package com.rutaexpress.bff

import com.rutaexpress.bff.service.DefaultBffGatewayService
import com.rutaexpress.bff.service.DownstreamException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

class BffDownstreamTests {
    private val builder = RestClient.builder().baseUrl("http://catalog")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.build()
    private val gateway = DefaultBffGatewayService(client, client, client, client, client, "", "", "http://catalog", "http://catalog", "", "", "", false, "")

    @Test
    fun `entrega lista del catalogo y conserva Bearer`() {
        server.expect(requestTo("http://catalog/api/catalog/services")).andExpect(header("Authorization", "Bearer token"))
            .andRespond(withSuccess("""{"success":true,"data":[{"id":1}]}""", MediaType.APPLICATION_JSON))
        assertThat(gateway.fetchCatalogServices("token")).isInstanceOf(List::class.java)
        server.verify()
    }

    @Test
    fun `propaga 403 del catalogo sin devolver 200`() {
        server.expect(requestTo("http://catalog/api/catalog/services")).andRespond(withStatus(HttpStatus.FORBIDDEN))
        val exception = assertThrows(DownstreamException::class.java) { gateway.fetchCatalogServices("token") }
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
        server.verify()
    }

    @Test
    fun `conserva el cuerpo del 409 con el mensaje exacto del backend`() {
        server.expect(requestTo("http://catalog/api/shipments/s-1/status"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status":409,"message":"Capacidad rechazada: cancele el envío o reintente la reserva"}""")
            )

        val exception = assertThrows(DownstreamException::class.java) {
            gateway.changeShipmentStatus("s-1", "EN_BODEGA", "token")
        }

        assertThat(exception.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(exception.body).contains("Capacidad rechazada: cancele el envío o reintente la reserva")
    }

    @Test
    fun `la lista de envios codifica los filtros en la consulta`() {
        server.expect { request ->
            assertThat(request.uri.path).isEqualTo("/api/shipments")
            val consulta = requireNotNull(request.uri.rawQuery)
            assertThat(consulta).contains("page=1", "size=20", "%2B").doesNotContain("+")
            assertThat(org.springframework.web.util.UriUtils.decode(consulta, java.nio.charset.StandardCharsets.UTF_8))
                .contains("from=2026-10-20T10:00:00+00:00")
        }
            .andRespond(withSuccess("""{"content":[],"page":1}""", MediaType.APPLICATION_JSON))

        val respuesta = gateway.fetchShipments(
            linkedMapOf("page" to "1", "size" to "20", "from" to "2026-10-20T10:00:00+00:00"), "token"
        )

        assertThat(respuesta).isInstanceOf(Map::class.java)
        server.verify()
    }

    @Test
    fun `dashboard conserva el resto de datos cuando catalogo y auditoria fallan y reportes usa los envios`() {
        server.expect(requestTo("http://catalog/api/shipments/summary"))
            .andRespond(withSuccess("""{"total":3,"byStatus":{"CREADO":3}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://catalog/api/catalog/services")).andRespond(withServerError())
        server.expect(requestTo("http://catalog/api/report/kpis?range=last24h")).andRespond(withServerError())
        // Con reportes caído, sus KPIs de respaldo vuelven a pedir el resumen de envíos.
        server.expect(requestTo("http://catalog/api/shipments/summary"))
            .andRespond(withSuccess("""{"total":3,"byStatus":{"CREADO":3}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://catalog/api/audit?page=0&size=10")).andRespond(withServerError())
        val jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("roles", listOf("Admin"))
            .claim("preferred_username", "admin@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build()

        val dashboard = gateway.buildDashboard(jwt)

        assertThat(dashboard.shipmentsOverview?.available).isTrue()
        assertThat(dashboard.catalogOverview?.available).isFalse()
        assertThat(dashboard.reportOverview?.available).isTrue()
        assertThat(dashboard.auditOverview?.available).isFalse()
        assertThat(dashboard.notices).hasSize(2)
        server.verify()
    }

    @Test
    fun `sin report desplegado los KPIs se calculan con los envios`() {
        val sinReport = DefaultBffGatewayService(client, client, client, client, client, "", "", "http://catalog", "", "", "", "", false, "")
        server.expect(requestTo("http://catalog/api/shipments/summary"))
            .andRespond(withSuccess("""{"total":4,"byStatus":{"CREADO":3,"ENTREGADO":1}}""", MediaType.APPLICATION_JSON))

        val kpis = sinReport.fetchKpis("last24h", "token") as Map<*, *>

        assertThat(kpis["totalShipments"]).isEqualTo(4L)
        assertThat(kpis["shipmentsPerHour"]).isEqualTo(0.17)
        server.verify()
    }

    @Test
    fun `la linea de tiempo une RabbitMQ y Kafka y tolera que audit este apagado`() {
        server.expect(requestTo("http://catalog/api/shipments/s-1/audit")).andRespond(
            withSuccess(
                """[{"eventType":"shipment.status.changed","routingKey":"shipment.status.ACEPTADO","detail":"estado=ACEPTADO actor=ana@rutaexpress.cl","occurredAt":"2026-10-20T10:00:00Z"}]""",
                MediaType.APPLICATION_JSON
            )
        )
        server.expect(requestTo("http://catalog/api/audit/entidad/s-1")).andRespond(withServerError())

        @Suppress("UNCHECKED_CAST")
        val linea = gateway.fetchShipmentTimeline("s-1", "token") as Map<String, Any?>

        assertThat(linea["kafkaAvailable"]).isEqualTo(false)
        val eventos = linea["events"] as List<*>
        assertThat(eventos).hasSize(1)
        assertThat((eventos[0] as Map<*, *>)["actor"]).isEqualTo("ana@rutaexpress.cl")
    }

    @Test
    fun `el estado omite los servicios sin URL y no informa Azure sin seguridad`() {
        val sinDesplegar = DefaultBffGatewayService(client, client, client, client, client, "", "", "", "", "", "", "", false, "")

        val estado = sinDesplegar.getStatus()

        assertThat(estado.azureIdaasConfigured).isFalse()
        assertThat(estado.services).isEmpty()
    }
}
