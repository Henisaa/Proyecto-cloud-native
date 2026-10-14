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
    @Operation(summary = "Panel de control consolidado según el rol del usuario (Admin, Despachador, Cliente, Auditor)")
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
    @Operation(summary = "Listar envíos con filtro opcional de estado (retransmite a ms-rutaexpress-shipments)")
    fun getShipments(
        @RequestParam(required = false) status: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.fetchShipments(status, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

    @GetMapping("/shipments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Obtener un envío por id (retransmite a ms-rutaexpress-shipments)")
    fun getShipment(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<Any> {
        val response = bffService.fetchShipment(id, jwt?.tokenValue ?: "")
        return ResponseEntity.ok(response)
    }

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

    @GetMapping("/status")
    @Operation(summary = "Estado de conectividad del BFF con Azure IDaaS y microservicios")
    fun getStatus(): ResponseEntity<GatewayHealthDto> {
        return ResponseEntity.ok(bffService.getStatus())
    }
}
