package com.rutaexpress.catalog.model.repository

import com.rutaexpress.catalog.model.entity.Tariff
import com.rutaexpress.catalog.model.enums.ZoneType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TariffRepository : JpaRepository<Tariff, Long> {

    fun findByServiceId(serviceId: Long): List<Tariff>

    fun findByServiceIdAndOriginZoneAndDestinationZone(
        serviceId: Long,
        originZone: ZoneType,
        destinationZone: ZoneType
    ): Optional<Tariff>
}
