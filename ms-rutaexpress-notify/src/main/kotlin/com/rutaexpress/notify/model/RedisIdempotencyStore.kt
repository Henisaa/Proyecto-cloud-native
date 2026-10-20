package com.rutaexpress.notify.model

import com.rutaexpress.notify.config.NotificationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisIdempotencyStore(
	private val redis: StringRedisTemplate,
	private val properties: NotificationProperties,
) : IdempotencyStore {
	override fun claim(eventId: String): ClaimResult {
		if (tomar(eventId)) return ClaimResult.ACQUIRED
		return when (redis.opsForValue().get(key(eventId))) {
			COMPLETED -> ClaimResult.DUPLICATE
			// La marca venció entre las dos lecturas: se intenta tomar de nuevo.
			null -> if (tomar(eventId)) ClaimResult.ACQUIRED else ClaimResult.IN_PROGRESS
			else -> ClaimResult.IN_PROGRESS
		}
	}

	private fun tomar(eventId: String): Boolean =
		redis.opsForValue().setIfAbsent(key(eventId), PROCESSING, PROCESSING_TTL) == true

	override fun complete(eventId: String) {
		redis.opsForValue().set(key(eventId), COMPLETED, properties.idempotencyTtl)
	}

	override fun release(eventId: String) {
		redis.delete(key(eventId))
	}

	private fun key(eventId: String) = PREFIX + eventId

	private companion object {
		const val PREFIX = "rutaexpress:notify:event:"
		const val PROCESSING = "processing"
		const val COMPLETED = "completed"

		/** Si el consumidor muere a mitad de proceso, la marca vence y la reentrega puede procesarse. */
		val PROCESSING_TTL: Duration = Duration.ofSeconds(60)
	}
}
