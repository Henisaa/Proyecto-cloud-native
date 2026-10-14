package com.rutaexpress.catalog.viewmodel.service

import com.rutaexpress.catalog.event.CatalogEventPublisher
import com.rutaexpress.catalog.model.entity.CatalogService
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.entity.Tariff
import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.CatalogServiceRepository
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.TariffRepository
import com.rutaexpress.catalog.viewmodel.request.*
import com.rutaexpress.catalog.viewmodel.response.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CatalogViewModelService(
    private val serviceRepository: CatalogServiceRepository,
    private val capacityRepository: FleetCapacityRepository,
    private val tariffRepository: TariffRepository,
    private val eventPublisher: CatalogEventPublisher
) {

    @Transactional(readOnly = true)
    @Cacheable(value = ["catalog_services"], key = "'list_' + #category + '_' + #status + '_' + #active")
    fun getAllServices(
        category: ServiceCategory?,
        status: ServiceStatus?,
        active: Boolean?
    ): List<ServiceSummaryViewModel> {
        val services = serviceRepository.searchServices(category, status, active)
        return services.map { toSummaryViewModel(it) }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ["catalog_service_details"], key = "#id")
    fun getServiceById(id: Long): ServiceDetailViewModel {
        val service = serviceRepository.findByIdWithDetails(id)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $id") }
        return toDetailViewModel(service)
    }

    @Transactional(readOnly = true)
    fun getServiceByCode(code: String): ServiceDetailViewModel {
        val service = serviceRepository.findByCode(code)
            .orElseThrow { NoSuchElementException("Catalog service not found with code: $code") }
        return toDetailViewModel(service)
    }

    @CacheEvict(value = ["catalog_services", "catalog_service_details"], allEntries = true)
    fun createService(request: CreateServiceViewModel): ServiceDetailViewModel {
        if (serviceRepository.existsByCode(request.code.trim().uppercase())) {
            throw IllegalArgumentException("A service with code '${request.code}' already exists")
        }

        val newService = CatalogService(
            code = request.code.trim().uppercase(),
            name = request.name.trim(),
            description = request.description?.trim(),
            category = request.category,
            status = ServiceStatus.ACTIVE,
            estimatedDeliveryHours = request.estimatedDeliveryHours,
            basePrice = request.basePrice,
            pricePerKm = request.pricePerKm,
            pricePerKg = request.pricePerKg,
            active = true
        )

        val saved = serviceRepository.save(newService)

        // Publish Kafka event
        eventPublisher.publishServiceCreated(
            serviceId = saved.id!!,
            code = saved.code,
            name = saved.name,
            category = saved.category.name,
            basePrice = saved.basePrice,
            estimatedHours = saved.estimatedDeliveryHours
        )

        return toDetailViewModel(saved)
    }

    @CacheEvict(value = ["catalog_services", "catalog_service_details"], allEntries = true)
    fun updateService(id: Long, request: UpdateServiceViewModel): ServiceDetailViewModel {
        val service = serviceRepository.findByIdWithDetails(id)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $id") }

        request.name?.let { service.name = it.trim() }
        request.description?.let { service.description = it.trim() }
        request.category?.let { service.category = it }
        request.estimatedDeliveryHours?.let { service.estimatedDeliveryHours = it }
        request.basePrice?.let { service.basePrice = it }
        request.pricePerKm?.let { service.pricePerKm = it }
        request.pricePerKg?.let { service.pricePerKg = it }
        request.status?.let { service.status = it }
        request.active?.let { service.active = it }

        val updated = serviceRepository.save(service)

        eventPublisher.publishServiceUpdated(
            serviceId = updated.id!!,
            code = updated.code,
            status = updated.status.name,
            active = updated.active
        )

        return toDetailViewModel(updated)
    }

    @CacheEvict(value = ["catalog_services", "catalog_service_details"], allEntries = true)
    fun updateServiceStatus(id: Long, request: UpdateStatusViewModel): ServiceDetailViewModel {
        val service = serviceRepository.findByIdWithDetails(id)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $id") }

        service.status = request.status
        request.active?.let { service.active = it }

        val updated = serviceRepository.save(service)

        eventPublisher.publishServiceUpdated(
            serviceId = updated.id!!,
            code = updated.code,
            status = updated.status.name,
            active = updated.active
        )

        return toDetailViewModel(updated)
    }

    @CacheEvict(value = ["catalog_services", "catalog_service_details"], allEntries = true)
    fun deleteService(id: Long) {
        val service = serviceRepository.findById(id)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $id") }

        service.active = false
        service.status = ServiceStatus.INACTIVE
        serviceRepository.save(service)

        eventPublisher.publishServiceUpdated(
            serviceId = service.id!!,
            code = service.code,
            status = ServiceStatus.INACTIVE.name,
            active = false
        )
    }

    // --- Capacity Management ---

    @Transactional(readOnly = true)
    fun getCapacities(serviceId: Long): List<CapacityDetailViewModel> {
        return capacityRepository.findByServiceId(serviceId)
            .map { toCapacityViewModel(it) }
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun addCapacity(serviceId: Long, request: CreateCapacityViewModel): CapacityDetailViewModel {
        val service = serviceRepository.findById(serviceId)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $serviceId") }

        val existing = capacityRepository.findByServiceIdAndZoneAndVehicleType(
            serviceId, request.zone, request.vehicleType
        )
        if (existing.isPresent) {
            throw IllegalArgumentException("Capacity for vehicle type '${request.vehicleType}' in zone '${request.zone}' already exists")
        }

        val capacity = FleetCapacity(
            service = service,
            zone = request.zone,
            vehicleType = request.vehicleType,
            maxDailyPackages = request.maxDailyPackages,
            currentBookedPackages = 0,
            maxWeightKg = request.maxWeightKg,
            maxVolumeM3 = request.maxVolumeM3,
            active = true
        )

        val saved = capacityRepository.save(capacity)

        eventPublisher.publishCapacityUpdated(
            serviceId = serviceId,
            capacityId = saved.id!!,
            zone = saved.zone.name,
            vehicleType = saved.vehicleType.name,
            maxDailyPackages = saved.maxDailyPackages,
            currentBookedPackages = saved.currentBookedPackages,
            availablePackages = saved.availablePackages
        )

        return toCapacityViewModel(saved)
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun updateCapacity(serviceId: Long, capacityId: Long, request: UpdateCapacityViewModel): CapacityDetailViewModel {
        val capacity = capacityRepository.findById(capacityId)
            .orElseThrow { NoSuchElementException("Capacity not found with ID: $capacityId") }

        if (capacity.service?.id != serviceId) {
            throw IllegalArgumentException("Capacity $capacityId does not belong to service $serviceId")
        }

        request.maxDailyPackages?.let { capacity.maxDailyPackages = it }
        request.currentBookedPackages?.let {
            if (it > capacity.maxDailyPackages) {
                throw IllegalArgumentException("Current booked packages cannot exceed max daily packages")
            }
            capacity.currentBookedPackages = it
        }
        request.maxWeightKg?.let { capacity.maxWeightKg = it }
        request.maxVolumeM3?.let { capacity.maxVolumeM3 = it }
        request.active?.let { capacity.active = it }

        val updated = capacityRepository.save(capacity)

        eventPublisher.publishCapacityUpdated(
            serviceId = serviceId,
            capacityId = updated.id!!,
            zone = updated.zone.name,
            vehicleType = updated.vehicleType.name,
            maxDailyPackages = updated.maxDailyPackages,
            currentBookedPackages = updated.currentBookedPackages,
            availablePackages = updated.availablePackages
        )

        return toCapacityViewModel(updated)
    }

    // --- Capacity Validation & Reservation for ms-rutaexpress-shipments ---

    @Transactional(readOnly = true)
    fun checkCapacity(serviceId: Long, request: CapacityCheckRequestViewModel): CapacityCheckResultViewModel {
        val service = serviceRepository.findById(serviceId)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $serviceId") }

        if (!service.active || service.status != ServiceStatus.ACTIVE) {
            return CapacityCheckResultViewModel(
                serviceId = serviceId,
                serviceCode = service.code,
                zone = request.zone,
                available = false,
                vehicleTypeSelected = null,
                remainingSlots = 0,
                maxWeightAllowedKg = 0.0,
                maxVolumeAllowedM3 = 0.0,
                message = "Service is inactive or suspended"
            )
        }

        val availableCapacities = capacityRepository.findAvailableCapacities(
            serviceId = serviceId,
            zone = request.zone,
            weightKg = request.weightKg,
            volumeM3 = request.volumeM3
        )

        val candidate = if (request.vehicleType != null) {
            availableCapacities.find { it.vehicleType == request.vehicleType }
        } else {
            availableCapacities.firstOrNull()
        }

        return if (candidate != null && candidate.availablePackages >= request.packagesCount) {
            CapacityCheckResultViewModel(
                serviceId = serviceId,
                serviceCode = service.code,
                zone = request.zone,
                available = true,
                vehicleTypeSelected = candidate.vehicleType,
                remainingSlots = candidate.availablePackages,
                maxWeightAllowedKg = candidate.maxWeightKg,
                maxVolumeAllowedM3 = candidate.maxVolumeM3,
                message = "Capacity available for dispatch"
            )
        } else {
            CapacityCheckResultViewModel(
                serviceId = serviceId,
                serviceCode = service.code,
                zone = request.zone,
                available = false,
                vehicleTypeSelected = null,
                remainingSlots = 0,
                maxWeightAllowedKg = 0.0,
                maxVolumeAllowedM3 = 0.0,
                message = "No available capacity found matching vehicle, weight, volume or slot requirements"
            )
        }
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun reserveCapacity(serviceId: Long, request: CapacityReservationViewModel): CapacityReservationResultViewModel {
        val capacity = capacityRepository.findByServiceIdAndZoneAndVehicleType(
            serviceId, request.zone, request.vehicleType
        ).orElseThrow {
            NoSuchElementException("No fleet capacity configured for service $serviceId in zone ${request.zone} with vehicle ${request.vehicleType}")
        }

        if (!capacity.active) {
            throw IllegalStateException("Capacity line is inactive")
        }

        if (capacity.availablePackages < request.packagesCount) {
            throw IllegalStateException("Insufficient capacity: only ${capacity.availablePackages} slots available, requested ${request.packagesCount}")
        }

        if (request.weightKg > capacity.maxWeightKg) {
            throw IllegalStateException("Package weight ${request.weightKg} kg exceeds vehicle max allowed ${capacity.maxWeightKg} kg")
        }

        if (request.volumeM3 > capacity.maxVolumeM3) {
            throw IllegalStateException("Package volume ${request.volumeM3} m3 exceeds vehicle max allowed ${capacity.maxVolumeM3} m3")
        }

        capacity.currentBookedPackages += request.packagesCount
        val updated = capacityRepository.save(capacity)

        eventPublisher.publishCapacityUpdated(
            serviceId = serviceId,
            capacityId = updated.id!!,
            zone = updated.zone.name,
            vehicleType = updated.vehicleType.name,
            maxDailyPackages = updated.maxDailyPackages,
            currentBookedPackages = updated.currentBookedPackages,
            availablePackages = updated.availablePackages
        )

        return CapacityReservationResultViewModel(
            serviceId = serviceId,
            capacityId = updated.id!!,
            zone = updated.zone,
            vehicleType = updated.vehicleType,
            packagesReserved = request.packagesCount,
            remainingCapacity = updated.availablePackages,
            success = true,
            message = "Capacity slot reserved successfully"
        )
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun releaseCapacity(serviceId: Long, request: CapacityReleaseViewModel): CapacityReservationResultViewModel {
        val capacity = capacityRepository.findByServiceIdAndZoneAndVehicleType(
            serviceId, request.zone, request.vehicleType
        ).orElseThrow {
            NoSuchElementException("No fleet capacity found for service $serviceId in zone ${request.zone} with vehicle ${request.vehicleType}")
        }

        capacity.currentBookedPackages = (capacity.currentBookedPackages - request.packagesCount).coerceAtLeast(0)
        val updated = capacityRepository.save(capacity)

        eventPublisher.publishCapacityUpdated(
            serviceId = serviceId,
            capacityId = updated.id!!,
            zone = updated.zone.name,
            vehicleType = updated.vehicleType.name,
            maxDailyPackages = updated.maxDailyPackages,
            currentBookedPackages = updated.currentBookedPackages,
            availablePackages = updated.availablePackages
        )

        return CapacityReservationResultViewModel(
            serviceId = serviceId,
            capacityId = updated.id!!,
            zone = updated.zone,
            vehicleType = updated.vehicleType,
            packagesReserved = -request.packagesCount,
            remainingCapacity = updated.availablePackages,
            success = true,
            message = "Capacity slot released successfully"
        )
    }

    // --- Tariff Management ---

    @Transactional(readOnly = true)
    fun getTariffs(serviceId: Long): List<TariffDetailViewModel> {
        return tariffRepository.findByServiceId(serviceId)
            .map { toTariffViewModel(it) }
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun addTariff(serviceId: Long, request: CreateTariffViewModel): TariffDetailViewModel {
        val service = serviceRepository.findById(serviceId)
            .orElseThrow { NoSuchElementException("Catalog service not found with ID: $serviceId") }

        val existing = tariffRepository.findByServiceIdAndOriginZoneAndDestinationZone(
            serviceId, request.originZone, request.destinationZone
        )
        if (existing.isPresent) {
            throw IllegalArgumentException("Tariff between origin '${request.originZone}' and destination '${request.destinationZone}' already exists")
        }

        val tariff = Tariff(
            service = service,
            originZone = request.originZone,
            destinationZone = request.destinationZone,
            baseFare = request.baseFare,
            perKmRate = request.perKmRate,
            perKgRate = request.perKgRate,
            minFare = request.minFare,
            active = true
        )

        val saved = tariffRepository.save(tariff)

        eventPublisher.publishTariffUpdated(
            serviceId = serviceId,
            tariffId = saved.id!!,
            originZone = saved.originZone.name,
            destinationZone = saved.destinationZone.name,
            baseFare = saved.baseFare,
            perKmRate = saved.perKmRate,
            perKgRate = saved.perKgRate,
            minFare = saved.minFare
        )

        return toTariffViewModel(saved)
    }

    @CacheEvict(value = ["catalog_service_details"], key = "#serviceId")
    fun updateTariff(serviceId: Long, tariffId: Long, request: UpdateTariffViewModel): TariffDetailViewModel {
        val tariff = tariffRepository.findById(tariffId)
            .orElseThrow { NoSuchElementException("Tariff not found with ID: $tariffId") }

        if (tariff.service?.id != serviceId) {
            throw IllegalArgumentException("Tariff $tariffId does not belong to service $serviceId")
        }

        request.baseFare?.let { tariff.baseFare = it }
        request.perKmRate?.let { tariff.perKmRate = it }
        request.perKgRate?.let { tariff.perKgRate = it }
        request.minFare?.let { tariff.minFare = it }
        request.active?.let { tariff.active = it }

        val updated = tariffRepository.save(tariff)

        eventPublisher.publishTariffUpdated(
            serviceId = serviceId,
            tariffId = updated.id!!,
            originZone = updated.originZone.name,
            destinationZone = updated.destinationZone.name,
            baseFare = updated.baseFare,
            perKmRate = updated.perKmRate,
            perKgRate = updated.perKgRate,
            minFare = updated.minFare
        )

        return toTariffViewModel(updated)
    }

    // --- Mappings between Model and ViewModels ---

    private fun toDetailViewModel(service: CatalogService): ServiceDetailViewModel {
        val capacityVms = service.capacities.map { toCapacityViewModel(it) }
        val tariffVms = service.tariffs.map { toTariffViewModel(it) }

        val totalDaily = capacityVms.sumOf { it.maxDailyPackages }
        val totalBooked = capacityVms.sumOf { it.currentBookedPackages }
        val overallUtilization = if (totalDaily > 0) {
            (totalBooked.toDouble() / totalDaily.toDouble()) * 100.0
        } else 0.0

        return ServiceDetailViewModel(
            id = service.id ?: 0L,
            code = service.code,
            name = service.name,
            description = service.description,
            category = service.category,
            status = service.status,
            estimatedDeliveryHours = service.estimatedDeliveryHours,
            basePrice = service.basePrice,
            pricePerKm = service.pricePerKm,
            pricePerKg = service.pricePerKg,
            active = service.active,
            isAvailable = service.active && service.status == ServiceStatus.ACTIVE && (totalDaily == 0 || totalBooked < totalDaily),
            totalDailyCapacity = totalDaily,
            totalBookedPackages = totalBooked,
            overallUtilizationPercentage = overallUtilization,
            capacities = capacityVms,
            tariffs = tariffVms,
            createdAt = service.createdAt,
            updatedAt = service.updatedAt
        )
    }

    private fun toSummaryViewModel(service: CatalogService): ServiceSummaryViewModel {
        return ServiceSummaryViewModel(
            id = service.id ?: 0L,
            code = service.code,
            name = service.name,
            category = service.category,
            status = service.status,
            estimatedDeliveryHours = service.estimatedDeliveryHours,
            basePrice = service.basePrice,
            active = service.active,
            availableCapacitiesCount = service.capacities.count { it.active && !it.isFull }
        )
    }

    private fun toCapacityViewModel(capacity: FleetCapacity): CapacityDetailViewModel {
        return CapacityDetailViewModel(
            id = capacity.id ?: 0L,
            zone = capacity.zone,
            vehicleType = capacity.vehicleType,
            maxDailyPackages = capacity.maxDailyPackages,
            currentBookedPackages = capacity.currentBookedPackages,
            availablePackages = capacity.availablePackages,
            utilizationPercentage = capacity.utilizationPercentage,
            isFull = capacity.isFull,
            maxWeightKg = capacity.maxWeightKg,
            maxVolumeM3 = capacity.maxVolumeM3,
            active = capacity.active,
            updatedAt = capacity.updatedAt
        )
    }

    private fun toTariffViewModel(tariff: Tariff): TariffDetailViewModel {
        return TariffDetailViewModel(
            id = tariff.id ?: 0L,
            originZone = tariff.originZone,
            destinationZone = tariff.destinationZone,
            baseFare = tariff.baseFare,
            perKmRate = tariff.perKmRate,
            perKgRate = tariff.perKgRate,
            minFare = tariff.minFare,
            active = tariff.active,
            updatedAt = tariff.updatedAt
        )
    }
}
