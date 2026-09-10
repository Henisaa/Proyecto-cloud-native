package com.rutaexpress.catalog.view.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.rutaexpress.catalog.model.entity.CatalogService
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.CatalogServiceRepository
import com.rutaexpress.catalog.viewmodel.request.CapacityCheckRequestViewModel
import com.rutaexpress.catalog.viewmodel.request.CreateServiceViewModel
import com.rutaexpress.catalog.viewmodel.request.UpdateStatusViewModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

private val testMapper = ObjectMapper().findAndRegisterModules()

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogViewControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var serviceRepository: CatalogServiceRepository

    private var seededServiceId: Long = 1L

    @BeforeEach
    fun setUp() {
        val existing = serviceRepository.findByCode("SRV-EXP-01")
        if (existing.isPresent) {
            seededServiceId = existing.get().id!!
        } else {
            val service = CatalogService(
                code = "SRV-EXP-01",
                name = "RutaExpress Mismo Día",
                description = "Entrega urgente express garantizada",
                category = ServiceCategory.SAME_DAY,
                status = ServiceStatus.ACTIVE,
                estimatedDeliveryHours = 6,
                basePrice = 4500.0,
                pricePerKm = 350.0,
                pricePerKg = 150.0,
                active = true
            )
            service.addCapacity(
                FleetCapacity(
                    zone = ZoneType.URBAN_CENTER,
                    vehicleType = VehicleType.MOTORCYCLE,
                    maxDailyPackages = 60,
                    currentBookedPackages = 15,
                    maxWeightKg = 15.0,
                    maxVolumeM3 = 0.08,
                    active = true
                )
            )
            val saved = serviceRepository.save(service)
            seededServiceId = saved.id!!
        }
    }

    @Test
    @WithMockUser(roles = ["OPERADOR"])
    fun `GET services should return 200 and list of services`() {
        mockMvc.perform(get("/api/catalog/services"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
    }

    @Test
    @WithMockUser(roles = ["CLIENTE"])
    fun `GET service by id should return 200 for seeded service with CLIENTE role`() {
        mockMvc.perform(get("/api/catalog/services/$seededServiceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("SRV-EXP-01"))
            .andExpect(jsonPath("$.data.category").value("SAME_DAY"))
    }

    @Test
    @WithMockUser(roles = ["OPERADOR"])
    fun `GET service by unknown id should return 404`() {
        mockMvc.perform(get("/api/catalog/services/99999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST service with valid body should return 201 Created for ADMIN role`() {
        val request = CreateServiceViewModel(
            code = "SRV-POST-01",
            name = "Nuevo Courier Express",
            description = "Servicio añadido vía endpoint REST",
            category = ServiceCategory.EXPRESS,
            estimatedDeliveryHours = 8,
            basePrice = 4000.0,
            pricePerKm = 300.0,
            pricePerKg = 150.0
        )

        mockMvc.perform(
            post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("SRV-POST-01"))
            .andExpect(jsonPath("$.data.name").value("Nuevo Courier Express"))
    }

    @Test
    @WithMockUser(roles = ["CLIENTE"])
    fun `POST service should return 403 Forbidden for CLIENTE role`() {
        val request = CreateServiceViewModel(
            code = "SRV-FORBIDDEN",
            name = "Unauthorized Service Creation",
            category = ServiceCategory.EXPRESS
        )

        mockMvc.perform(
            post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["OPERADOR"])
    fun `POST service with invalid blank fields should return 400 Bad Request`() {
        val invalidRequest = CreateServiceViewModel(
            code = "",
            name = "",
            category = ServiceCategory.STANDARD
        )

        mockMvc.perform(
            post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors").isMap)
    }

    @Test
    @WithMockUser(roles = ["OPERADOR"])
    fun `PATCH service status should update operative status`() {
        val statusUpdate = UpdateStatusViewModel(
            status = ServiceStatus.SUSPENDED
        )

        mockMvc.perform(
            patch("/api/catalog/services/$seededServiceId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testMapper.writeValueAsString(statusUpdate))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
    }

    @Test
    @WithMockUser(roles = ["OPERADOR"])
    fun `POST capacity check should respond with availability calculation`() {
        val checkRequest = CapacityCheckRequestViewModel(
            zone = ZoneType.URBAN_CENTER,
            weightKg = 5.0,
            volumeM3 = 0.05,
            vehicleType = VehicleType.MOTORCYCLE,
            packagesCount = 1
        )

        mockMvc.perform(
            post("/api/catalog/services/$seededServiceId/capacity/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testMapper.writeValueAsString(checkRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.serviceCode").value("SRV-EXP-01"))
            .andExpect(jsonPath("$.data.available").value(true))
    }
}
