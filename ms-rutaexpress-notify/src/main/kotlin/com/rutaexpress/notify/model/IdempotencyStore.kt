package com.rutaexpress.notify.model

/** Resultado de intentar tomar un eventId para procesarlo. */
enum class ClaimResult {
	/** Primera vez: este consumidor debe procesar el evento. */
	ACQUIRED,

	/** Ya se procesó por completo: es un duplicado real y se confirma con ACK sin enviar nada. */
	DUPLICATE,

	/** Otro intento lo marcó "processing" y no terminó (por ejemplo, el consumidor murió): se reintenta con espera. */
	IN_PROGRESS,
}

interface IdempotencyStore {
	fun claim(eventId: String): ClaimResult
	fun complete(eventId: String)
	fun release(eventId: String)
}
