package com.rutaexpress.notify.viewmodel

import com.rutaexpress.notify.model.ClaimResult
import com.rutaexpress.notify.model.EventEnvelope
import com.rutaexpress.notify.model.IdempotencyStore
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator

/** Otro intento tiene el evento "processing": es recuperable, el consumidor reintenta con espera. */
class EventoEnProcesoException(eventId: String) :
	RuntimeException("El evento $eventId figura en proceso; se reintentará")

abstract class ValidatingNotificationProcessor(
	private val validator: Validator,
	private val idempotencyStore: IdempotencyStore,
) {
	protected fun <T : Any> processOnce(envelope: EventEnvelope<T>, action: (T) -> Unit) {
		val violations = validator.validate(envelope) + validator.validate(envelope.payload)
		if (violations.isNotEmpty()) {
			throw ConstraintViolationException(violations)
		}
		when (idempotencyStore.claim(envelope.eventId)) {
			ClaimResult.DUPLICATE -> return
			ClaimResult.IN_PROGRESS -> throw EventoEnProcesoException(envelope.eventId)
			ClaimResult.ACQUIRED -> Unit
		}
		try {
			action(envelope.payload)
			idempotencyStore.complete(envelope.eventId)
		} catch (exception: RuntimeException) {
			idempotencyStore.release(envelope.eventId)
			throw exception
		}
	}
}
