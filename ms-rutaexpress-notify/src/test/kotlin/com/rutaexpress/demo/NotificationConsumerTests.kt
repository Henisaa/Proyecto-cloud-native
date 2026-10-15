package com.rutaexpress.demo

import com.rabbitmq.client.Channel
import com.rutaexpress.demo.config.NotificationProperties
import com.rutaexpress.demo.config.RabbitNombres
import com.rutaexpress.demo.viewmodel.EmailNotificationViewModel
import com.rutaexpress.demo.viewmodel.NotificationConsumerViewModel
import com.rutaexpress.demo.viewmodel.WarehouseNotificationViewModel
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.json.JsonMapper

class NotificationConsumerTests {
    private val email: EmailNotificationViewModel = mock()
    private val almacen: WarehouseNotificationViewModel = mock()
    private val canal: Channel = mock()
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `confirma con ACK un correo procesado correctamente`() {
        consumidor(maxAttempts = 3).onNotification(mensaje(42, "notification.email.send", correo()), canal)

        verify(email).notifyRecipient(any())
        verify(canal).basicAck(42, false)
        verify(canal, never()).basicNack(any(), any(), any())
    }

    @Test
    fun `enruta cada routing key al manejador de su tipo`() {
        consumidor(maxAttempts = 1).onNotification(mensaje(7, "notification.warehouse.ticket", ticket()), canal)
        consumidor(maxAttempts = 1).onNotification(mensaje(8, "notification.label.generate", etiqueta()), canal)

        verify(almacen).sendTicket(any())
        verify(almacen).sendLabel(any())
        verify(canal).basicAck(7, false)
        verify(canal).basicAck(8, false)
    }

    @Test
    fun `un JSON invalido se rechaza sin reencolar y sin reintentar`() {
        consumidor(maxAttempts = 3).onNotification(mensaje(43, "notification.email.send", "{no es json".toByteArray()), canal)

        verifyNoInteractions(email)
        verify(canal).basicNack(43, false, false)
        verify(canal, never()).basicAck(any(), any())
    }

    @Test
    fun `una routing key desconocida se rechaza sin reencolar`() {
        consumidor(maxAttempts = 3).onNotification(mensaje(44, "notification.desconocido", correo()), canal)

        verify(canal).basicNack(44, false, false)
        verifyNoInteractions(email, almacen)
    }

    @Test
    fun `datos que no cumplen la validacion no se reintentan`() {
        doThrow(ConstraintViolationException("destinatario vacío", emptySet())).`when`(email).notifyRecipient(any())

        consumidor(maxAttempts = 3).onNotification(mensaje(45, "notification.email.send", correo()), canal)

        verify(email, times(1)).notifyRecipient(any())
        verify(canal).basicNack(45, false, false)
    }

    @Test
    fun `un fallo recuperable se reintenta de forma acotada y luego va a la DLQ`() {
        doThrow(IllegalStateException("SMTP caído")).`when`(email).notifyRecipient(any())

        consumidor(maxAttempts = 3).onNotification(mensaje(46, "notification.email.send", correo()), canal)

        verify(email, times(3)).notifyRecipient(any())
        verify(canal).basicNack(46, false, false)
        verify(canal, never()).basicAck(any(), any())
    }

    private fun consumidor(maxAttempts: Int) = NotificationConsumerViewModel(
        email,
        almacen,
        NotificationProperties().apply {
            from = "no-reply@rutaexpress.local"
            retry.maxAttempts = maxAttempts
            retry.initialInterval = 0
            retry.maxInterval = 0
        },
        RabbitNombres(),
        mapper,
    )

    private fun correo() = sobre(
        "EmailNotification",
        mapOf(
            "shipmentId" to "RX-1",
            "recipientEmail" to "cliente@example.com",
            "recipientName" to "Ana",
            "subject" to "Envío en ruta",
            "message" to "Mensaje",
        ),
    )

    private fun ticket() = sobre(
        "WarehouseTicket",
        mapOf(
            "shipmentId" to "RX-1",
            "warehouseEmail" to "bodega@example.com",
            "recipientName" to "Ana",
            "recipientAddress" to "Calle 1",
            "serviceName" to "EXPRESS",
            "packageDescription" to "1 paquete",
        ),
    )

    private fun etiqueta() = sobre(
        "ShippingLabel",
        mapOf(
            "shipmentId" to "RX-1",
            "warehouseEmail" to "bodega@example.com",
            "recipientName" to "Ana",
            "recipientAddress" to "Calle 1",
            "trackingCode" to "RX-ABC12345",
        ),
    )

    private fun sobre(tipo: String, payload: Map<String, Any>): String = mapper.writeValueAsString(
        mapOf(
            "type" to tipo,
            "eventId" to "evt-$tipo",
            "timestamp" to "2026-09-10T12:00:00Z",
            "traceId" to "trace-1",
            "correlationId" to "correlation-1",
            "payload" to payload,
        )
    )

    private fun mensaje(etiqueta: Long, clave: String, cuerpo: String): Message = mensaje(etiqueta, clave, cuerpo.toByteArray())

    private fun mensaje(etiqueta: Long, clave: String, cuerpo: ByteArray): Message = Message(
        cuerpo,
        MessageProperties().apply {
            deliveryTag = etiqueta
            receivedRoutingKey = clave
            contentType = "application/json"
            messageId = "msg-$etiqueta"
        },
    )
}
