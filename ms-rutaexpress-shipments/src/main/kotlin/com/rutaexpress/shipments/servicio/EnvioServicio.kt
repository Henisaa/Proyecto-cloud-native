package com.rutaexpress.shipments.servicio

import com.rutaexpress.shipments.cliente.CapacidadNoDisponibleException
import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.comandos.NotificacionPublisher
import com.rutaexpress.shipments.dominio.CrearEnvioRequest
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EnvioResponse
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.eventos.EnvioEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

class EnvioNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

class TransicionInvalidaException(mensaje: String) : RuntimeException(mensaje)

@Service
class EnvioServicio(
    private val repositorio: EnvioRepositorio,
    private val catalogoCliente: CatalogoCliente,
    private val eventPublisher: EnvioEventPublisher,
    private val notificacionPublisher: NotificacionPublisher
) {
    private val logger = LoggerFactory.getLogger(EnvioServicio::class.java)
    private val random = SecureRandom()

    @Transactional
    fun crear(request: CrearEnvioRequest, token: String?): EnvioResponse {
        val serviceId = requireNotNull(request.serviceId)
        val weightKg = requireNotNull(request.weightKg)
        val volumeM3 = requireNotNull(request.volumeM3)

        var serviceCode: String? = null
        var vehicleType: String? = null

        if (catalogoCliente.habilitado) {
            val capacidad = catalogoCliente.verificarCapacidad(
                serviceId, request.zone, weightKg, volumeM3, request.packagesCount, token
            )
            if (!capacidad.available) {
                throw CapacidadNoDisponibleException("Sin capacidad disponible: ${capacidad.message}")
            }
            serviceCode = capacidad.serviceCode
            vehicleType = capacidad.vehicleTypeSelected
        }

        val envio = Envio(
            trackingCode = generarTrackingCode(),
            serviceId = serviceId,
            serviceCode = serviceCode,
            zone = request.zone,
            vehicleType = vehicleType,
            recipientName = request.recipientName,
            recipientEmail = request.recipientEmail,
            recipientPhone = request.recipientPhone,
            originAddress = request.originAddress,
            destinationAddress = request.destinationAddress,
            weightKg = weightKg,
            volumeM3 = volumeM3,
            packagesCount = request.packagesCount,
            price = request.price,
            status = EstadoEnvio.CREADO
        )

        val guardado = repositorio.save(envio)
        eventPublisher.publicarCambioEstado(guardado)
        logger.info("Envío {} creado (tracking {})", guardado.id, guardado.trackingCode)
        return EnvioResponse.desde(guardado)
    }

    @Transactional(readOnly = true)
    fun obtener(id: String): EnvioResponse = EnvioResponse.desde(buscar(id))

    @Transactional(readOnly = true)
    fun listar(status: String?, desde: Instant?, hasta: Instant?): List<EnvioResponse> {
        val estado = status?.takeIf { it.isNotBlank() }?.let { EstadoEnvio.desde(it) }
        return repositorio.buscar(estado, desde, hasta).map { EnvioResponse.desde(it) }
    }

    @Transactional
    fun cambiarEstado(id: String, nuevoEstadoTexto: String, token: String?): EnvioResponse {
        val nuevoEstado = EstadoEnvio.desde(nuevoEstadoTexto)
        val envio = buscar(id)

        if (!envio.status.puedeTransicionarA(nuevoEstado)) {
            throw TransicionInvalidaException(
                "Transición inválida: ${envio.status} -> $nuevoEstado. " +
                    "Desde ${envio.status} solo se permite: " +
                    envio.status.transicionesPermitidas().joinToString(", ") { it.name }
            )
        }

        if (nuevoEstado == EstadoEnvio.ACEPTADO && !envio.capacityReserved && catalogoCliente.habilitado) {
            val zone = envio.zone
            val vehicleType = envio.vehicleType
            if (zone != null && vehicleType != null) {
                val restante = catalogoCliente.reservarCapacidad(
                    envio.serviceId, zone, vehicleType, envio.packagesCount, envio.weightKg, envio.volumeM3, token
                )
                envio.capacityReserved = true
                logger.info("Capacidad reservada para envío {} (cupo restante {})", envio.id, restante)
            } else {
                logger.warn("Envío {} sin zona/vehículo resuelto; no se reserva capacidad", envio.id)
            }
        }

        if (nuevoEstado == EstadoEnvio.CANCELADO && envio.capacityReserved && catalogoCliente.habilitado) {
            val zone = envio.zone
            val vehicleType = envio.vehicleType
            if (zone != null && vehicleType != null) {
                catalogoCliente.liberarCapacidad(envio.serviceId, zone, vehicleType, envio.packagesCount, token)
                envio.capacityReserved = false
                logger.info("Capacidad liberada para envío {}", envio.id)
            }
        }

        envio.status = nuevoEstado
        envio.updatedAt = Instant.now()
        val guardado = repositorio.save(envio)

        eventPublisher.publicarCambioEstado(guardado)
        notificarSegunEstado(guardado)

        return EnvioResponse.desde(guardado)
    }

    private fun notificarSegunEstado(envio: Envio) {
        when (envio.status) {
            EstadoEnvio.ACEPTADO -> notificacionPublisher.enviarEmailEstado(
                envio,
                "Tu envío ${envio.trackingCode} fue aceptado",
                "Hemos aceptado tu envío ${envio.trackingCode}. Pronto será preparado en bodega."
            )

            EstadoEnvio.EN_BODEGA -> notificacionPublisher.generarTicketBodega(envio)

            EstadoEnvio.EN_RUTA -> {
                notificacionPublisher.generarEtiqueta(envio)
                notificacionPublisher.enviarEmailEstado(
                    envio,
                    "Tu envío ${envio.trackingCode} está en ruta",
                    "Tu envío ${envio.trackingCode} está en ruta hacia ${envio.destinationAddress}."
                )
            }

            EstadoEnvio.ENTREGADO -> notificacionPublisher.enviarEmailEstado(
                envio,
                "Tu envío ${envio.trackingCode} fue entregado",
                "Tu envío ${envio.trackingCode} fue entregado a ${envio.recipientName}."
            )

            else -> Unit
        }
    }

    private fun buscar(id: String): Envio = repositorio.findById(id)
        .orElseThrow { EnvioNoEncontradoException("Envío no encontrado: $id") }

    private fun generarTrackingCode(): String {
        val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sufijo = (1..8).map { alfabeto[random.nextInt(alfabeto.length)] }.joinToString("")
        return "RX-$sufijo"
    }
}
