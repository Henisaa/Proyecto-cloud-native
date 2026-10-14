package com.rutaexpress.demo.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class WarehouseTicket(
	@field:NotBlank val shipmentId: String,
	@field:NotBlank @field:Email val warehouseEmail: String,
	@field:NotBlank val recipientName: String,
	@field:NotBlank val recipientAddress: String,
	@field:NotBlank val serviceName: String,
	@field:NotBlank val packageDescription: String,
)
