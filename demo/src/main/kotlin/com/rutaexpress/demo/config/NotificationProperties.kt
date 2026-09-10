package com.rutaexpress.demo.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("rutaexpress.notify")
class NotificationProperties {
	@field:NotBlank
	@field:Email
	var from: String = ""

	@field:Valid
	val retry = Retry()

	var idempotencyTtl: Duration = Duration.ofDays(7)

	@field:Valid
	val firebase = Firebase()

	class Retry {
		@field:Min(1)
		var maxAttempts: Int = 3

		@field:Min(0)
		var initialInterval: Long = 1_000

		var multiplier: Double = 2.0

		@field:Min(0)
		var maxInterval: Long = 10_000

		fun delayBefore(attempt: Int): Long =
			minOf(maxInterval, (initialInterval * Math.pow(multiplier, attempt.toDouble())).toLong())
	}

	class Firebase {
		var credentialsPath: String = ""
	}
}
