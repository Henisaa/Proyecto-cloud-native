package com.rutaexpress.demo.viewmodel

import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.IdempotencyStore
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator

abstract class ValidatingNotificationProcessor(
	private val validator: Validator,
	private val idempotencyStore: IdempotencyStore,
) {
	protected fun <T : Any> processOnce(envelope: EventEnvelope<T>, action: (T) -> Unit) {
		val violations = validator.validate(envelope) + validator.validate(envelope.payload)
		if (violations.isNotEmpty()) {
			throw ConstraintViolationException(violations)
		}
		if (!idempotencyStore.claim(envelope.eventId)) return
		try {
			action(envelope.payload)
			idempotencyStore.complete(envelope.eventId)
		} catch (exception: RuntimeException) {
			idempotencyStore.release(envelope.eventId)
			throw exception
		}
	}
}
