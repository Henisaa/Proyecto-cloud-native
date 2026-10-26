package com.rutaexpress.bff.controller

import com.rutaexpress.bff.service.BffGatewayService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Modo sin seguridad (SECURITY_ENABLED=false): no hay Authentication y el detalle del catálogo no debe fallar. */
class CatalogoSinSeguridadTests {
    private val servicio = mock(BffGatewayService::class.java)
    private val controlador = BffController(servicio)

    @Test
    fun `sin autenticacion el detalle del catalogo responde y muestra las capacidades`() {
        val detalle = mapOf("id" to 1, "capacities" to listOf(mapOf("zone" to "URBAN_CENTER")))
        `when`(servicio.fetchCatalogService(1L, "")).thenReturn(detalle)

        val respuesta = controlador.getCatalogService(1L, null, null)

        assertThat(respuesta.statusCode.value()).isEqualTo(200)
        assertThat(respuesta.body).isEqualTo(detalle)
    }
}
