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
}
