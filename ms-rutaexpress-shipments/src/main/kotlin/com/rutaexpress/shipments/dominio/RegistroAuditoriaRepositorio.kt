package com.rutaexpress.shipments.dominio

import org.springframework.data.jpa.repository.JpaRepository

interface RegistroAuditoriaRepositorio : JpaRepository<RegistroAuditoriaEnvio, String> {
    fun findByShipmentIdOrderByOcurridoEnAsc(shipmentId: String): List<RegistroAuditoriaEnvio>
}
