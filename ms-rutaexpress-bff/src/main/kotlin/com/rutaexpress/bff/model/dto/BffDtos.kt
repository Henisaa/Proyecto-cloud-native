package com.rutaexpress.bff.model.dto

import java.time.Instant

data class UserProfileDto(
    val userId: String,
    val username: String,
    val name: String?,
    val email: String?,
    val roles: List<String>,
    val scopes: List<String>,
    val tenantId: String?,
    val authenticatedAt: Instant = Instant.now()
)

/** Un bloque del panel: si el servicio que lo alimenta no responde, solo ese bloque queda como no disponible. */
data class DashboardBlock(
    val available: Boolean,
    val data: Any? = null,
    val message: String? = null,
)

data class DashboardSummaryDto(
    val role: String,
    val greeting: String,
    val generatedAt: Instant = Instant.now(),
    val shipmentsOverview: DashboardBlock? = null,
    val catalogOverview: DashboardBlock? = null,
    val reportOverview: DashboardBlock? = null,
    val auditOverview: DashboardBlock? = null,
    val notices: List<String> = emptyList()
)

/** Estado de cada servicio: solo nombre y UP/DOWN. No expone URLs ni detalles internos. */
data class GatewayHealthDto(
    val service: String = "ms-rutaexpress-bff",
    val status: String = "UP",
    val azureIdaasConfigured: Boolean,
    val services: Map<String, String>
)
