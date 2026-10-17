package com.rutaexpress.catalog.messaging

import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.entity.RegistroComandoCapacidad
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.RegistroComandoCapacidadRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Aplica los comandos de capacidad sobre la base de datos de catalog.
 *  - Idempotente: un commandId ya registrado devuelve su resultado sin volver a mover la capacidad.
 *  - Consistente: las líneas de flota se leen con bloqueo pesimista (SELECT ... FOR UPDATE), así dos
 *    reservas simultáneas no pueden vender el mismo cupo.
 *  - Una capacidad insuficiente no es un error técnico: se registra como RECHAZADA y se comunica con un evento.
 */
@Service
class CapacidadComandoProcesador(
    private val capacidades: FleetCapacityRepository,
    private val registros: RegistroComandoCapacidadRepository,
    private val cacheManager: CacheManager,
) {
    private val log = LoggerFactory.getLogger(CapacidadComandoProcesador::class.java)

    @Transactional
    fun procesar(comando: ComandoCapacidad): ResultadoCapacidad {
        registros.findById(comando.commandId).orElse(null)?.let {
            log.info("Comando {} ya procesado: se devuelve el resultado registrado", comando.commandId)
            return it.aResultado(comando.serviceId)
        }

        val reservaActiva = if (comando.tipo == TipoComando.RESERVAR) reservaActiva(comando.shipmentId) else null
        val resultado = when (comando.tipo) {
            TipoComando.RESERVAR -> reservaActiva?.let {
                ResultadoCapacidad(
                    commandId = comando.commandId,
                    shipmentId = comando.shipmentId,
                    serviceId = comando.serviceId,
                    estado = EstadoCapacidad.RESERVADA,
                    vehicleType = it.vehicleType,
                    capacidadRestante = it.capacidadRestante,
                    detalle = "Capacidad ya reservada para este envío",
                )
            } ?: reservar(comando)
            TipoComando.LIBERAR -> liberar(comando)
        }
        registros.save(
            RegistroComandoCapacidad(
                commandId = comando.commandId,
                tipo = comando.tipo.name,
                shipmentId = comando.shipmentId,
                resultado = resultado.estado.name,
                vehicleType = resultado.vehicleType,
                capacidadRestante = resultado.capacidadRestante,
                detalle = resultado.detalle.take(250),
                procesadoEn = Instant.now(),
            )
        )
        invalidarCache(comando.serviceId)
        log.info("Comando {} ({}) del envío {}: {}", comando.commandId, comando.tipo, comando.shipmentId, resultado.estado)
        return resultado
    }

    /** Un envío conserva su reserva hasta que exista una liberación posterior. */
    private fun reservaActiva(shipmentId: String): RegistroComandoCapacidad? {
        val historial = registros.findByShipmentIdOrderByProcesadoEnAsc(shipmentId)
        val ultimaReserva = historial.lastOrNull { it.tipo == TipoComando.RESERVAR.name && it.resultado == EstadoCapacidad.RESERVADA.name }
            ?: return null
        val ultimaLiberacion = historial.lastOrNull { it.tipo == TipoComando.LIBERAR.name }
        return ultimaReserva.takeIf { ultimaLiberacion == null || it.procesadoEn.isAfter(ultimaLiberacion.procesadoEn) }
    }

    private fun reservar(comando: ComandoCapacidad): ResultadoCapacidad {
        val zona = ZoneType.valueOf(comando.zona)
        val preferida = comando.vehicleType?.let { tipo ->
            capacidades.findLockedByServiceIdAndZoneAndVehicleType(comando.serviceId, zona, VehicleType.valueOf(tipo))
                .orElse(null)
                ?.takeIf { cabe(it, comando) }
        }
        val linea = preferida ?: lineaAlternativa(comando, zona)

        return if (linea == null) {
            ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.RECHAZADA,
                vehicleType = null,
                capacidadRestante = 0,
                detalle = "Sin capacidad disponible en ${zona.name} para ${comando.packagesCount} paquete(s)",
            )
        } else {
            linea.currentBookedPackages += comando.packagesCount
            ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.RESERVADA,
                vehicleType = linea.vehicleType.name,
                capacidadRestante = linea.availablePackages,
                detalle = "Capacidad reservada",
            )
        }
    }

    private fun liberar(comando: ComandoCapacidad): ResultadoCapacidad {
        val zona = ZoneType.valueOf(comando.zona)
        val linea = comando.vehicleType?.let { tipo ->
            capacidades.findLockedByServiceIdAndZoneAndVehicleType(comando.serviceId, zona, VehicleType.valueOf(tipo))
                .orElse(null)
        }
        if (linea == null) {
            return ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.LIBERADA,
                vehicleType = comando.vehicleType,
                capacidadRestante = 0,
                detalle = "Sin línea de capacidad que liberar: no hubo cambios",
            )
        }
        linea.currentBookedPackages = (linea.currentBookedPackages - comando.packagesCount).coerceAtLeast(0)
        return ResultadoCapacidad(
            commandId = comando.commandId,
            shipmentId = comando.shipmentId,
            serviceId = comando.serviceId,
            estado = EstadoCapacidad.LIBERADA,
            vehicleType = linea.vehicleType.name,
            capacidadRestante = linea.availablePackages,
            detalle = "Capacidad liberada",
        )
    }

    /** Sin vehículo preferido (o si el preferido no alcanza), toma la primera línea de la zona que sí tenga cupo. */
    private fun lineaAlternativa(comando: ComandoCapacidad, zona: ZoneType): FleetCapacity? =
        capacidades.findAvailableCapacities(comando.serviceId, zona, comando.weightKg, comando.volumeM3)
            .asSequence()
            .mapNotNull { candidata ->
                capacidades.findLockedByServiceIdAndZoneAndVehicleType(comando.serviceId, zona, candidata.vehicleType)
                    .orElse(null)
            }
            .firstOrNull { cabe(it, comando) }

    private fun cabe(linea: FleetCapacity, comando: ComandoCapacidad): Boolean =
        linea.active &&
            linea.availablePackages >= comando.packagesCount &&
            comando.weightKg <= linea.maxWeightKg &&
            comando.volumeM3 <= linea.maxVolumeM3

    /** La disponibilidad cambió: se descartan las vistas cacheadas del servicio y del listado. */
    private fun invalidarCache(serviceId: Long) {
        cacheManager.getCache("catalog_service_details")?.evict(serviceId)
        cacheManager.getCache("catalog_services")?.clear()
    }

    private fun RegistroComandoCapacidad.aResultado(serviceId: Long) = ResultadoCapacidad(
        commandId = commandId,
        shipmentId = shipmentId,
        serviceId = serviceId,
        estado = EstadoCapacidad.valueOf(resultado),
        vehicleType = vehicleType,
        capacidadRestante = capacidadRestante,
        detalle = detalle,
    )
}
