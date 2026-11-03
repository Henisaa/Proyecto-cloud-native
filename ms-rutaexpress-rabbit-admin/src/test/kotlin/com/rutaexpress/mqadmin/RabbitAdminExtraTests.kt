package com.rutaexpress.mqadmin

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ShutdownSignalException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.AmqpIOException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Binding
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.io.IOException

/** Casos de P1-14 (validaciones y errores del broker), P1-12 (DLQ acumulado), P0-07 y P3-10. */
class RabbitAdminExtraTests {
    private val admin: AmqpAdmin = mock()
    private val template: RabbitTemplate = mock()
    private val cliente: ClienteGestion = mock()
    private val monitor: MqAdminService = mock()
    private val topologia = RabbitTopology(RabbitNombres())
    private val servicio = RabbitAdminService(
        admin, template, cliente, topologia, monitor, "/", 86400000L, 604800000L, 10000,
    )

    @Test
    fun `rechaza una cola con un exchange dead-letter que no existe`() {
        whenever(cliente.existe(RutasGestion.exchange("/", "ex.fantasma"))).thenReturn(false)

        assertThatThrownBy {
            servicio.crearCola(CrearColaRequest(name = "q.prueba", deadLetterExchange = "ex.fantasma"))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ex.fantasma")
            .hasMessageContaining("no existe")
        verify(admin, never()).declareQueue(any())
    }

    @Test
    fun `acepta una cola cuyo exchange dead-letter existe`() {
        whenever(cliente.existe(RutasGestion.exchange("/", "ex.retencion.dlx"))).thenReturn(true)
        whenever(cliente.existe(RutasGestion.cola("/", "q.retencion"))).thenReturn(false)

        servicio.crearCola(
            CrearColaRequest(
                name = "q.retencion", maxLength = 5, ttlMs = 10000, overflow = "reject-publish",
                deadLetterExchange = "ex.retencion.dlx",
            )
        )

        verify(admin).declareQueue(any())
    }

    @Test
    fun `una confirmacion negativa del broker se informa como conflicto y no como caida`() {
        whenever(cliente.existe(any())).thenReturn(true)
        doAnswer { invocacion ->
            (invocacion.arguments[3] as CorrelationData).future.complete(CorrelationData.Confirm(false, null))
            null
        }.whenever(template).send(any(), any(), any(), any())

        assertThatThrownBy {
            servicio.publicarMensaje("ex.retencion", PublicarMensajeRequest(routingKey = "retencion", body = "{}"))
        }
            .isInstanceOf(ConflictoException::class.java)
            .hasMessageContaining("rechazado por la política de la cola")
    }

    @Test
    fun `reconciliar traduce el 406 de una cola base con otros argumentos a conflicto`() {
        val cierre = AMQP.Channel.Close.Builder().replyCode(406).replyText("PRECONDITION_FAILED").classId(50).methodId(10).build()
        val causa = AmqpIOException(IOException(ShutdownSignalException(true, false, cierre, null)))
        doThrow(causa).whenever(admin).declareQueue(any())

        assertThatThrownBy { servicio.reconciliar() }
            .isInstanceOf(ConflictoException::class.java)
            .hasMessageContaining("argumentos distintos")
    }

    @Test
    fun `elimina un exchange propio y exige force para uno protegido`() {
        whenever(cliente.existe(any())).thenReturn(true)

        servicio.eliminarExchange("ex.demo", false)
        verify(admin).deleteExchange("ex.demo")

        assertThatThrownBy { servicio.eliminarExchange("ex.rutaexpress.cmd", false) }
            .isInstanceOf(ConflictoException::class.java)
        verify(admin, times(1)).deleteExchange(any())
    }

    @Test
    fun `crea un enlace con la routing key del topic intacta`() {
        whenever(cliente.existe(any())).thenReturn(true)
        whenever(cliente.lista(any())).thenReturn(emptyList())

        servicio.crearEnlace(CrearBindingRequest(source = "ex.demo", destination = "q.demo", routingKey = "demo.#"))

        val enlace = argumentCaptor<Binding>()
        verify(admin).declareBinding(enlace.capture())
        assertThat(enlace.firstValue.routingKey).isEqualTo("demo.#")
        assertThat(enlace.firstValue.exchange).isEqualTo("ex.demo")
    }

    @Test
    fun `las rutas de gestion codifican el vhost raiz y los comodines`() {
        assertThat(RutasGestion.cola("/", "q.demo")).isEqualTo("/api/queues/%2F/q.demo")
        assertThat(RutasGestion.politicasDelVhost("/")).isEqualTo("/api/policies/%2F")
    }

    @Test
    fun `las politicas vigentes se resumen con nombre patron y definicion`() {
        whenever(cliente.lista(RutasGestion.politicasDelVhost("/"))).thenReturn(
            listOf(
                mapOf(
                    "name" to "rutaexpress.trabajo", "pattern" to "^(q.a)$", "apply-to" to "queues",
                    "priority" to 10, "definition" to mapOf("max-length" to 10000),
                )
            )
        )

        val politicas = servicio.politicas()

        assertThat(politicas).hasSize(1)
        assertThat(politicas[0]["name"]).isEqualTo("rutaexpress.trabajo")
        assertThat(politicas[0]["applyTo"]).isEqualTo("queues")
    }

    @Test
    fun `el resumen del cluster muestra nombre ejecucion memoria y alarmas`() {
        val nodos = mock<ClienteGestion>()
        whenever(nodos.obtener(eq("/api/nodes"), eq(List::class.java))).thenReturn(
            listOf(
                mapOf("name" to "rabbit@rabbit1", "running" to true, "mem_used" to 100, "mem_limit" to 400, "mem_alarm" to false, "disk_free_alarm" to true, "uptime" to 5000, "otro" to "x"),
            )
        )
        val servicioMonitor = MqAdminService(nodos, topologia, SimpleMeterRegistry(), "/", 1)

        val resumen = servicioMonitor.cluster()

        assertThat(resumen).hasSize(1)
        assertThat(resumen[0]["running"]).isEqualTo(true)
        assertThat(resumen[0]["diskFreeAlarm"]).isEqualTo(true)
        assertThat(resumen[0]).doesNotContainKey("otro")
    }

    @Test
    fun `el acumulado de la DLQ sigue subiendo aunque la DLQ este vacia`() {
        val gestion = mock<ClienteGestion>()
        whenever(gestion.lista(RutasGestion.colasDelVhost("/"))).thenReturn(
            listOf(
                mapOf(
                    "name" to "q.rutaexpress.notification.dlq",
                    "messages" to 0,
                    "message_stats" to mapOf("ack" to 7, "ack_details" to mapOf("rate" to 0.5)),
                ),
                mapOf("name" to "q.rutaexpress.command", "messages" to 3, "consumers" to 1),
            )
        )
        val servicioMonitor = MqAdminService(gestion, topologia, SimpleMeterRegistry(), "/", 1)

        val metricas = servicioMonitor.metricas()

        assertThat(metricas["dlqMessages"]).isEqualTo(0L)
        assertThat(metricas["dlqArrivedTotal"]).isEqualTo(7L)
        assertThat(metricas["dlqArrivedRate"]).isEqualTo(0.5)
    }

    @Test
    fun `la topologia inicial se aplica tras reintentar mientras RabbitMQ no responde`() {
        val servicioAdmin = mock<RabbitAdminService>()
        var llamadas = 0
        whenever(servicioAdmin.reconciliar()).thenAnswer {
            llamadas++
            if (llamadas < 3) throw BrokerNoDisponibleException("RabbitMQ aún no responde")
            emptyMap<String, Any>()
        }

        TopologiaInicial(servicioAdmin, true, 5, 0).aplicarConReintentos()

        verify(servicioAdmin, times(3)).reconciliar()
    }

    @Test
    fun `el listado incluye las colas creadas desde la API y deja fuera las internas amq`() {
        val gestion = mock<ClienteGestion>()
        whenever(gestion.lista(RutasGestion.colasDelVhost("/"))).thenReturn(
            listOf(mapOf("name" to "q.v2.prueba"), mapOf("name" to "amq.gen-XYZ"))
        )
        whenever(gestion.objeto(RutasGestion.cola("/", "q.v2.prueba"))).thenReturn(mapOf("messages" to 2, "consumers" to 0))
        val servicioMonitor = MqAdminService(gestion, topologia, SimpleMeterRegistry(), "/", 1)

        val colas = servicioMonitor.colas().associateBy { it["name"] }

        assertThat(colas["q.v2.prueba"]?.get("declared")).isEqualTo(true)
        assertThat(colas["q.v2.prueba"]?.get("messages")).isEqualTo(2L)
        assertThat(colas).doesNotContainKey("amq.gen-XYZ")
    }
}
