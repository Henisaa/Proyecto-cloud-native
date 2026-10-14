package com.rutaexpress.demo.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class EventEnvelope<T : Any>(
	@field:NotBlank val type: String,
	@field:NotBlank val eventId: String,
	@field:NotNull val timestamp: Instant,
	@field:NotBlank val traceId: String,
	@field:NotBlank val correlationId: String,
	@field:NotNull val payload: T,
)
