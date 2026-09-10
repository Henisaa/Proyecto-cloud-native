package com.rutaexpress.demo.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ShippingLabel(
	@field:NotBlank val shipmentId: String,
	@field:NotBlank @field:Email val warehouseEmail: String,
	@field:NotBlank val recipientName: String,
	@field:NotBlank val recipientAddress: String,
	@field:NotBlank val trackingCode: String,
)
