package com.rutaexpress.shipments.dominio

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant

data class CrearEnvioRequest(
    @field:NotNull(message = "serviceId es obligatorio")
    @field:Positive(message = "serviceId debe ser positivo")
    val serviceId: Long? = null,

    @field:NotBlank(message = "zone es obligatoria")
    val zone: String = "",

    @field:Min(value = 1, message = "packagesCount debe ser al menos 1")
    val packagesCount: Int = 1,

    @field:NotNull(message = "weightKg es obligatorio")
    @field:Positive(message = "weightKg debe ser positivo")
    val weightKg: BigDecimal? = null,

    @field:NotNull(message = "volumeM3 es obligatorio")
    @field:Positive(message = "volumeM3 debe ser positivo")
    val volumeM3: BigDecimal? = null,

    @field:NotBlank(message = "recipientName es obligatorio")
    val recipientName: String = "",

    @field:Email(message = "recipientEmail debe ser un correo válido")
    val recipientEmail: String? = null,

    val recipientPhone: String? = null,

    @field:NotBlank(message = "originAddress es obligatoria")
    val originAddress: String = "",

    @field:NotBlank(message = "destinationAddress es obligatoria")
    val destinationAddress: String = "",

    val price: BigDecimal? = null,
)

data class CambiarEstadoRequest(
    @field:NotBlank(message = "status es obligatorio")
    val status: String = "",
)

data class EnvioResponse(
    val id: String,
    val trackingCode: String,
    val serviceId: Long,
    val serviceCode: String?,
    val serviceType: String?,
    val status: String,
    val zone: String?,
    val vehicleType: String?,
    val recipientName: String,
    val recipientEmail: String?,
    val recipientPhone: String?,
    val originAddress: String,
    val destinationAddress: String,
    val weightKg: BigDecimal,
    val volumeM3: BigDecimal,
    val packagesCount: Int,
    val price: BigDecimal?,
    val capacityReserved: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun desde(envio: Envio) = EnvioResponse(
            id = envio.id,
            trackingCode = envio.trackingCode,
            serviceId = envio.serviceId,
            serviceCode = envio.serviceCode,
            serviceType = envio.serviceType,
            status = envio.status.name,
            zone = envio.zone,
            vehicleType = envio.vehicleType,
            recipientName = envio.recipientName,
            recipientEmail = envio.recipientEmail,
            recipientPhone = envio.recipientPhone,
            originAddress = envio.originAddress,
            destinationAddress = envio.destinationAddress,
            weightKg = envio.weightKg,
            volumeM3 = envio.volumeM3,
            packagesCount = envio.packagesCount,
            price = envio.price,
            capacityReserved = envio.capacityReserved,
            createdAt = envio.createdAt,
            updatedAt = envio.updatedAt,
        )
    }
}

data class ErrorResponse(
    val success: Boolean = false,
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null,
    val timestamp: Instant = Instant.now(),
    val validationErrors: Map<String, String>? = null,
)
