package com.rutaexpress.bff.controller

import com.rutaexpress.bff.model.dto.DashboardSummaryDto
import com.rutaexpress.bff.model.dto.GatewayHealthDto
import com.rutaexpress.bff.model.dto.UserProfileDto
import com.rutaexpress.bff.service.BffGatewayService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/bff")
@Tag(name = "BFF Gateway RutaExpress", description = "Backend For Frontend protegido por Azure AD JWT")
@SecurityRequirement(name = "bearerAuth")
class BffController(
    private val bffService: BffGatewayService
) {

    @GetMapping("/auth/me")
    @Operation(summary = "Obtener perfil y roles del usuario autenticado vía Azure AD")
    fun getMyProfile(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<UserProfileDto> {
        val profile = bffService.getUserProfile(jwt)
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Panel consolidado según el rol; un servicio caído solo deja no disponible su bloque")
    fun getDashboard(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<DashboardSummaryDto> {
        val dashboard = bffService.buildDashboard(jwt)
        return ResponseEntity.ok(dashboard)
    }

    @GetMapping("/catalog/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Listar catálogo de servicios disponibles (retransmite a ms-rutaexpress-catalog)")
    fun getCatalogServices(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Any> {
        val response = bffService.fetchCatalogServices(jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @GetMapping("/catalog/services/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Detalle de un servicio con tarifas; las capacidades solo las ven Admin y Despachador")
    fun getCatalogService(
        @PathVariable id: Long,
        authentication: Authentication,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val detalle = bffService.fetchCatalogService(id, jwt?.tokenValue ?: "")
        val veCapacidades = authentication.authorities.any { it.authority == "ROLE_ADMIN" || it.authority == "ROLE_DESPACHADOR" }
        if (!veCapacidades && detalle is Map<*, *>) {
            return ResponseEntity.ok(detalle.filterKeys { it != "capacities" })
        }
        return ResponseEntity.ok(detalle)
    }

    @PutMapping("/catalog/manage/services/{id}/capacities/{capacityId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar una capacidad de flota: máximo diario, peso, volumen o estado activo (solo ADMIN)")
    fun updateCapacity(
        @PathVariable id: Long,
        @PathVariable capacityId: Long,
        @RequestBody body: Map<String, Any?>,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> =
        ResponseEntity.ok(bffService.updateCapacity(id, capacityId, body, jwt?.tokenValue ?: ""))

    @GetMapping("/report/kpis")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar KPIs de red (retransmite a ms-rutaexpress-report, exclusivo ADMIN)")
    fun getKpis(
        @RequestParam(defaultValue = "last24h") range: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.fetchKpis(range, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @GetMapping("/report/top-services")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Servicios más usados (retransmite a ms-rutaexpress-report, exclusivo ADMIN)")
    fun getTopServices(
        @RequestParam(defaultValue = "last7d") range: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> = ResponseEntity.ok(bffService.fetchTopServices(range, jwt?.tokenValue ?: ""))

    @GetMapping("/audit/timeline/{idEntidad}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Consultar trazabilidad de envío (retransmite a ms-rutaexpress-audit, ADMIN y AUDITOR)")
    fun getTimeline(
        @PathVariable idEntidad: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.fetchTimeline(idEntidad, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @GetMapping("/shipments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Listar envíos paginados con filtros (page, size, status, capacity, from, to, q)")
    fun getShipments(
        @RequestParam params: Map<String, String>,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val filtros = params.filterKeys { it in FILTROS_ENVIOS }.filterValues { it.isNotBlank() }
        return ResponseEntity.ok(bffService.fetchShipments(filtros, jwt?.tokenValue ?: ""))
    }

    @GetMapping("/shipments/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Conteos por estado y capacidad, y los últimos envíos (un cliente solo ve los suyos)")
    fun getShipmentsSummary(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Any> =
        ResponseEntity.ok(bffService.fetchShipmentsSummary(jwt?.tokenValue ?: ""))

    @GetMapping("/shipments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Obtener un envío por id, con las transiciones permitidas")
    fun getShipment(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.fetchShipment(id, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @GetMapping("/shipments/{id}/timeline")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'AUDITOR')")
    @Operation(summary = "Línea de tiempo unificada del envío: RabbitMQ (shipments) y Kafka (audit)")
    fun getShipmentTimeline(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> = ResponseEntity.ok(bffService.fetchShipmentTimeline(id, jwt?.tokenValue ?: ""))

    @PostMapping("/shipments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Crear envío validando capacidad en catálogo (retransmite a ms-rutaexpress-shipments)")
    fun createShipment(
        @RequestBody body: Map<String, Any?>,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.createShipment(body, jwt?.tokenValue ?: "")
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/shipments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR')")
    @Operation(summary = "Cambiar estado del envío: reserva/libera capacidad y dispara eventos (retransmite a ms-rutaexpress-shipments)")
    fun changeShipmentStatus(
        @PathVariable id: String,
        @RequestBody body: Map<String, Any?>,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val status = body["status"]?.toString() ?: ""
        val response = bffService.changeShipmentStatus(id, status, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @PostMapping("/shipments/{id}/capacity/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR')")
    @Operation(summary = "Reintentar la reserva de capacidad de un envío ACEPTADO con capacidad RECHAZADA")
    fun retryCapacity(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> = ResponseEntity.ok(bffService.retryShipmentCapacity(id, jwt?.tokenValue ?: ""))

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Estado UP/DOWN de cada microservicio, sin exponer URLs internas (solo ADMIN)")
    fun getStatus(): ResponseEntity<GatewayHealthDto> {
        return ResponseEntity.ok(bffService.getStatus())
    }

    private companion object {
        val FILTROS_ENVIOS = setOf("page", "size", "status", "capacity", "from", "to", "q")
    }
}
