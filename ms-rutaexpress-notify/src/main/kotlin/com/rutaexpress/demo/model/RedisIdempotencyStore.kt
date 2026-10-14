package com.rutaexpress.demo.model

import com.rutaexpress.demo.config.NotificationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisIdempotencyStore(
	private val redis: StringRedisTemplate,
	private val properties: NotificationProperties,
) : IdempotencyStore {
	override fun claim(eventId: String): Boolean =
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
		val PROCESSING_TTL: Duration = Duration.ofMinutes(5)
	}
}
