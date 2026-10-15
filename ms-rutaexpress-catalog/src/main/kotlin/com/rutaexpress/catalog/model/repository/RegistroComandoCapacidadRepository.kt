package com.rutaexpress.catalog.model.repository

import com.rutaexpress.catalog.model.entity.RegistroComandoCapacidad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RegistroComandoCapacidadRepository : JpaRepository<RegistroComandoCapacidad, String>
