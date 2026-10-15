package com.rutaexpress.shipments.servicio

import com.rutaexpress.shipments.cliente.CapacidadNoDisponibleException
import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.dominio.CrearEnvioRequest
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EnvioResponse
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.eventos.EnvioEventPublisher
import com.rutaexpress.shipments.mensajeria.MensajeriaPublicador
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

class EnvioNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

class TransicionInvalidaException(mensaje: String) : RuntimeException(mensaje)

/**
 * Reglas de envíos. Cada operación persiste el cambio y publica sus mensajes dentro de la misma transacción:
 * el envío solo queda cambiado si el broker confirmó los comandos, eventos y solicitudes de notificación.
 * La reserva de capacidad es asíncrona: al aceptar se envía el comando capacity.reserve y catalog responde
 * con su resultado; la respuesta HTTP no espera ese procesamiento.
 */
@Service
class EnvioServicio(
    private val repositorio: EnvioRepositorio,
    private val catalogoCliente: CatalogoCliente,
    private val eventPublisher: EnvioEventPublisher,
    private val mensajeria: MensajeriaPublicador,
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
            // Validación de la regla de negocio: hay cupo en la zona. La reserva se pide al aceptar el envío.
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
            status = EstadoEnvio.CREADO,
            capacityStatus = EstadoCapacidad.NO_APLICA,
        )

        val guardado = repositorio.saveAndFlush(envio)
        val actor = actorActual()
        mensajeria.publicarEstado(guardado, actor)
        eventPublisher.publicarCambioEstado(guardado, actor)
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

        val integrado = catalogoCliente.habilitado
        envio.status = nuevoEstado
        envio.updatedAt = Instant.now()
        if (nuevoEstado == EstadoEnvio.ACEPTADO && integrado) {
            envio.capacityStatus = EstadoCapacidad.PENDIENTE
        }

        // Primero se persiste el estado; después se publica. Así el resultado de catalog nunca llega
        // antes de que el cambio exista en la base.
        val guardado = repositorio.saveAndFlush(envio)
        val actor = actorActual()

        if (nuevoEstado == EstadoEnvio.ACEPTADO && integrado) {
            mensajeria.solicitarReserva(guardado)
        }
        if (nuevoEstado == EstadoEnvio.CANCELADO && integrado && guardado.capacityStatus == EstadoCapacidad.RESERVADA) {
            mensajeria.solicitarLiberacion(guardado)
        }
        mensajeria.publicarEstado(guardado, actor)
        eventPublisher.publicarCambioEstado(guardado, actor)
        notificarSegunEstado(guardado)

        return EnvioResponse.desde(guardado)
    }

    private fun notificarSegunEstado(envio: Envio) {
        when (envio.status) {
            EstadoEnvio.ACEPTADO -> mensajeria.enviarEmailEstado(
                envio,
                "Tu envío ${envio.trackingCode} fue aceptado",
                "Hemos aceptado tu envío ${envio.trackingCode}. Pronto será preparado en bodega."
            )

            EstadoEnvio.EN_BODEGA -> mensajeria.generarTicketBodega(envio)

            EstadoEnvio.EN_RUTA -> {
                mensajeria.generarEtiqueta(envio)
                mensajeria.enviarEmailEstado(
                    envio,
                    "Tu envío ${envio.trackingCode} está en ruta",
                    "Tu envío ${envio.trackingCode} está en ruta hacia ${envio.destinationAddress}."
                )
            }

            EstadoEnvio.ENTREGADO -> mensajeria.enviarEmailEstado(
                envio,
                "Tu envío ${envio.trackingCode} fue entregado",
                "Tu envío ${envio.trackingCode} fue entregado a ${envio.recipientName}."
            )

            else -> Unit
        }
    }

    private fun actorActual(): String =
        SecurityContextHolder.getContext().authentication?.name ?: "LOCAL"

    private fun buscar(id: String): Envio = repositorio.findById(id)
        .orElseThrow { EnvioNoEncontradoException("Envío no encontrado: $id") }

    private fun generarTrackingCode(): String {
        val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sufijo = (1..8).map { alfabeto[random.nextInt(alfabeto.length)] }.joinToString("")
        return "RX-$sufijo"
    }
}
