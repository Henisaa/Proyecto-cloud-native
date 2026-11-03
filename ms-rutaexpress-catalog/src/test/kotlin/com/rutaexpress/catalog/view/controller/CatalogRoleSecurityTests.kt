package com.rutaexpress.catalog.view.controller

import com.rutaexpress.catalog.model.entity.CatalogService
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.model.repository.CatalogServiceRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.security.enabled=true"])
class CatalogRoleSecurityTests {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var repository: CatalogServiceRepository
    @MockitoBean private lateinit var jwtDecoder: JwtDecoder
    private var seededServiceId = 0L

    @BeforeEach
    fun seed() {
        val service = repository.findByCode("ROLE-TEST-01").orElseGet {
            val fresh = CatalogService(code="ROLE-TEST-01", name="Servicio", category=ServiceCategory.SAME_DAY,
                status=ServiceStatus.ACTIVE, estimatedDeliveryHours=6, basePrice=1000.0, pricePerKm=10.0, pricePerKg=10.0, active=true)
            fresh.addCapacity(FleetCapacity(zone=ZoneType.URBAN_CENTER, vehicleType=VehicleType.VAN,
                maxDailyPackages=20, currentBookedPackages=2, maxWeightKg=1000.0, maxVolumeM3=100.0, active=true))
            repository.save(fresh)
        }
        seededServiceId = requireNotNull(service.id)
    }
    @Test
    @WithMockUser(roles = ["CLIENTE"])
    fun `cliente selecciona servicios sin obtener capacidades por ninguna proyeccion`() {
        mockMvc.perform(get("/api/catalog/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].availableCapacitiesCount").doesNotExist())
        for (path in listOf("/api/catalog/services/$seededServiceId", "/api/catalog/services/code/ROLE-TEST-01")) {
            mockMvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.capacities").doesNotExist())
                .andExpect(jsonPath("$.data.totalDailyCapacity").doesNotExist())
                .andExpect(jsonPath("$.data.totalBookedPackages").doesNotExist())
                .andExpect(jsonPath("$.data.overallUtilizationPercentage").doesNotExist())
        }
        mockMvc.perform(get("/api/catalog/services/$seededServiceId/capacities")).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["AUDITOR"])
    fun `auditor no consulta catalogo`() {
        mockMvc.perform(get("/api/catalog/services")).andExpect(status().isForbidden)
    }

    @Test
    fun `catalogo rechaza anonimos`() {
        mockMvc.perform(get("/api/catalog/services")).andExpect(status().isUnauthorized)
    }
}
