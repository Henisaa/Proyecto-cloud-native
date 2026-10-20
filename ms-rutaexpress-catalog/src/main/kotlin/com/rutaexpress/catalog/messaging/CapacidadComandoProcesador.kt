package com.rutaexpress.catalog.messaging

import com.rutaexpress.catalog.model.entity.EstadoReserva
import com.rutaexpress.catalog.model.entity.FleetCapacity
import com.rutaexpress.catalog.model.entity.RegistroComandoCapacidad
import com.rutaexpress.catalog.model.entity.ReservaCapacidad
import com.rutaexpress.catalog.model.enums.VehicleType
import com.rutaexpress.catalog.model.enums.ZoneType
import com.rutaexpress.catalog.model.repository.FleetCapacityRepository
import com.rutaexpress.catalog.model.repository.RegistroComandoCapacidadRepository
import com.rutaexpress.catalog.model.repository.ReservaCapacidadRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/**
 * Aplica los comandos de capacidad sobre la base de datos de catalog.
 *  - Idempotente por comando: un commandId ya registrado devuelve su resultado sin volver a mover la capacidad.
 *  - Idempotente por envío: la reserva se guarda en tb_capacity_reservations con shipment_id único. Reservar dos
 *    veces el mismo envío devuelve la reserva existente; liberar dos veces descuenta una sola vez.
 *  - Consistente: las líneas de flota y la reserva se leen con bloqueo pesimista (SELECT ... FOR UPDATE).
 *  - La liberación actúa siempre sobre la línea que se reservó, no sobre la que indique el comando.
 *  - Una capacidad insuficiente no es un error técnico: se registra como RECHAZADA y se comunica con un evento.
 */
@Service
class CapacidadComandoProcesador(
    private val capacidades: FleetCapacityRepository,
    private val registros: RegistroComandoCapacidadRepository,
    private val reservas: ReservaCapacidadRepository,
    private val cacheManager: CacheManager,
) {
    private val log = LoggerFactory.getLogger(CapacidadComandoProcesador::class.java)

    @Transactional
    fun procesar(comando: ComandoCapacidad): ResultadoCapacidad {
        registros.findById(comando.commandId).orElse(null)?.let {
            log.info("Comando {} ya procesado: se devuelve el resultado registrado", comando.commandId)
            return it.aResultado(comando.serviceId)
        }

        val resultado = when (comando.tipo) {
            TipoComando.RESERVAR -> reservar(comando)
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
        invalidarCacheTrasCommit(comando.serviceId)
        log.info("Comando {} ({}) del envío {}: {}", comando.commandId, comando.tipo, comando.shipmentId, resultado.estado)
        return resultado
    }

    private fun reservar(comando: ComandoCapacidad): ResultadoCapacidad {
        val existente = reservas.findLockedByShipmentId(comando.shipmentId).orElse(null)
        if (existente != null && existente.estado == EstadoReserva.ACTIVA) {
            val restante = capacidades.findById(existente.capacityId).map { it.availablePackages }.orElse(0)
            return ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.RESERVADA,
                vehicleType = existente.vehicleType,
                capacidadRestante = restante,
                detalle = "Capacidad ya reservada para este envío",
            )
        }

        val zona = ZoneType.valueOf(comando.zona)
        val linea = seleccionarLinea(comando, zona)
            ?: return ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.RECHAZADA,
                vehicleType = null,
                capacidadRestante = 0,
                detalle = "Sin capacidad disponible en ${zona.name} para ${comando.packagesCount} paquete(s)",
            )

        linea.currentBookedPackages += comando.packagesCount
        // La reserva se inserta en la misma transacción que el contador. Si otro comando del mismo envío ganó la
        // carrera, la clave primaria falla, esta transacción se revierte y el reintento devuelve la reserva existente.
        val reserva = existente ?: ReservaCapacidad(shipmentId = comando.shipmentId)
        reserva.capacityId = requireNotNull(linea.id) { "La línea de capacidad no tiene id" }
        reserva.paquetes = comando.packagesCount
        reserva.vehicleType = linea.vehicleType.name
        reserva.estado = EstadoReserva.ACTIVA
        reserva.reservaCommandId = comando.commandId
        reserva.liberacionCommandId = null
        reserva.creadaEn = Instant.now()
        reserva.liberadaEn = null
        reservas.saveAndFlush(reserva)

        return ResultadoCapacidad(
            commandId = comando.commandId,
            shipmentId = comando.shipmentId,
            serviceId = comando.serviceId,
            estado = EstadoCapacidad.RESERVADA,
            vehicleType = linea.vehicleType.name,
            capacidadRestante = linea.availablePackages,
            detalle = "Capacidad reservada",
        )
    }

    private fun liberar(comando: ComandoCapacidad): ResultadoCapacidad {
        val reserva = reservas.findLockedByShipmentId(comando.shipmentId).orElse(null)
        if (reserva == null || reserva.estado != EstadoReserva.ACTIVA) {
            return ResultadoCapacidad(
                commandId = comando.commandId,
                shipmentId = comando.shipmentId,
                serviceId = comando.serviceId,
                estado = EstadoCapacidad.LIBERADA,
                vehicleType = reserva?.vehicleType,
                capacidadRestante = 0,
                detalle = "Sin reserva activa para el envío: no hubo cambios",
            )
        }
        val linea = capacidades.findLockedById(reserva.capacityId).orElse(null)
        linea?.let { it.currentBookedPackages = (it.currentBookedPackages - reserva.paquetes).coerceAtLeast(0) }
        reserva.estado = EstadoReserva.LIBERADA
        reserva.liberacionCommandId = comando.commandId
        reserva.liberadaEn = Instant.now()
        reservas.save(reserva)
        return ResultadoCapacidad(
            commandId = comando.commandId,
            shipmentId = comando.shipmentId,
            serviceId = comando.serviceId,
            estado = EstadoCapacidad.LIBERADA,
            vehicleType = reserva.vehicleType,
            capacidadRestante = linea?.availablePackages ?: 0,
            detalle = "Capacidad liberada",
        )
    }

    /**
     * Línea del vehículo pedido si cabe; si no, la primera de la zona con cupo. Las candidatas se bloquean en una
     * sola consulta ordenada por id, así dos comandos concurrentes no se bloquean entre sí en orden distinto.
     */
    private fun seleccionarLinea(comando: ComandoCapacidad, zona: ZoneType): FleetCapacity? {
        val preferida = comando.vehicleType?.let { tipo ->
            capacidades.findLockedByServiceIdAndZoneAndVehicleType(comando.serviceId, zona, VehicleType.valueOf(tipo))
                .orElse(null)
                ?.takeIf { cabe(it, comando) }
        }
        return preferida ?: capacidades
            .findLockedAvailable(comando.serviceId, zona, comando.weightKg, comando.volumeM3)
            .firstOrNull { cabe(it, comando) }
    }

    private fun cabe(linea: FleetCapacity, comando: ComandoCapacidad): Boolean =
        linea.active &&
            linea.availablePackages >= comando.packagesCount &&
            comando.weightKg <= linea.maxWeightKg &&
            comando.volumeM3 <= linea.maxVolumeM3

    /** La disponibilidad cambió: se descartan las vistas cacheadas, pero solo cuando la transacción ya confirmó. */
    private fun invalidarCacheTrasCommit(serviceId: Long) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = invalidarCache(serviceId)
            })
        } else {
            invalidarCache(serviceId)
        }
    }

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
