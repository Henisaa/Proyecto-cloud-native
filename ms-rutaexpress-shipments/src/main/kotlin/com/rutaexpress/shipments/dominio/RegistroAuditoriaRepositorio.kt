package com.rutaexpress.shipments.dominio

import org.springframework.data.jpa.repository.JpaRepository

interface RegistroAuditoriaRepositorio : JpaRepository<RegistroAuditoriaEnvio, String>
