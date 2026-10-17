package com.rutaexpress.shipments.mensajeria

import com.rabbitmq.client.Channel
import com.rutaexpress.shipments.configuracion.RabbitNombres
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.springframework.amqp.core.MessagePostProcessor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.json.JsonMapper

class MensajeriaTests {
    private val RESERVA_CAPACIDAD = """{"eventId":"cmd-9:resultado","payload":{"shipmentId":"s-1","result":"RESERVADA"}}"""
    private val nombres = RabbitNombres()
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `publica el evento de estado y espera la confirmacion del broker`() {
        val plantilla = mock<RabbitTemplate>()
        confirmar(plantilla, ack = true)

        publicador(plantilla).publicarEstado(envio(EstadoEnvio.ACEPTADO), "ana@rutaexpress.local")

        verify(plantilla).convertAndSend(
            ArgumentMatchers.eq("ex.rutaexpress.events"),
            ArgumentMatchers.eq("shipment.status.ACEPTADO"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(MessagePostProcessor::class.java),
            ArgumentMatchers.any(CorrelationData::class.java),
        )
    }

    @Test
    fun `si el broker no confirma, la operacion falla para que la transaccion se revierta`() {
        val plantilla = mock<RabbitTemplate>()
        confirmar(plantilla, ack = false)

        assertThatThrownBy { publicador(plantilla).publicarEstado(envio(EstadoEnvio.CREADO), "LOCAL") }
            .isInstanceOf(MensajeriaNoDisponibleException::class.java)
    }

    @Test
    fun `con RabbitMQ deshabilitado no intenta publicar`() {
        val plantilla = mock<RabbitTemplate>()

        MensajeriaPublicador(plantilla, false, "bodega@rutaexpress.local", nombres)
            .publicarEstado(envio(EstadoEnvio.CREADO), "LOCAL")

        verifyNoInteractions(plantilla)
    }

    @Test
    fun `la solicitud de reserva usa el exchange de comandos y devuelve el commandId`() {
        val plantilla = mock<RabbitTemplate>()
        confirmar(plantilla, ack = true)

        val commandId = publicador(plantilla).solicitarReserva(envio(EstadoEnvio.ACEPTADO))

        assertThat(commandId).isNotBlank()
        verify(plantilla).convertAndSend(
            ArgumentMatchers.eq("ex.rutaexpress.cmd"),
            ArgumentMatchers.eq("capacity.reserve"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(MessagePostProcessor::class.java),
            ArgumentMatchers.any(CorrelationData::class.java),
        )
    }

    @Test
    fun `un evento de auditoria se registra y se confirma con ACK`() {
        val auditoria = mock<AuditoriaEnvioService>()
        val canal = mock<Channel>()

        listener(auditoria, publicador(mock()), intentos = 1)
            .onEvento(mensaje(5, "shipment.status.ACEPTADO", """{"eventId":"evt-1","type":"shipment.status.changed","payload":{"shipmentId":"s-1"}}"""), canal)

        verify(auditoria).registrarEstado(eq(mapper.readTree("""{"eventId":"evt-1","type":"shipment.status.changed","payload":{"shipmentId":"s-1"}}""")), eq("shipment.status.ACEPTADO"))
        verify(canal).basicAck(5, false)
    }

    @Test
    fun `una routing key desconocida va a la DLQ sin reintentar`() {
        val auditoria = mock<AuditoriaEnvioService>()
        val canal = mock<Channel>()

        listener(auditoria, publicador(mock()), intentos = 3)
            .onEvento(mensaje(6, "otra.cosa", """{"eventId":"evt-2"}"""), canal)

        verify(canal).basicNack(6, false, false)
        verifyNoInteractions(auditoria)
    }

    @Test
    fun `una reserva confirmada para un envio cancelado dispara la liberacion compensatoria`() {
        val auditoria = mock<AuditoriaEnvioService>()
        val plantilla = mock<RabbitTemplate>()
        confirmar(plantilla, ack = true)
        val cancelado = envio(EstadoEnvio.CANCELADO).apply { capacityStatus = EstadoCapacidad.RESERVADA }
        whenever(auditoria.aplicarResultadoCapacidad(eq(mapper.readTree(RESERVA_CAPACIDAD)), eq("catalog.capacity.reserved"))).thenReturn(cancelado)
        val canal = mock<Channel>()

        listener(auditoria, publicador(plantilla), intentos = 1)
            .onEvento(mensaje(7, "catalog.capacity.reserved", RESERVA_CAPACIDAD), canal)

        verify(plantilla).convertAndSend(
            ArgumentMatchers.eq("ex.rutaexpress.cmd"),
            ArgumentMatchers.eq("capacity.release"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(MessagePostProcessor::class.java),
            ArgumentMatchers.any(CorrelationData::class.java),
        )
        verify(canal).basicAck(7, false)
    }

    @Test
    fun `un resultado de capacidad sin compensacion solo se confirma`() {
        val auditoria = mock<AuditoriaEnvioService>()
        val plantilla = mock<RabbitTemplate>()
        val canal = mock<Channel>()

        listener(auditoria, publicador(plantilla), intentos = 1)
            .onEvento(mensaje(8, "catalog.capacity.rejected", """{"eventId":"cmd-10:resultado","payload":{"shipmentId":"s-2","result":"RECHAZADA"}}"""), canal)

        verify(canal).basicAck(8, false)
        verify(plantilla, never()).convertAndSend(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any(MessagePostProcessor::class.java), ArgumentMatchers.any(CorrelationData::class.java))
    }

    private fun publicador(plantilla: RabbitTemplate?) =
        MensajeriaPublicador(plantilla, true, "bodega@rutaexpress.local", nombres)

    private fun listener(auditoria: AuditoriaEnvioService, publicador: MensajeriaPublicador, intentos: Int) =
        AuditoriaListener(
            auditoria,
            publicador,
            nombres.copy(consumer = nombres.consumer.copy(maxAttempts = intentos, initialIntervalMs = 0)),
            mapper,
        )

    private fun confirmar(plantilla: RabbitTemplate, ack: Boolean) {
        doAnswer { invocacion ->
            (invocacion.arguments[4] as CorrelationData).future.complete(CorrelationData.Confirm(ack, if (ack) null else "rechazado"))
            null
        }.whenever(plantilla).convertAndSend(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any(MessagePostProcessor::class.java), ArgumentMatchers.any(CorrelationData::class.java))
    }

    private fun envio(estado: EstadoEnvio) = Envio(
        trackingCode = "RX-TEST1234",
        serviceId = 1L,
        zone = "URBAN_CENTER",
        recipientName = "Ana",
        recipientEmail = "ana@example.com",
        originAddress = "Origen 1",
        destinationAddress = "Destino 2",
        packagesCount = 2,
        status = estado,
    )

    private fun mensaje(etiqueta: Long, clave: String, cuerpo: String) = Message(
        cuerpo.toByteArray(),
        MessageProperties().apply {
            deliveryTag = etiqueta
            receivedRoutingKey = clave
            messageId = "msg-$etiqueta"
        },
    )
}
