package com.rutaexpress.catalog.view.controller

import com.rutaexpress.catalog.model.enums.ServiceCategory
import com.rutaexpress.catalog.model.enums.ServiceStatus
import com.rutaexpress.catalog.viewmodel.request.*
import com.rutaexpress.catalog.viewmodel.response.*
import com.rutaexpress.catalog.viewmodel.service.CatalogViewModelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog Microservice", description = "Servicios, tarifas y capacidad de flota para RutaExpress")
@SecurityRequirement(name = "BearerAuth")
class CatalogViewController(
    private val viewModelService: CatalogViewModelService
) {

    // ----------------------------------------------------
    // Endpoints de Servicios (/api/catalog/services)
    // ----------------------------------------------------

    @GetMapping("/services")
    @Operation(summary = "Listar catálogo de servicios", description = "Obtiene los servicios disponibles con filtros opcionales por categoría, estado y activo.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Lista de servicios recuperada exitosamente"),
        ApiResponse(responseCode = "401", description = "No autenticado"),
        ApiResponse(responseCode = "403", description = "Acceso denegado")
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun listServices(
        @RequestParam(required = false) category: ServiceCategory?,
        @RequestParam(required = false) status: ServiceStatus?,
        @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<ApiResponseViewModel<List<ServiceSummaryViewModel>>> {
        val services = viewModelService.getAllServices(category, status, active)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Services retrieved successfully",
                data = services
            )
        )
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "Obtener detalle de un servicio", description = "Obtiene la ficha técnica completa de un servicio incluyendo capacidades de flota y tarifas.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Servicio encontrado"),
        ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun getServiceById(@PathVariable id: Long): ResponseEntity<ApiResponseViewModel<ServiceDetailViewModel>> {
        val service = viewModelService.getServiceById(id)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Service retrieved successfully",
                data = service
            )
        )
    }

    @GetMapping("/services/code/{code}")
    @Operation(summary = "Obtener servicio por código", description = "Búsqueda de servicio por código único de negocio (ej. SRV-EXP-01).")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun getServiceByCode(@PathVariable code: String): ResponseEntity<ApiResponseViewModel<ServiceDetailViewModel>> {
        val service = viewModelService.getServiceByCode(code)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Service retrieved successfully",
                data = service
            )
        )
    }

    @PostMapping("/services")
    @Operation(summary = "Crear nuevo servicio de catálogo", description = "Registra un nuevo servicio de courier / despacho en el catálogo.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Servicio creado exitosamente"),
        ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        ApiResponse(responseCode = "409", description = "Código de servicio ya existente")
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun createService(
        @Valid @RequestBody request: CreateServiceViewModel
    ): ResponseEntity<ApiResponseViewModel<ServiceDetailViewModel>> {
        val created = viewModelService.createService(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponseViewModel(
                message = "Service created successfully",
                data = created
            )
        )
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Actualizar servicio", description = "Actualiza los datos comerciales, precios base y tiempos del servicio.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Servicio actualizado exitosamente"),
        ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun updateService(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateServiceViewModel
    ): ResponseEntity<ApiResponseViewModel<ServiceDetailViewModel>> {
        val updated = viewModelService.updateService(id, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Service updated successfully",
                data = updated
            )
        )
    }

    @PatchMapping("/services/{id}/status")
    @Operation(summary = "Cambiar estado del servicio", description = "Actualiza el estado operativo (ACTIVO, INACTIVO, SUSPENDIDO).")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun updateStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStatusViewModel
    ): ResponseEntity<ApiResponseViewModel<ServiceDetailViewModel>> {
        val updated = viewModelService.updateServiceStatus(id, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Service status updated successfully",
                data = updated
            )
        )
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "Desactivar servicio (baja lógica)", description = "Pasa el servicio a inactivo y suspende su oferta comercial.")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteService(@PathVariable id: Long): ResponseEntity<ApiResponseViewModel<Unit>> {
        viewModelService.deleteService(id)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Service deactivated successfully",
                data = null
            )
        )
    }

    // ----------------------------------------------------
    // Endpoints de Capacidad de Flota (/capacities)
    // ----------------------------------------------------

    @GetMapping("/services/{id}/capacities")
    @Operation(summary = "Listar capacidades de flota para un servicio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun getCapacities(@PathVariable id: Long): ResponseEntity<ApiResponseViewModel<List<CapacityDetailViewModel>>> {
        val capacities = viewModelService.getCapacities(id)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Capacities retrieved successfully",
                data = capacities
            )
        )
    }

    @PostMapping("/services/{id}/capacities")
    @Operation(summary = "Agregar capacidad de flota a un servicio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun addCapacity(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateCapacityViewModel
    ): ResponseEntity<ApiResponseViewModel<CapacityDetailViewModel>> {
        val capacity = viewModelService.addCapacity(id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponseViewModel(
                message = "Capacity added successfully",
                data = capacity
            )
        )
    }

    @PutMapping("/services/{id}/capacities/{capacityId}")
    @Operation(summary = "Actualizar capacidad de flota")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun updateCapacity(
        @PathVariable id: Long,
        @PathVariable capacityId: Long,
        @Valid @RequestBody request: UpdateCapacityViewModel
    ): ResponseEntity<ApiResponseViewModel<CapacityDetailViewModel>> {
        val capacity = viewModelService.updateCapacity(id, capacityId, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Capacity updated successfully",
                data = capacity
            )
        )
    }

    // ----------------------------------------------------
    // Conexión con Envíos (Shipments): Consulta & Reserva de Capacidad
    // ----------------------------------------------------

    @PostMapping("/services/{id}/capacity/check")
    @Operation(
        summary = "Verificar disponibilidad de capacidad (para ms-rutaexpress-shipments)",
        description = "Consulta si existe flota disponible para transportar paquetes con peso y volumen determinados en la zona."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun checkCapacity(
        @PathVariable id: Long,
        @Valid @RequestBody request: CapacityCheckRequestViewModel
    ): ResponseEntity<ApiResponseViewModel<CapacityCheckResultViewModel>> {
        val result = viewModelService.checkCapacity(id, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = if (result.available) "Capacity is available" else "No capacity available",
                data = result
            )
        )
    }

    @PostMapping("/services/{id}/capacity/reserve")
    @Operation(
        summary = "Reservar cupo de capacidad para un envío",
        description = "Bloquea o incrementa la capacidad ocupada al crear un envío en ms-rutaexpress-shipments."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun reserveCapacity(
        @PathVariable id: Long,
        @Valid @RequestBody request: CapacityReservationViewModel
    ): ResponseEntity<ApiResponseViewModel<CapacityReservationResultViewModel>> {
        val result = viewModelService.reserveCapacity(id, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = result.message,
                data = result
            )
        )
    }

    @PostMapping("/services/{id}/capacity/release")
    @Operation(
        summary = "Liberar cupo de capacidad de un envío cancelado",
        description = "Disminuye la capacidad ocupada cuando un envío es cancelado."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun releaseCapacity(
        @PathVariable id: Long,
        @Valid @RequestBody request: CapacityReleaseViewModel
    ): ResponseEntity<ApiResponseViewModel<CapacityReservationResultViewModel>> {
        val result = viewModelService.releaseCapacity(id, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = result.message,
                data = result
            )
        )
    }

    // ----------------------------------------------------
    // Endpoints de Tarifas (/tariffs)
    // ----------------------------------------------------

    @GetMapping("/services/{id}/tariffs")
    @Operation(summary = "Listar tarifas de un servicio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    fun getTariffs(@PathVariable id: Long): ResponseEntity<ApiResponseViewModel<List<TariffDetailViewModel>>> {
        val tariffs = viewModelService.getTariffs(id)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Tariffs retrieved successfully",
                data = tariffs
            )
        )
    }

    @PostMapping("/services/{id}/tariffs")
    @Operation(summary = "Configurar nueva tarifa zonal para un servicio")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun addTariff(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateTariffViewModel
    ): ResponseEntity<ApiResponseViewModel<TariffDetailViewModel>> {
        val tariff = viewModelService.addTariff(id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponseViewModel(
                message = "Tariff configured successfully",
                data = tariff
            )
        )
    }

    @PutMapping("/services/{id}/tariffs/{tariffId}")
    @Operation(summary = "Actualizar valores de una tarifa")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    fun updateTariff(
        @PathVariable id: Long,
        @PathVariable tariffId: Long,
        @Valid @RequestBody request: UpdateTariffViewModel
    ): ResponseEntity<ApiResponseViewModel<TariffDetailViewModel>> {
        val tariff = viewModelService.updateTariff(id, tariffId, request)
        return ResponseEntity.ok(
            ApiResponseViewModel(
                message = "Tariff updated successfully",
                data = tariff
            )
        )
    }
}
