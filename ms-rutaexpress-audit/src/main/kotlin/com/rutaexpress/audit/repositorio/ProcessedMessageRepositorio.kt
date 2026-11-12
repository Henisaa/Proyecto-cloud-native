package com.rutaexpress.audit.repositorio

import com.rutaexpress.audit.dominio.ProcessedMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProcessedMessageRepositorio : JpaRepository<ProcessedMessage, String>
