package com.rutaexpress.shipments.controlador

import com.rutaexpress.shipments.dominio.CambiarEstadoRequest
import com.rutaexpress.shipments.dominio.CrearEnvioRequest
import com.rutaexpress.shipments.dominio.EnvioResponse
import com.rutaexpress.shipments.dominio.PaginaResponse
import com.rutaexpress.shipments.dominio.RegistroAuditoriaResponse
import com.rutaexpress.shipments.dominio.ResumenEnviosResponse
import com.rutaexpress.shipments.servicio.EnvioServicio
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/shipments")
@Tag(name = "Envíos", description = "CRUD de envíos, máquina de estados, capacidad y notificaciones")
class EnvioControlador(
    private val servicio: EnvioServicio
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Crear envío (verifica capacidad disponible en catálogo)")
    fun crear(
        @Valid @RequestBody request: CrearEnvioRequest,
        @AuthenticationPrincipal jwt: Jwt?
    ): EnvioResponse = servicio.crear(request, jwt?.tokenValue)

    @GetMapping("/audit/resolve/{reference}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    fun referenciaAuditoria(@PathVariable reference: String): Map<String, String> = servicio.referenciaAuditoria(reference)

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Conteos por estado y por capacidad, y los últimos envíos (un cliente solo ve los suyos)")
    fun resumen(): ResumenEnviosResponse = servicio.resumen()

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Obtener un envío por id (un cliente solo ve los suyos)")
    fun obtener(@PathVariable id: String): EnvioResponse = servicio.obtener(id)

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Línea de tiempo del envío según los eventos de RabbitMQ")
    fun auditoria(@PathVariable id: String): List<RegistroAuditoriaResponse> = servicio.auditoria(id)

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')")
    @Operation(summary = "Listar envíos paginados, con filtros de estado, capacidad, fechas y código de seguimiento")
    fun listar(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) capacity: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PaginaResponse<EnvioResponse> = servicio.listar(status, capacity, from, to, q, page, size)

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR')")
    @Operation(summary = "Cambiar estado del envío (reserva/libera capacidad y dispara eventos y notificaciones)")
    fun cambiarEstado(
        @PathVariable id: String,
        @Valid @RequestBody request: CambiarEstadoRequest,
        @AuthenticationPrincipal jwt: Jwt?
    ): EnvioResponse = servicio.cambiarEstado(id, request.status, jwt?.tokenValue)

    @PostMapping("/{id}/capacity/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR')")
    @Operation(summary = "Reintentar la reserva de capacidad de un envío ACEPTADO con capacidad RECHAZADA")
    fun reintentarReserva(@PathVariable id: String): EnvioResponse = servicio.reintentarReserva(id)
}
