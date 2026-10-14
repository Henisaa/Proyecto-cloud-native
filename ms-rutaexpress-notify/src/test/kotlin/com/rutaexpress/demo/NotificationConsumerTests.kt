package com.rutaexpress.demo

import com.rabbitmq.client.Channel
import com.rutaexpress.demo.config.NotificationProperties
import com.rutaexpress.demo.model.EmailNotification
import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.viewmodel.EmailNotificationViewModel
import com.rutaexpress.demo.viewmodel.NotificationConsumerViewModel
import com.rutaexpress.demo.viewmodel.WarehouseNotificationViewModel
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import java.time.Instant

class NotificationConsumerTests {
	private val emailViewModel = mock<EmailNotificationViewModel>()
	private val warehouseViewModel = mock<WarehouseNotificationViewModel>()
	private val channel = mock<Channel>()
	private val registry = SimpleMeterRegistry()

	@Test
	fun `confirma con ACK el mensaje procesado`() {
		val consumer = consumer(maxAttempts = 3)

		consumer.consumeEmail(event("event-1"), message(42), channel)

		verify(channel).basicAck(42, false)
		verify(channel, never()).basicNack(any(), any(), any())
		assertThat(dlqCount()).isZero()
	}

	@Test
	fun `reintenta antes de descartar el mensaje`() {
		doThrow(IllegalStateException("SMTP caído")).`when`(emailViewModel).notifyRecipient(any())
		val consumer = consumer(maxAttempts = 3)

		consumer.consumeEmail(event("event-2"), message(43), channel)

		verify(emailViewModel, times(3)).notifyRecipient(any())
		verify(channel).basicNack(43, false, false)
		verify(channel, never()).basicAck(any(), any())
	}

	@Test
	fun `envia a la DLQ y suma la metrica cuando se agotan los intentos`() {
		doThrow(IllegalStateException("SMTP caído")).`when`(emailViewModel).notifyRecipient(any())
		val consumer = consumer(maxAttempts = 1)

		consumer.consumeEmail(event("event-3"), message(44), channel)

		verify(channel).basicNack(44, false, false)
		assertThat(dlqCount()).isEqualTo(1.0)
	}

	private fun consumer(maxAttempts: Int) = NotificationConsumerViewModel(
		emailViewModel,
		warehouseViewModel,
		NotificationProperties().apply {
			from = "no-reply@rutaexpress.local"
			retry.maxAttempts = maxAttempts
			retry.initialInterval = 0
			retry.maxInterval = 0
		},
		registry,
	)

	private fun event(id: String) = EventEnvelope(
		type = "notification.requested",
		eventId = id,
		timestamp = Instant.parse("2026-09-10T12:00:00Z"),
		traceId = "trace-1",
		correlationId = "correlation-1",
		payload = EmailNotification("SHP-1", "cliente@example.com", "Ana", "Envío en ruta", "Mensaje"),
	)

	private fun message(deliveryTag: Long) =
		Message(ByteArray(0), MessageProperties().apply { this.deliveryTag = deliveryTag })

	private fun dlqCount() = registry.find("rutaexpress.notify.dlq.messages").counter()?.count() ?: 0.0
}
