package com.rutaexpress.demo.model

interface IdempotencyStore {
	fun claim(eventId: String): Boolean
	fun complete(eventId: String)
	fun release(eventId: String)
}
