package com.rutaexpress.shipments.cliente

import com.fasterxml.jackson.databind.JsonNode
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
 * Cliente del microservicio de catálogo (ms-rutaexpress-catalog).
 * Reenvía el JWT del llamador porque los endpoints de capacidad exigen rol ADMIN/OPERADOR.
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

    fun reservarCapacidad(
        serviceId: Long,
        zone: String,
        vehicleType: String,
        packagesCount: Int,
        weightKg: BigDecimal,
        volumeM3: BigDecimal,
        token: String?
    ): Int {
        val data = llamar(
            serviceId,
            "capacity/reserve",
            mapOf(
                "zone" to zone,
                "vehicleType" to vehicleType,
                "packagesCount" to packagesCount,
                "weightKg" to weightKg,
                "volumeM3" to volumeM3
            ),
            token
        ) ?: throw ServicioCatalogoNoEncontradoException("El servicio $serviceId no existe en el catálogo")

        return data.path("remainingCapacity").asInt(0)
    }

    fun liberarCapacidad(
        serviceId: Long,
        zone: String,
        vehicleType: String,
        packagesCount: Int,
        token: String?
    ): Int {
        val data = llamar(
            serviceId,
            "capacity/release",
            mapOf(
                "zone" to zone,
                "vehicleType" to vehicleType,
                "packagesCount" to packagesCount
            ),
            token
        ) ?: throw ServicioCatalogoNoEncontradoException("El servicio $serviceId no existe en el catálogo")

        return data.path("remainingCapacity").asInt(0)
    }

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
                else -> throw CatalogoNoDisponibleException("Error del catálogo en '$operacion' (HTTP ${ex.statusCode.value()})", ex)
            }
        } catch (ex: Exception) {
            throw CatalogoNoDisponibleException("No fue posible contactar al catálogo: ${ex.message}", ex)
        }
    }
}
