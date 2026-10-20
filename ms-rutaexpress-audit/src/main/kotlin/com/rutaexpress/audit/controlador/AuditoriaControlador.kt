package com.rutaexpress.audit.controlador

import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Auditoría y Línea de Tiempo", description = "Endpoints de solo lectura para auditoría y trazabilidad")
class AuditoriaControlador(
    private val auditoriaRepositorio: AuditoriaRepositorio
) {

    @GetMapping
    @Operation(summary = "Listar eventos recientes de auditoría, paginados (máximo 100 por página)")
    fun listarTodos(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<List<EventoAuditoria>> {
        val pagina = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "fechaEvento"))
        return ResponseEntity.ok(auditoriaRepositorio.findAll(pagina).content)
    }

    @GetMapping("/entidad/{idEntidad}")
    @Operation(summary = "Consultar el historial y timeline por identificador de entidad (ej: idEnvio)")
    fun obtenerPorEntidad(@PathVariable idEntidad: String): ResponseEntity<List<EventoAuditoria>> =
        ResponseEntity.ok(auditoriaRepositorio.findByIdEntidadOrderByFechaEventoDesc(idEntidad))

    @GetMapping("/tipo/{tipoEntidad}")
    @Operation(summary = "Consultar eventos por tipo de entidad")
    fun obtenerPorTipo(@PathVariable tipoEntidad: String): ResponseEntity<List<EventoAuditoria>> =
        ResponseEntity.ok(auditoriaRepositorio.findByTipoEntidadOrderByFechaEventoDesc(tipoEntidad))
}
