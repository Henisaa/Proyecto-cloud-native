package com.rutaexpress.bff
import com.rutaexpress.bff.service.DefaultBffGatewayService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class BffDownstreamTests {
    private val builder = RestClient.builder().baseUrl("http://catalog")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.build()
    private val gateway = DefaultBffGatewayService(client,client,client,client,"","","","","","","")
    @Test fun `entrega lista del catalogo y conserva Bearer`() {
        server.expect(requestTo("http://catalog/api/catalog/services")).andExpect(header("Authorization","Bearer token"))
            .andRespond(withSuccess("""{"success":true,"data":[{"id":1}]}""",MediaType.APPLICATION_JSON))
        assertThat(gateway.fetchCatalogServices("token")).isInstanceOf(List::class.java)
        server.verify()
    }
    @Test fun `propaga 403 del catalogo sin devolver 200`() {
        server.expect(requestTo("http://catalog/api/catalog/services")).andRespond(withStatus(HttpStatus.FORBIDDEN))
        val exception = assertThrows(ResponseStatusException::class.java) { gateway.fetchCatalogServices("token") }
        assertThat(exception.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        server.verify()
    }

    @Test fun `dashboard conserva el resto de datos cuando catálogo y reportes fallan`() {
        server.expect(requestTo("http://catalog/api/catalog/services")).andRespond(withServerError())
        server.expect(requestTo("http://catalog/api/report/kpis?range=last24h")).andRespond(withServerError())
        server.expect(requestTo("http://catalog/api/audit")).andRespond(withServerError())
        val jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("roles", listOf("Admin"))
            .claim("preferred_username", "admin@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build()

        val dashboard = gateway.buildDashboard(jwt)

        assertThat(dashboard.catalogOverview).isNull()
        assertThat(dashboard.reportOverview).isNull()
        assertThat(dashboard.notices).containsExactly(
            "No se pudo consultar el catálogo; inténtelo nuevamente más tarde.",
            "No se pudieron consultar los indicadores de reportes.",
        )
        server.verify()
    }
}
