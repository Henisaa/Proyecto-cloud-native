package com.rutaexpress.catalog.viewmodel.service

import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.viewmodel.request.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogViewModelServiceTests {

    @Autowired
    private lateinit var viewModelService: CatalogViewModelService

    @Test
    fun `should create and retrieve a new catalog service`() {
        val request = CreateServiceViewModel(
            code = "SRV-TEST-01",
            name = "Test Courier Express",
            description = "Servicio rápido de prueba",
            category = ServiceCategory.EXPRESS,
            estimatedDeliveryHours = 12,
            basePrice = 3000.0,
            pricePerKm = 250.0,
            pricePerKg = 100.0
        )

        val created = viewModelService.createService(request)

        assertNotNull(created.id)
        assertEquals("SRV-TEST-01", created.code)
        assertEquals("Test Courier Express", created.name)
        assertEquals(ServiceStatus.ACTIVE, created.status)
        assertTrue(created.active)

        val retrieved = viewModelService.getServiceById(created.id)
        assertEquals("SRV-TEST-01", retrieved.code)
        assertEquals(12, retrieved.estimatedDeliveryHours)
    }

    @Test
    fun `should fail when creating service with duplicate code`() {
        val request = CreateServiceViewModel(
            code = "SRV-DUP-01",
            name = "Duplicate Service",
            category = ServiceCategory.STANDARD
        )

        viewModelService.createService(request)

        assertThrows(IllegalArgumentException::class.java) {
            viewModelService.createService(request)
        }
    }

    @Test
    fun `should add and update fleet capacity correctly`() {
        val service = viewModelService.createService(
            CreateServiceViewModel(
                code = "SRV-CAP-01",
                name = "Capacity Test Service",
                category = ServiceCategory.SAME_DAY
            )
        )

        val capacityRequest = CreateCapacityViewModel(
            zone = ZoneType.URBAN_CENTER,
            vehicleType = VehicleType.MOTORCYCLE,
            maxDailyPackages = 50,
            maxWeightKg = 20.0,
            maxVolumeM3 = 0.1
        )

        val capacity = viewModelService.addCapacity(service.id, capacityRequest)

        assertEquals(50, capacity.maxDailyPackages)
        assertEquals(0, capacity.currentBookedPackages)
        assertEquals(50, capacity.availablePackages)
        assertFalse(capacity.isFull)

        // Update capacity booked packages
        val updatedCapacity = viewModelService.updateCapacity(
            service.id,
            capacity.id,
            UpdateCapacityViewModel(currentBookedPackages = 25)
        )

        assertEquals(25, updatedCapacity.currentBookedPackages)
        assertEquals(25, updatedCapacity.availablePackages)
        assertEquals(50.0, updatedCapacity.utilizationPercentage)
    }

    @Test
    fun `should check capacity availability for shipments service`() {
        val service = viewModelService.createService(
            CreateServiceViewModel(
                code = "SRV-CHK-01",
                name = "Shipments Check Service",
                category = ServiceCategory.EXPRESS
            )
        )

        viewModelService.addCapacity(
            service.id,
            CreateCapacityViewModel(
                zone = ZoneType.URBAN_CENTER,
                vehicleType = VehicleType.VAN,
                maxDailyPackages = 30,
                maxWeightKg = 500.0,
                maxVolumeM3 = 4.0
            )
        )

        // Check availability within limits
        val checkOk = viewModelService.checkCapacity(
            service.id,
            CapacityCheckRequestViewModel(
                zone = ZoneType.URBAN_CENTER,
                weightKg = 15.0,
                volumeM3 = 0.2,
                vehicleType = VehicleType.VAN,
                packagesCount = 2
            )
        )

        assertTrue(checkOk.available)
        assertEquals(VehicleType.VAN, checkOk.vehicleTypeSelected)
        assertEquals(30, checkOk.remainingSlots)

        // Check availability exceeding weight limit
        val checkFailWeight = viewModelService.checkCapacity(
            service.id,
            CapacityCheckRequestViewModel(
                zone = ZoneType.URBAN_CENTER,
                weightKg = 600.0,
                volumeM3 = 0.2,
                vehicleType = VehicleType.VAN,
                packagesCount = 1
            )
        )

        assertFalse(checkFailWeight.available)
    }

    @Test
    fun `should reserve and release capacity slot`() {
        val service = viewModelService.createService(
            CreateServiceViewModel(
                code = "SRV-RES-01",
                name = "Reservation Service",
                category = ServiceCategory.STANDARD
            )
        )

        viewModelService.addCapacity(
            service.id,
            CreateCapacityViewModel(
                zone = ZoneType.URBAN_PERIPHERY,
                vehicleType = VehicleType.MOTORCYCLE,
                maxDailyPackages = 10,
                maxWeightKg = 25.0,
                maxVolumeM3 = 0.15
            )
        )

        // Reserve 2 packages
        val reservation = viewModelService.reserveCapacity(
            service.id,
            CapacityReservationViewModel(
                zone = ZoneType.URBAN_PERIPHERY,
                vehicleType = VehicleType.MOTORCYCLE,
                packagesCount = 2,
                weightKg = 5.0,
                volumeM3 = 0.02
            )
        )

        assertTrue(reservation.success)
        assertEquals(8, reservation.remainingCapacity)

        // Release 1 package
        val release = viewModelService.releaseCapacity(
            service.id,
            CapacityReleaseViewModel(
                zone = ZoneType.URBAN_PERIPHERY,
                vehicleType = VehicleType.MOTORCYCLE,
                packagesCount = 1
            )
        )

        assertTrue(release.success)
        assertEquals(9, release.remainingCapacity)
    }

    @Test
    fun `should configure and retrieve tariffs for a service`() {
        val service = viewModelService.createService(
            CreateServiceViewModel(
                code = "SRV-TAR-01",
                name = "Tariff Service",
                category = ServiceCategory.EXPRESS
            )
        )

        val tariffRequest = CreateTariffViewModel(
            originZone = ZoneType.URBAN_CENTER,
            destinationZone = ZoneType.URBAN_PERIPHERY,
            baseFare = 5000.0,
            perKmRate = 350.0,
            perKgRate = 120.0,
            minFare = 5000.0
        )

        val tariff = viewModelService.addTariff(service.id, tariffRequest)

        assertEquals(5000.0, tariff.baseFare)
        assertEquals(350.0, tariff.perKmRate)
        assertEquals(120.0, tariff.perKgRate)
        assertTrue(tariff.active)

        val tariffsList = viewModelService.getTariffs(service.id)
        assertEquals(1, tariffsList.size)
        assertEquals(ZoneType.URBAN_CENTER, tariffsList[0].originZone)
    }
}
