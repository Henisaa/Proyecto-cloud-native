package com.rutaexpress.demo.model

import jakarta.validation.constraints.NotBlank

data class EmailNotification(
	@field:NotBlank val shipmentId: String,
	val recipientEmail: String? = null,
	@field:NotBlank val recipientName: String,
	@field:NotBlank val subject: String,
	@field:NotBlank val message: String,
	val deviceToken: String? = null,
) {
	val hasEmail: Boolean get() = !recipientEmail.isNullOrBlank()
	val hasDeviceToken: Boolean get() = !deviceToken.isNullOrBlank()
}
