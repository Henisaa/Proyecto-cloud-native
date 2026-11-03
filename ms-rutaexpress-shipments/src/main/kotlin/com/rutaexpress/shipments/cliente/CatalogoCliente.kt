package com.rutaexpress.shipments.cliente

import tools.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal

class ServicioCatalogoNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

class CapacidadNoDisponibleException(mensaje: String) : RuntimeException(mensaje)

class CatalogoNoDisponibleException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)

data class ResultadoCapacidad(
    val serviceId: Long,
    val serviceCode: String?,
    val available: Boolean,
    val vehicleTypeSelected: String?,
    val remainingSlots: Int,
    val message: String,
)

/**
 * Consulta síncrona de disponibilidad al microservicio de catálogo (validación al crear el envío).
 * La reserva y la liberación de cupo no se hacen aquí: viajan como comandos por RabbitMQ.
 */
@Component
class CatalogoCliente(
    private val catalogoRestClient: RestClient,
    @Value("\${app.catalogo.enabled:true}")
    private val catalogoEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(CatalogoCliente::class.java)

    val habilitado: Boolean
        get() = catalogoEnabled

    fun verificarCapacidad(
        serviceId: Long,
        zone: String,
        weightKg: BigDecimal,
        volumeM3: BigDecimal,
        packagesCount: Int,
        token: String?
    ): ResultadoCapacidad {
        val data = llamar(
            serviceId,
            "capacity/check",
            mapOf(
                "zone" to zone,
                "weightKg" to weightKg,
                "volumeM3" to volumeM3,
                "packagesCount" to packagesCount
            ),
            token
        ) ?: throw ServicioCatalogoNoEncontradoException("El servicio $serviceId no existe en el catálogo")

        return ResultadoCapacidad(
            serviceId = data.path("serviceId").asLong(serviceId),
            serviceCode = data.path("serviceCode").asText(null),
            available = data.path("available").asBoolean(false),
            vehicleTypeSelected = data.path("vehicleTypeSelected").asText(null),
            remainingSlots = data.path("remainingSlots").asInt(0),
            message = data.path("message").asText("")
        )
    }

    /** Mensaje que devolvió catalog en su cuerpo de error; si no hay, el texto de HTTP. */
    private fun mensajeDe(ex: RestClientResponseException): String =
        runCatching {
            tools.jackson.databind.json.JsonMapper().readTree(ex.responseBodyAsString).path("message").asText("")
        }.getOrDefault("").ifBlank { "solicitud inválida" }

    private fun llamar(serviceId: Long, operacion: String, body: Map<String, Any?>, token: String?): JsonNode? {
        if (!catalogoEnabled) {
            logger.debug("Integración con catálogo deshabilitada; se omite {}", operacion)
            return null
        }

        return try {
            val peticion = catalogoRestClient.post()
                .uri("/api/catalog/services/{id}/$operacion", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
            if (!token.isNullOrBlank()) {
                peticion.header("Authorization", "Bearer $token")
            }
            val respuesta = peticion.body(body).retrieve().body(JsonNode::class.java)
            respuesta?.path("data")
        } catch (ex: RestClientResponseException) {
            when (ex.statusCode.value()) {
                404 -> throw ServicioCatalogoNoEncontradoException("El servicio $serviceId no existe en el catálogo (404)")
                409 -> throw CapacidadNoDisponibleException("Catálogo rechazó '$operacion': sin capacidad disponible (409)")
                400 -> throw IllegalArgumentException("Catálogo rechazó la solicitud: ${mensajeDe(ex)}")
                else -> throw CatalogoNoDisponibleException("Error del catálogo en '$operacion' (HTTP ${ex.statusCode.value()})", ex)
            }
        } catch (ex: Exception) {
            throw CatalogoNoDisponibleException("No fue posible contactar al catálogo: ${ex.message}", ex)
        }
    }
}
