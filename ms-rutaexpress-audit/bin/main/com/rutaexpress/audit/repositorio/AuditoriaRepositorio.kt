package com.rutaexpress.audit.repositorio

import com.rutaexpress.audit.dominio.EventoAuditoria
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditoriaRepositorio : JpaRepository<EventoAuditoria, Long> {
    fun findByIdEntidadOrderByFechaEventoDesc(idEntidad: String): List<EventoAuditoria>
    fun findByTipoEntidadOrderByFechaEventoDesc(tipoEntidad: String): List<EventoAuditoria>
}
