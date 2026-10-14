package com.rutaexpress.shipments.controlador

import com.rutaexpress.shipments.dominio.CambiarEstadoRequest
import com.rutaexpress.shipments.dominio.CrearEnvioRequest
import com.rutaexpress.shipments.dominio.EnvioResponse
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Obtener un envío por id")
    fun obtener(@PathVariable id: String): EnvioResponse = servicio.obtener(id)

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR')")
    @Operation(summary = "Listar envíos con filtros opcionales de estado y fechas de creación")
    fun listar(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?
    ): List<EnvioResponse> = servicio.listar(status, from, to)

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DESPACHADOR')")
    @Operation(summary = "Cambiar estado del envío (reserva/libera capacidad y dispara eventos y notificaciones)")
    fun cambiarEstado(
        @PathVariable id: String,
        @Valid @RequestBody request: CambiarEstadoRequest,
        @AuthenticationPrincipal jwt: Jwt?
    ): EnvioResponse = servicio.cambiarEstado(id, request.status, jwt?.tokenValue)
}
