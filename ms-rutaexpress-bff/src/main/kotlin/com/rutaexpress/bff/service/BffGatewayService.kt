package com.rutaexpress.bff.service

import com.rutaexpress.bff.model.dto.DashboardSummaryDto
import com.rutaexpress.bff.model.dto.GatewayHealthDto
import com.rutaexpress.bff.model.dto.UserProfileDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException

interface BffGatewayService {
    fun getUserProfile(jwt: Jwt?): UserProfileDto
    fun buildDashboard(jwt: Jwt?): DashboardSummaryDto
    fun fetchCatalogServices(token: String): Any
    fun fetchKpis(range: String, token: String): Any
    fun fetchTimeline(idEntidad: String, token: String): Any
    fun fetchShipments(status: String?, token: String): Any
    fun fetchShipment(id: String, token: String): Any
    fun createShipment(body: Map<String, Any?>, token: String): Any
    fun changeShipmentStatus(id: String, status: String, token: String): Any
    fun getStatus(): GatewayHealthDto
}

@Service
class DefaultBffGatewayService(
    @Qualifier("catalogRestClient") private val catalogClient: RestClient,
    @Qualifier("reportRestClient") private val reportClient: RestClient,
    @Qualifier("auditRestClient") private val auditClient: RestClient,
    @Qualifier("shipmentsRestClient") private val shipmentsClient: RestClient,
    @Value("\${rutaexpress.services.catalog-url}") private val catalogUrl: String,
    @Value("\${rutaexpress.services.notify-url}") private val notifyUrl: String,
    @Value("\${rutaexpress.services.audit-url}") private val auditUrl: String,
    @Value("\${rutaexpress.services.report-url}") private val reportUrl: String,
    @Value("\${rutaexpress.services.shipments-url:http://localhost:8085}") private val shipmentsUrl: String
) : BffGatewayService {
    private val logger = LoggerFactory.getLogger(DefaultBffGatewayService::class.java)

    override fun getUserProfile(jwt: Jwt?): UserProfileDto {
        if (jwt == null) {
            return UserProfileDto(
                userId = "anonimo",
                username = "usuario-invitado",
                name = "Usuario Invitado",
                email = null,
                roles = listOf("CLIENTE"),
                scopes = emptyList(),
                tenantId = null
            )
        }

        val roles = when (val claim = jwt.claims["roles"]) {
            is List<*> -> claim.filterIsInstance<String>()
            else -> emptyList()
        }

        val scopes = when (val scp = jwt.claims["scp"] ?: jwt.claims["scope"]) {
            is String -> scp.split(" ").filter { it.isNotBlank() }
            is List<*> -> scp.filterIsInstance<String>()
            else -> emptyList()
        }

        val tenantId = jwt.getClaimAsString("tid")
        val oid = jwt.getClaimAsString("oid") ?: jwt.subject ?: "desconocido"
        val username = jwt.getClaimAsString("preferred_username")
            ?: jwt.getClaimAsString("upn")
            ?: jwt.getClaimAsString("email")
            ?: oid
        val name = jwt.getClaimAsString("name")
        val email = jwt.getClaimAsString("email") ?: jwt.getClaimAsString("preferred_username")

        return UserProfileDto(
            userId = oid,
            username = username,
            name = name,
            email = email,
            roles = roles,
            scopes = scopes,
            tenantId = tenantId
        )
    }

    override fun buildDashboard(jwt: Jwt?): DashboardSummaryDto {
        val profile = getUserProfile(jwt)
        val primaryRole = profile.roles.firstOrNull()?.uppercase() ?: "CLIENTE"
        val token = jwt?.tokenValue ?: ""

        var catalogData: Any? = null
        var reportData: Any? = null
        var auditData: Any? = null
        val notices = mutableListOf<String>()

        if (primaryRole in listOf("ADMIN", "DESPACHADOR")) {
            catalogData = fetchCatalogServices(token)
        }

        if (primaryRole == "ADMIN") {
            reportData = fetchKpis("last24h", token)
        }

        if (primaryRole in listOf("ADMIN", "AUDITOR")) {
            auditData = fetchAuditSummary(token)
        }

        return DashboardSummaryDto(
            role = primaryRole,
            greeting = "Bienvenido a RutaExpress, ${profile.name ?: profile.username}",
            catalogOverview = catalogData,
            reportOverview = reportData,
            auditOverview = auditData,
            notices = notices
        )
    }

    override fun fetchCatalogServices(token: String): Any {
        return try {
            catalogClient.get()
                .uri("/api/catalog/services")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyList<Any>()
        } catch (e: Exception) {
            logger.warn("ms-rutaexpress-catalog no disponible: ${e.message}")
            mapOf("aviso" to "ms-rutaexpress-catalog no disponible localmente", "detalle" to e.message)
        }
    }

    override fun fetchKpis(range: String, token: String): Any {
        return try {
            reportClient.get()
                .uri("/api/report/kpis?range={range}", range)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            logger.warn("ms-rutaexpress-report no disponible: ${e.message}")
            mapOf("aviso" to "ms-rutaexpress-report no disponible localmente", "detalle" to e.message)
        }
    }

    override fun fetchTimeline(idEntidad: String, token: String): Any {
        return try {
            auditClient.get()
                .uri("/api/audit/entidad/{idEntidad}", idEntidad)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyList<Any>()
        } catch (e: Exception) {
            logger.warn("ms-rutaexpress-audit no disponible: ${e.message}")
            mapOf("aviso" to "ms-rutaexpress-audit no disponible localmente", "detalle" to e.message)
        }
    }

    private fun fetchAuditSummary(token: String): Any {
        return try {
            auditClient.get()
                .uri("/api/audit")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyList<Any>()
        } catch (e: Exception) {
            emptyList<Any>()
        }
    }

    override fun fetchShipments(status: String?, token: String): Any {
        return try {
            val peticion = if (status.isNullOrBlank()) {
                shipmentsClient.get().uri("/api/shipments")
            } else {
                shipmentsClient.get().uri("/api/shipments?status={status}", status)
            }
            peticion
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyList<Any>()
        } catch (e: Exception) {
            falloDownstream("ms-rutaexpress-shipments", e)
        }
    }

    override fun fetchShipment(id: String, token: String): Any {
        return try {
            shipmentsClient.get()
                .uri("/api/shipments/{id}", id)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            falloDownstream("ms-rutaexpress-shipments", e)
        }
    }

    override fun createShipment(body: Map<String, Any?>, token: String): Any {
        return try {
            shipmentsClient.post()
                .uri("/api/shipments")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            falloDownstream("ms-rutaexpress-shipments", e)
        }
    }

    override fun changeShipmentStatus(id: String, status: String, token: String): Any {
        return try {
            shipmentsClient.put()
                .uri("/api/shipments/{id}/status", id)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("status" to status))
                .retrieve()
                .body(Any::class.java) ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            falloDownstream("ms-rutaexpress-shipments", e)
        }
    }

    /**
     * Propaga el código de error real del microservicio (409 sin capacidad, 404, 403, etc.)
     * en lugar de devolver 200 con un aviso.
     */
    private fun falloDownstream(servicio: String, e: Exception): Nothing {
        logger.warn("$servicio respondió con error: ${e.message}")
        if (e is HttpClientErrorException) {
            throw ResponseStatusException(e.statusCode, e.responseBodyAsString, e)
        }
        if (e is org.springframework.web.client.HttpServerErrorException) {
            throw ResponseStatusException(e.statusCode, e.responseBodyAsString, e)
        }
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "$servicio no disponible", e)
    }

    override fun getStatus(): GatewayHealthDto {
        return GatewayHealthDto(
            azureIdaasConfigured = true,
            downstreamServices = mapOf(
                "catalog" to catalogUrl,
                "notify" to notifyUrl,
                "audit" to auditUrl,
                "report" to reportUrl,
                "shipments" to shipmentsUrl
            )
        )
    }
}
