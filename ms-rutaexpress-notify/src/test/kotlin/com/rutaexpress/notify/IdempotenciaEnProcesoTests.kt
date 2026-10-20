package com.rutaexpress.notify

import com.rutaexpress.notify.config.NotificationProperties
import com.rutaexpress.notify.model.ClaimResult
import com.rutaexpress.notify.model.EmailNotification
import com.rutaexpress.notify.model.EventEnvelope
import com.rutaexpress.notify.model.IdempotencyStore
import com.rutaexpress.notify.model.RedisIdempotencyStore
import com.rutaexpress.notify.view.EmailNotificationView
import com.rutaexpress.notify.view.PushNotificationView
import com.rutaexpress.notify.viewmodel.EmailNotificationViewModel
import com.rutaexpress.notify.viewmodel.EventoEnProcesoException
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant

/** P1-11: una marca "processing" previa no se toma como duplicado, así la notificación no se pierde. */
class IdempotenciaEnProcesoTests {
	private val redis = mock<StringRedisTemplate>()
	private val operaciones = mock<ValueOperations<String, String>>()
	private val tienda = RedisIdempotencyStore(redis, NotificationProperties())

	init {
		whenever(redis.opsForValue()).thenReturn(operaciones)
	}

	@Test
	fun `una clave libre se toma para procesar`() {
		whenever(operaciones.setIfAbsent(any(), any(), any<Duration>())).thenReturn(true)

		assertThat(tienda.claim("evt-1")).isEqualTo(ClaimResult.ACQUIRED)
	}

	@Test
	fun `una clave completada es un duplicado real`() {
		whenever(operaciones.setIfAbsent(any(), any(), any<Duration>())).thenReturn(false)
		whenever(operaciones.get(any<String>())).thenReturn("completed")

		assertThat(tienda.claim("evt-2")).isEqualTo(ClaimResult.DUPLICATE)
	}

	@Test
	fun `una clave en proceso no es un duplicado`() {
		whenever(operaciones.setIfAbsent(any(), any(), any<Duration>())).thenReturn(false)
		whenever(operaciones.get(any<String>())).thenReturn("processing")

		assertThat(tienda.claim("evt-3")).isEqualTo(ClaimResult.IN_PROGRESS)
	}

	@Test
	fun `con el evento en proceso el procesador falla para reintentar y no envia nada`() {
		val emailView = mock<EmailNotificationView>()
		val pushView = mock<PushNotificationView>()
		val tiendaEnProceso = object : IdempotencyStore {
			override fun claim(eventId: String) = ClaimResult.IN_PROGRESS
			override fun complete(eventId: String) = Unit
			override fun release(eventId: String) = Unit
		}
		val viewModel = EmailNotificationViewModel(
			emailView, pushView, tiendaEnProceso, Validation.buildDefaultValidatorFactory().validator,
		)
		val notificacion = EmailNotification("SHP-9", "cliente@example.com", "Ana", "Asunto", "Mensaje")
		val evento = EventEnvelope(
			type = "notification.requested",
			eventId = "evt-9",
			timestamp = Instant.parse("2026-10-20T10:00:00Z"),
			traceId = "t",
			correlationId = "c",
			payload = notificacion,
		)

		assertThatThrownBy { viewModel.notifyRecipient(evento) }.isInstanceOf(EventoEnProcesoException::class.java)

		verify(emailView, never()).send(any())
	}
}
