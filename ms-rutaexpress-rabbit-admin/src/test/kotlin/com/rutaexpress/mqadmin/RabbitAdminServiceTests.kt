package com.rutaexpress.mqadmin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate

class RabbitAdminServiceTests {
    private val admin: AmqpAdmin = mock()
    private val template: RabbitTemplate = mock()
    private val cliente: ClienteGestion = mock()
    private val monitor: MqAdminService = mock()
    private val topologia = RabbitTopology(RabbitNombres())
    private val servicio = RabbitAdminService(
        admin, template, cliente, topologia, monitor, "/", 86400000L, 604800000L, 10000,
    )

    @Test
    fun `rechaza un nombre de cola vacio sin tocar el broker`() {
        assertThatThrownBy { servicio.crearCola(CrearColaRequest(name = " ")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("nombre de la cola es obligatorio")
        verify(admin, never()).declareQueue(any())
    }

    @Test
    fun `rechaza el prefijo reservado amq y los caracteres no permitidos`() {
        assertThatThrownBy { servicio.crearCola(CrearColaRequest(name = "amq.gen-1")) }
            .hasMessageContaining("amq.")
        assertThatThrownBy { servicio.crearCola(CrearColaRequest(name = "cola con espacios")) }
            .hasMessageContaining("no es válido")
    }

    @Test
    fun `rechaza una retencion fuera de rango con un mensaje claro`() {
        assertThatThrownBy { servicio.crearCola(CrearColaRequest(name = "q.prueba", ttlMs = 10)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ttlMs debe estar entre")
    }

    @Test
    fun `crea la cola con los argumentos de retencion y expiracion`() {
        whenever(cliente.existe(any())).thenReturn(false)

        servicio.crearCola(
            CrearColaRequest(name = "q.prueba", ttlMs = 60000, maxLength = 500, expiresMs = 3600000, overflow = "drop-head")
        )

        val captor = argumentCaptor<Queue>()
        verify(admin).declareQueue(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("q.prueba")
        assertThat(captor.firstValue.arguments)
            .containsEntry("x-message-ttl", 60000L)
            .containsEntry("x-max-length", 500)
            .containsEntry("x-expires", 3600000L)
            .containsEntry("x-overflow", "drop-head")
    }

    @Test
    fun `responde conflicto si la cola ya existe`() {
        whenever(cliente.existe(any())).thenReturn(true)

        assertThatThrownBy { servicio.crearCola(CrearColaRequest(name = "q.prueba")) }
            .isInstanceOf(ConflictoException::class.java)
        verify(admin, never()).declareQueue(any())
    }

    @Test
    fun `no elimina la topologia protegida sin force`() {
        whenever(cliente.existe(any())).thenReturn(true)

        assertThatThrownBy { servicio.eliminarCola("q.rutaexpress.command", false) }
            .isInstanceOf(ConflictoException::class.java)
            .hasMessageContaining("force=true")
        verify(admin, never()).deleteQueue(any())
    }

    @Test
    fun `elimina una cola propia que existe`() {
        whenever(cliente.existe(any())).thenReturn(true)

        servicio.eliminarCola("q.prueba", false)

        verify(admin).deleteQueue("q.prueba")
    }

    @Test
    fun `responde 404 al eliminar una cola inexistente`() {
        whenever(cliente.existe(any())).thenReturn(false)

        assertThatThrownBy { servicio.eliminarCola("q.fantasma", false) }
            .isInstanceOf(RecursoNoEncontradoException::class.java)
    }

    @Test
    fun `valida el tipo de exchange`() {
        assertThatThrownBy { servicio.crearExchange(CrearExchangeRequest(name = "ex.prueba", type = "rare")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no es válido")
    }

    @Test
    fun `no crea enlaces si el exchange de origen no existe`() {
        whenever(cliente.existe(any())).thenReturn(false)

        assertThatThrownBy {
            servicio.crearEnlace(CrearBindingRequest(source = "ex.fantasma", destination = "q.prueba", routingKey = "a.b"))
        }.isInstanceOf(RecursoNoEncontradoException::class.java)
        verify(admin, never()).declareBinding(any())
    }

    @Test
    fun `publica un mensaje confirmado por el broker`() {
        whenever(cliente.existe(any())).thenReturn(true)
        doAnswer { invocacion ->
            (invocacion.arguments[3] as CorrelationData).future.complete(CorrelationData.Confirm(true, null))
            null
        }.whenever(template).send(any(), any(), any(), any())

        val resultado = servicio.publicarMensaje(
            "ex.rutaexpress.cmd",
            PublicarMensajeRequest(routingKey = "capacity.reserve", body = "{}"),
        )

        assertThat(resultado["confirmed"]).isEqualTo(true)
        assertThat(resultado["routed"]).isEqualTo(true)
    }

    @Test
    fun `reconciliar declara la topologia fija y aplica la retencion por defecto`() {
        servicio.reconciliar()

        verify(admin, times(3)).declareExchange(any())
        verify(admin, times(6)).declareQueue(any())
        verify(monitor).aplicarRetencion(RetentionRequest(86400000L, 604800000L, 10000))
    }
}
