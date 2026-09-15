package com.rutaexpress.shipments

import com.rutaexpress.shipments.dominio.EstadoEnvio
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EstadoEnvioTests {

    @Test
    fun `creado solo puede pasar a aceptado o cancelado`() {
        assertTrue(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.ACEPTADO))
        assertTrue(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.CANCELADO))
        assertFalse(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.EN_BODEGA))
        assertFalse(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.EN_RUTA))
    }

    @Test
    fun `no se puede pasar a en ruta sin aceptar`() {
        assertFalse(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.EN_RUTA))
        assertTrue(EstadoEnvio.EN_BODEGA.puedeTransicionarA(EstadoEnvio.EN_RUTA))
    }

    @Test
    fun `flujo completo hasta entregado`() {
        assertTrue(EstadoEnvio.CREADO.puedeTransicionarA(EstadoEnvio.ACEPTADO))
        assertTrue(EstadoEnvio.ACEPTADO.puedeTransicionarA(EstadoEnvio.EN_BODEGA))
        assertTrue(EstadoEnvio.EN_BODEGA.puedeTransicionarA(EstadoEnvio.EN_RUTA))
        assertTrue(EstadoEnvio.EN_RUTA.puedeTransicionarA(EstadoEnvio.ENTREGADO))
        assertTrue(EstadoEnvio.ENTREGADO.esFinal)
    }

    @Test
    fun `entregado y cancelado son finales`() {
        assertFalse(EstadoEnvio.ENTREGADO.puedeTransicionarA(EstadoEnvio.CANCELADO))
        assertFalse(EstadoEnvio.CANCELADO.puedeTransicionarA(EstadoEnvio.ACEPTADO))
    }

    @Test
    fun `desde acepta minusculas y rechaza valores invalidos`() {
        assertTrue(EstadoEnvio.desde("aceptado") == EstadoEnvio.ACEPTADO)
        assertTrue(runCatching { EstadoEnvio.desde("VOLANDO") }.isFailure)
    }
}
