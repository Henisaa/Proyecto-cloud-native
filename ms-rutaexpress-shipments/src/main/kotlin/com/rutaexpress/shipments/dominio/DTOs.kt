package com.rutaexpress.shipments.dominio

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

/** Zonas válidas del catálogo. Cualquier otro valor se rechaza con 400 en vez de llegar al catálogo. */
const val ZONAS_VALIDAS = "URBAN_CENTER|URBAN_PERIPHERY|RURAL|INTERURBAN"

/**
 * Los límites replican las columnas de tb_shipments: un valor fuera de rango responde 400 con un mensaje claro
 * y no un 500 de Oracle. Los campos de texto opcionales vacíos se tratan como nulos.
 */
data class CrearEnvioRequest(
    @field:NotNull(message = "serviceId es obligatorio")
    @field:Positive(message = "serviceId debe ser positivo")
    val serviceId: Long? = null,

    @field:NotBlank(message = "zone es obligatoria")
    @field:Pattern(regexp = ZONAS_VALIDAS, message = "zone debe ser una de: URBAN_CENTER, URBAN_PERIPHERY, RURAL, INTERURBAN")
    val zone: String = "",

    @field:Min(value = 1, message = "packagesCount debe ser al menos 1")
    @field:Max(value = 99_999, message = "packagesCount no puede superar 99.999")
    val packagesCount: Int = 1,

    @field:NotNull(message = "weightKg es obligatorio")
    @field:Positive(message = "weightKg debe ser positivo")
    @field:Digits(integer = 8, fraction = 2, message = "weightKg admite hasta 8 enteros y 2 decimales")
    val weightKg: BigDecimal? = null,

    @field:NotNull(message = "volumeM3 es obligatorio")
    @field:Positive(message = "volumeM3 debe ser positivo")
    @field:Digits(integer = 7, fraction = 3, message = "volumeM3 admite hasta 7 enteros y 3 decimales")
    val volumeM3: BigDecimal? = null,

    @field:NotBlank(message = "recipientName es obligatorio")
    @field:Size(max = 120, message = "recipientName admite hasta 120 caracteres")
    val recipientName: String = "",

    @field:Email(message = "recipientEmail debe ser un correo válido")
    @field:Size(max = 120, message = "recipientEmail admite hasta 120 caracteres")
    val recipientEmail: String? = null,

    @field:Size(max = 30, message = "recipientPhone admite hasta 30 caracteres")
    val recipientPhone: String? = null,

    @field:NotBlank(message = "originAddress es obligatoria")
    @field:Size(max = 250, message = "originAddress admite hasta 250 caracteres")
    val originAddress: String = "",

    @field:NotBlank(message = "destinationAddress es obligatoria")
    @field:Size(max = 250, message = "destinationAddress admite hasta 250 caracteres")
    val destinationAddress: String = "",

    @field:DecimalMin(value = "0.00", message = "price no puede ser negativo")
    @field:Digits(integer = 10, fraction = 2, message = "price admite hasta 10 enteros y 2 decimales")
    val price: BigDecimal? = null,
) {
    /** Cadenas vacías -> nulo, para que "" no se confunda con un correo o teléfono informado. */
    fun normalizado() = copy(
        recipientEmail = recipientEmail?.trim()?.ifEmpty { null },
        recipientPhone = recipientPhone?.trim()?.ifEmpty { null },
        recipientName = recipientName.trim(),
        originAddress = originAddress.trim(),
        destinationAddress = destinationAddress.trim(),
    )
}

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
    val customerEmail: String?,
    val capacityReserved: Boolean,
    val capacityStatus: String,
    /** Transiciones que se pueden hacer hoy, ya calculadas con la regla de capacidad. */
    val allowedTransitions: List<String>,
    /** true cuando la reserva fue rechazada y el envío sigue ACEPTADO: se puede pedir de nuevo. */
    val canRetryCapacity: Boolean,
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
            customerEmail = envio.customerEmail,
            capacityReserved = envio.capacityReserved,
            capacityStatus = envio.capacityStatus.name,
            allowedTransitions = envio.status.transicionesPermitidas(envio.capacityStatus).map { it.name },
            canRetryCapacity = envio.status == EstadoEnvio.ACEPTADO && envio.capacityStatus == EstadoCapacidad.RECHAZADA,
            createdAt = envio.createdAt,
            updatedAt = envio.updatedAt,
        )
    }
}

/** Página de resultados: el servidor pagina (máximo 100 por página). */
data class PaginaResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ResumenEnviosResponse(
    val total: Long,
    val byStatus: Map<String, Long>,
    val byCapacity: Map<String, Long>,
    val recent: List<EnvioResponse>,
)

data class RegistroAuditoriaResponse(
    val eventId: String,
    val shipmentId: String,
    val eventType: String,
    val routingKey: String,
    val detail: String?,
    val occurredAt: Instant,
    val receivedAt: Instant,
    val source: String = "RABBITMQ",
) {
    companion object {
        fun desde(registro: RegistroAuditoriaEnvio) = RegistroAuditoriaResponse(
            eventId = registro.eventId,
            shipmentId = registro.shipmentId,
            eventType = registro.tipoEvento,
            routingKey = registro.routingKey,
            detail = registro.detalle,
            occurredAt = registro.ocurridoEn,
            receivedAt = registro.recibidoEn,
        )
    }
}

data class ErrorResponse(
    val success: Boolean = false,
    val status: Int,
    val error: String,
    val message: String,
    /** Mismo texto en el campo estándar de ProblemDetail, para que los clientes lean ambos formatos. */
    val detail: String = message,
    val path: String? = null,
    val timestamp: Instant = Instant.now(),
    val validationErrors: Map<String, String>? = null,
    val correlationId: String? = null,
)
