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

data class DashboardSummaryDto(
    val role: String,
    val greeting: String,
    val generatedAt: Instant = Instant.now(),
    val catalogOverview: Any? = null,
    val reportOverview: Any? = null,
    val auditOverview: Any? = null,
    val notices: List<String> = emptyList()
)

data class GatewayHealthDto(
    val service: String = "ms-rutaexpress-bff",
    val status: String = "UP",
    val azureIdaasConfigured: Boolean,
    val downstreamServices: Map<String, String>
)
