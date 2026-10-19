package com.rutaexpress.shipments

import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.eventos.EnvioEventPublisher
import com.rutaexpress.shipments.mensajeria.MensajeriaPublicador
import com.rutaexpress.shipments.servicio.EnvioServicio
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class EnvioServicioTests {
    @Test
    fun `publica el comando de reserva despues de estados y notificaciones`() {
        val repositorio = mock<EnvioRepositorio>()
        val catalogo = CatalogoCliente(mock(), true)
        val eventos = mock<EnvioEventPublisher>()
        val mensajeria = mock<MensajeriaPublicador>()
        val envio = Envio(
            id = "shipment-1",
            trackingCode = "RX-TEST1234",
            status = EstadoEnvio.CREADO,
            recipientName = "Ana",
            recipientEmail = "ana@example.com",
        )
        whenever(repositorio.findById("shipment-1")).thenReturn(Optional.of(envio))
        whenever(repositorio.saveAndFlush(any<Envio>())).thenAnswer { it.getArgument<Envio>(0) }

        EnvioServicio(repositorio, catalogo, eventos, mensajeria)
            .cambiarEstado("shipment-1", "ACEPTADO", "token")

        val orden = inOrder(mensajeria, eventos)
        orden.verify(mensajeria).publicarEstado(any(), any())
        orden.verify(eventos).publicarCambioEstado(any(), any())
        orden.verify(mensajeria).enviarEmailEstado(any(), any(), any())
        orden.verify(mensajeria).solicitarReserva(any())
    }
}
