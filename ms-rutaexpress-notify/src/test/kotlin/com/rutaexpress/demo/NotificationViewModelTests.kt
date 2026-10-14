package com.rutaexpress.demo

import com.rutaexpress.demo.model.EmailNotification
import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.IdempotencyStore
import com.rutaexpress.demo.model.ShippingLabel
import com.rutaexpress.demo.model.WarehouseTicket
import com.rutaexpress.demo.view.EmailNotificationView
import com.rutaexpress.demo.view.PdfNotificationView
import com.rutaexpress.demo.view.PushNotificationView
import com.rutaexpress.demo.viewmodel.EmailNotificationViewModel
import com.rutaexpress.demo.viewmodel.WarehouseNotificationViewModel
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant

class NotificationViewModelTests {
	private val emailView = mock<EmailNotificationView>()
	private val pushView = mock<PushNotificationView>()
	private val idempotency = InMemoryIdempotencyStore()
	private val validator = Validation.buildDefaultValidatorFactory().validator

	@Test
	fun `entrega correo y push una sola vez por eventId`() {
		val viewModel = EmailNotificationViewModel(emailView, pushView, idempotency, validator)
		val notification = EmailNotification(
			shipmentId = "SHP-1",
			recipientEmail = "cliente@example.com",
			recipientName = "Ana",
			subject = "Envío en ruta",
			message = "Tu envío está en camino",
			deviceToken = "device-token",
		)
		val event = envelope("event-1", notification)

		viewModel.notifyRecipient(event)
		viewModel.notifyRecipient(event)

		verify(emailView).send(notification)
		verify(pushView).send("SHP-1", "device-token", "Envío en ruta", "Tu envío está en camino")
		assertThat(idempotency.completed).containsExactly("event-1")
	}

	@Test
	fun `libera la clave cuando el envío falla para permitir el reintento`() {
		doThrow(IllegalStateException("SMTP caído")).`when`(emailView).send(any())
		val viewModel = EmailNotificationViewModel(emailView, pushView, idempotency, validator)
		val event = envelope(
			"event-2",
			EmailNotification("SHP-2", "cliente@example.com", "Ana", "Estado", "Mensaje"),
		)

		assertThatThrownBy { viewModel.notifyRecipient(event) }.isInstanceOf(IllegalStateException::class.java)

		assertThat(idempotency.claim("event-2")).isTrue()
		assertThat(idempotency.completed).isEmpty()
		verify(pushView, never()).send(any(), any(), any(), any())
	}

	@Test
	fun `rechaza notificacion sin destinatario`() {
		val viewModel = EmailNotificationViewModel(emailView, pushView, idempotency, validator)
		val event = envelope(
			"event-3",
			EmailNotification("SHP-3", null, "Ana", "Estado", "Mensaje", null),
		)

		assertThatThrownBy { viewModel.notifyRecipient(event) }
			.isInstanceOf(IllegalArgumentException::class.java)
			.hasMessageContaining("recipientEmail o deviceToken")
	}

	@Test
	fun `rechaza envelope con payload invalido`() {
		val viewModel = EmailNotificationViewModel(emailView, pushView, idempotency, validator)
		val event = envelope(
			"event-4",
			EmailNotification("", "cliente@example.com", "Ana", "", "Mensaje"),
		)

		assertThatThrownBy { viewModel.notifyRecipient(event) }
			.isInstanceOf(ConstraintViolationException::class.java)

		verify(emailView, never()).send(any())
	}

	@Test
	fun `genera un PDF real para el ticket de bodega`() {
		val bytes = PdfNotificationView().renderTicket(
			WarehouseTicket("SHP-5", "bodega@example.com", "Ana", "Calle 1", "Express", "Caja"),
		)

		assertThat(bytes.size).isGreaterThan(100)
		assertThat(String(bytes, 0, 5)).isEqualTo("%PDF-")
	}

	@Test
	fun `adjunta la etiqueta PDF al correo de bodega`() {
		val pdfView = PdfNotificationView()
		val viewModel = WarehouseNotificationViewModel(pdfView, emailView, idempotency, validator)
		val label = ShippingLabel("SHP-6", "bodega@example.com", "Ana", "Calle 1", "RX-123")

		viewModel.sendLabel(envelope("event-6", label))

		verify(emailView).sendAttachment(
			org.mockito.kotlin.eq("bodega@example.com"),
			org.mockito.kotlin.eq("Etiqueta de despacho - SHP-6"),
			any(),
			org.mockito.kotlin.eq("etiqueta-SHP-6.pdf"),
			org.mockito.kotlin.check { it.size > 100 },
		)
	}

	private fun <T : Any> envelope(id: String, payload: T) = EventEnvelope(
		type = "notification.requested",
		eventId = id,
		timestamp = Instant.parse("2026-09-10T12:00:00Z"),
		traceId = "trace-1",
		correlationId = "correlation-1",
		payload = payload,
	)

	private class InMemoryIdempotencyStore : IdempotencyStore {
		private val events = mutableSetOf<String>()
		val completed = mutableSetOf<String>()

		override fun claim(eventId: String): Boolean = events.add(eventId)

		override fun complete(eventId: String) {
			completed.add(eventId)
		}

		override fun release(eventId: String) {
			events.remove(eventId)
		}
	}
}
