package com.rutaexpress.bff.service

import com.rutaexpress.bff.model.dto.DashboardBlock
import com.rutaexpress.bff.model.dto.DashboardSummaryDto
import com.rutaexpress.bff.model.dto.GatewayHealthDto
import com.rutaexpress.bff.model.dto.UserProfileDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException

/**
 * Un microservicio interno respondió con error (o no respondió). Conserva el código y el cuerpo originales para que
 * la interfaz muestre el mensaje exacto del backend en vez de un texto genérico.
 */
class DownstreamException(
    val status: HttpStatusCode,
    val body: String?,
    val servicio: String,
    cause: Throwable? = null,
) : RuntimeException("$servicio respondió ${status.value()}", cause)

interface BffGatewayService {
    fun getUserProfile(jwt: Jwt?): UserProfileDto
    fun buildDashboard(jwt: Jwt?): DashboardSummaryDto
    fun fetchCatalogServices(token: String): Any
    fun fetchCatalogService(id: Long, token: String): Any
    fun updateCapacity(serviceId: Long, capacityId: Long, body: Map<String, Any?>, token: String): Any
    fun fetchKpis(range: String, token: String): Any
    fun fetchTopServices(range: String, token: String): Any
    fun fetchTimeline(idEntidad: String, token: String): Any
    fun fetchShipments(filtros: Map<String, String>, token: String): Any
    fun fetchShipmentsSummary(token: String): Any
    fun fetchShipment(id: String, token: String): Any
    fun fetchShipmentTimeline(id: String, token: String): Any
    fun createShipment(body: Map<String, Any?>, token: String): Any
    fun changeShipmentStatus(id: String, status: String, token: String): Any
    fun retryShipmentCapacity(id: String, token: String): Any
    fun getStatus(): GatewayHealthDto
}

@Service
class DefaultBffGatewayService(
    @Qualifier("catalogRestClient") private val catalogClient: RestClient,
    @Qualifier("reportRestClient") private val reportClient: RestClient,
    @Qualifier("auditRestClient") private val auditClient: RestClient,
    @Qualifier("shipmentsRestClient") private val shipmentsClient: RestClient,
    @Qualifier("healthRestClient") private val healthClient: RestClient,
    @Value("\${rutaexpress.services.catalog-url}") private val catalogUrl: String,
    @Value("\${rutaexpress.services.notify-url}") private val notifyUrl: String,
    @Value("\${rutaexpress.services.audit-url}") private val auditUrl: String,
    @Value("\${rutaexpress.services.report-url}") private val reportUrl: String,
    @Value("\${rutaexpress.services.shipments-url:http://localhost:8085}") private val shipmentsUrl: String,
    @Value("\${rutaexpress.services.mq-admin-url:http://localhost:8086}") private val mqAdminUrl: String,
    @Value("\${rutaexpress.services.kafka-admin-url:}") private val kafkaAdminUrl: String,
    @Value("\${app.security.enabled:true}") private val seguridadActiva: Boolean,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") private val issuerUri: String,
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

    /**
     * Panel agregado por rol. Cada bloque se consulta por separado: si un servicio no responde (por ejemplo, report
     * con el perfil ep1 apagado), ese bloque se marca como no disponible y el resto del panel se entrega igual.
     */
    override fun buildDashboard(jwt: Jwt?): DashboardSummaryDto {
        val profile = getUserProfile(jwt)
        val normalizedRoles = profile.roles.map {
            when (val role = it.trim().removePrefix("ROLE_").uppercase()) {
                "OPERADOR DEL DOMINIO", "OPERADOR" -> "DESPACHADOR"
                "CLIENTE DEL DOMINIO" -> "CLIENTE"
                else -> role
            }
        }
        val primaryRole = listOf("ADMIN", "DESPACHADOR", "AUDITOR", "CLIENTE").firstOrNull { it in normalizedRoles } ?: "CLIENTE"
        val token = jwt?.tokenValue ?: ""
        val avisos = mutableListOf<String>()

        fun bloque(nombre: String, consulta: () -> Any): DashboardBlock = try {
            DashboardBlock(available = true, data = consulta())
        } catch (e: DownstreamException) {
            logger.warn("Panel: {} no disponible ({})", nombre, e.message)
            avisos += "No se pudo consultar $nombre."
            DashboardBlock(available = false, message = "$nombre no disponible")
        }

        val envios = bloque("los envíos") { fetchShipmentsSummary(token) }
        val catalogo = if (primaryRole in listOf("ADMIN", "DESPACHADOR")) bloque("el catálogo") { fetchCatalogServices(token) } else null
        val reportes = if (primaryRole == "ADMIN") bloque("los indicadores de reportes") { fetchKpis("last24h", token) } else null
        val auditoria = if (primaryRole in listOf("ADMIN", "AUDITOR")) bloque("la auditoría") { fetchAuditSummary(token) } else null

        return DashboardSummaryDto(
            role = primaryRole,
            greeting = "Bienvenido a RutaExpress, ${profile.name ?: profile.username}",
            shipmentsOverview = envios,
            catalogOverview = catalogo,
            reportOverview = reportes,
            auditOverview = auditoria,
            notices = avisos,
        )
    }

    override fun fetchCatalogServices(token: String): Any = llamarGet("ms-rutaexpress-catalog") {
        val result = catalogClient.get()
            .uri("/api/catalog/services")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyList<Any>()
        if (result is Map<*, *>) result["data"] ?: emptyList<Any>() else result
    }

    override fun fetchCatalogService(id: Long, token: String): Any = llamarGet("ms-rutaexpress-catalog") {
        val result = catalogClient.get()
            .uri("/api/catalog/services/{id}", id)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
        if (result is Map<*, *>) result["data"] ?: result else result
    }

    override fun updateCapacity(serviceId: Long, capacityId: Long, body: Map<String, Any?>, token: String): Any =
        llamar("ms-rutaexpress-catalog") {
            val result = catalogClient.put()
                .uri("/api/catalog/services/{id}/capacities/{capacityId}", serviceId, capacityId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Any::class.java) ?: emptyMap<String, Any>()
            if (result is Map<*, *>) result["data"] ?: result else result
        }

    override fun fetchKpis(range: String, token: String): Any = desplegado("ms-rutaexpress-report", reportUrl) {
        reportClient.get()
            .uri("/api/report/kpis?range={range}", range)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    override fun fetchTopServices(range: String, token: String): Any = desplegado("ms-rutaexpress-report", reportUrl) {
        reportClient.get()
            .uri("/api/report/top-services?range={range}", range)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyList<Any>()
    }

    override fun fetchTimeline(idEntidad: String, token: String): Any = desplegado("ms-rutaexpress-audit", auditUrl) {
        auditClient.get()
            .uri("/api/audit/entidad/{idEntidad}", idEntidad)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyList<Any>()
    }

    private fun fetchAuditSummary(token: String): Any = desplegado("ms-rutaexpress-audit", auditUrl) {
        auditClient.get()
            .uri("/api/audit?page=0&size=10")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyList<Any>()
    }

    /** Los filtros ya vienen validados por lista blanca desde el controlador; el cliente codifica los valores. */
    override fun fetchShipments(filtros: Map<String, String>, token: String): Any = llamarGet("ms-rutaexpress-shipments") {
        shipmentsClient.get()
            .uri { uri ->
                val destino = org.springframework.web.util.UriComponentsBuilder.fromUri(uri.path("/api/shipments").build())
                filtros.keys.forEach { destino.queryParam(it, "{$it}") }
                destino.encode().buildAndExpand(filtros).toUri()
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    override fun fetchShipmentsSummary(token: String): Any = llamarGet("ms-rutaexpress-shipments") {
        shipmentsClient.get()
            .uri("/api/shipments/summary")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    override fun fetchShipment(id: String, token: String): Any = llamarGet("ms-rutaexpress-shipments") {
        shipmentsClient.get()
            .uri("/api/shipments/{id}", id)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    /**
     * Línea de tiempo unificada: eventos de RabbitMQ (shipments) más la auditoría de Kafka de la EP1 (audit).
     * Si audit está apagado, se devuelve solo la de RabbitMQ y se indica con kafkaAvailable=false.
     */
    override fun fetchShipmentTimeline(id: String, token: String): Any {
        val rabbit = llamarGet("ms-rutaexpress-shipments") {
            shipmentsClient.get()
                .uri("/api/shipments/{id}/audit", id)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Any::class.java) ?: emptyList<Any>()
        }
        val kafka: Any? = try {
            fetchTimeline(id, token)
        } catch (e: DownstreamException) {
            logger.info("Línea de tiempo: la auditoría de Kafka no está disponible ({})", e.message)
            null
        }

        val eventos = mutableListOf<Map<String, Any?>>()
        (rabbit as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach {
            eventos += mapOf(
                "source" to "RABBITMQ",
                "eventType" to it["eventType"],
                "routingKey" to it["routingKey"],
                "detail" to it["detail"],
                "actor" to actorDeDetalle(it["detail"] as? String),
                "occurredAt" to it["occurredAt"],
            )
        }
        (kafka as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach {
            eventos += mapOf(
                "source" to "KAFKA",
                "eventType" to it["tipoEvento"],
                "routingKey" to it["origen"],
                "detail" to it["datosPayload"],
                "actor" to it["usuarioResponsable"],
                "occurredAt" to it["fechaEvento"],
            )
        }
        eventos.sortBy { it["occurredAt"]?.toString() ?: "" }
        return mapOf("shipmentId" to id, "kafkaAvailable" to (kafka != null), "events" to eventos)
    }

    private fun actorDeDetalle(detalle: String?): String? =
        detalle?.substringAfter("actor=", "")?.takeIf { it.isNotBlank() }

    override fun createShipment(body: Map<String, Any?>, token: String): Any = llamar("ms-rutaexpress-shipments") {
        shipmentsClient.post()
            .uri("/api/shipments")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    override fun changeShipmentStatus(id: String, status: String, token: String): Any = llamar("ms-rutaexpress-shipments") {
        shipmentsClient.put()
            .uri("/api/shipments/{id}/status", id)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("status" to status))
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    override fun retryShipmentCapacity(id: String, token: String): Any = llamar("ms-rutaexpress-shipments") {
        shipmentsClient.post()
            .uri("/api/shipments/{id}/capacity/retry", id)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(Any::class.java) ?: emptyMap<String, Any>()
    }

    /**
     * Solo nombre y UP/DOWN según el health real de cada servicio; nunca las URLs internas. Un servicio sin URL
     * configurada no se informa: este stack no lo despliega.
     */
    override fun getStatus(): GatewayHealthDto {
        val servicios = linkedMapOf(
            "shipments" to shipmentsUrl,
            "catalog" to catalogUrl,
            "notify" to notifyUrl,
            "rabbit-admin" to mqAdminUrl,
            "audit" to auditUrl,
            "report" to reportUrl,
            "kafka-admin" to kafkaAdminUrl,
        )
        return GatewayHealthDto(
            azureIdaasConfigured = seguridadActiva && issuerUri.isNotBlank(),
            services = servicios.filterValues { it.isNotBlank() }.mapValues { (_, url) -> estadoDe(url) },
        )
    }

    private fun estadoDe(url: String): String = try {
        healthClient.get().uri("$url/actuator/health/readiness").retrieve().toBodilessEntity()
        "UP"
    } catch (e: RestClientException) {
        "DOWN"
    }

    /** Un servicio que este stack no despliega (URL vacía) no se consulta: se informa como no disponible. */
    private fun <T : Any> desplegado(servicio: String, url: String, accion: () -> T): T =
        if (url.isBlank()) throw DownstreamException(HttpStatus.SERVICE_UNAVAILABLE, null, servicio)
        else llamarGet(servicio, accion)

    /** GET con un reintento si falla la conexión o se agota el tiempo (solo lecturas: son idempotentes). */
    private fun <T : Any> llamarGet(servicio: String, accion: () -> T): T = llamar(servicio) {
        try {
            accion()
        } catch (e: ResourceAccessException) {
            logger.info("{}: nuevo intento tras un fallo de conexión ({})", servicio, e.message)
            accion()
        }
    }

    /** Traduce los fallos HTTP del microservicio a DownstreamException, conservando código y cuerpo. */
    private fun <T : Any> llamar(servicio: String, accion: () -> T): T = try {
        accion()
    } catch (e: RestClientResponseException) {
        logger.warn("{} respondió con error {}", servicio, e.statusCode.value())
        throw DownstreamException(e.statusCode, e.responseBodyAsString, servicio, e)
    } catch (e: ResourceAccessException) {
        logger.warn("{} no disponible: {}", servicio, e.message)
        val agotado = e.cause is HttpTimeoutException || e.cause is SocketTimeoutException
        throw DownstreamException(if (agotado) HttpStatus.GATEWAY_TIMEOUT else HttpStatus.SERVICE_UNAVAILABLE, null, servicio, e)
    } catch (e: RestClientException) {
        logger.warn("{} falló: {}", servicio, e.message)
        throw DownstreamException(HttpStatus.SERVICE_UNAVAILABLE, null, servicio, e)
    }
}
