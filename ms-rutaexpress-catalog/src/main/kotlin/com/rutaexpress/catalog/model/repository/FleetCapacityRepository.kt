package com.rutaexpress.catalog.model.repository

import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FleetCapacityRepository : JpaRepository<FleetCapacity, Long> {

    fun findByServiceId(serviceId: Long): List<FleetCapacity>

    fun findByServiceIdAndZone(serviceId: Long, zone: ZoneType): List<FleetCapacity>

    fun findByServiceIdAndZoneAndVehicleType(
        serviceId: Long,
        zone: ZoneType,
        vehicleType: VehicleType
    ): Optional<FleetCapacity>

    @Query("""
        SELECT c FROM FleetCapacity c 
        WHERE c.service.id = :serviceId 
          AND c.zone = :zone 
          AND c.active = true 
          AND c.currentBookedPackages < c.maxDailyPackages
          AND c.maxWeightKg >= :weightKg
          AND c.maxVolumeM3 >= :volumeM3
    """)
    fun findAvailableCapacities(
        @Param("serviceId") serviceId: Long,
        @Param("zone") zone: ZoneType,
        @Param("weightKg") weightKg: Double,
        @Param("volumeM3") volumeM3: Double
    ): List<FleetCapacity>
}
