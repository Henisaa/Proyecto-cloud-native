package com.rutaexpress.shipments.servicio

import com.rutaexpress.shipments.cliente.CapacidadNoDisponibleException
import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.configuracion.Actor
import com.rutaexpress.shipments.dominio.CrearEnvioRequest
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioFiltros
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EnvioResponse
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import com.rutaexpress.shipments.dominio.PaginaResponse
import com.rutaexpress.shipments.dominio.RegistroAuditoriaRepositorio
import com.rutaexpress.shipments.dominio.RegistroAuditoriaResponse
import com.rutaexpress.shipments.dominio.ResumenEnviosResponse
import com.rutaexpress.shipments.eventos.EnvioEventPublisher
import com.rutaexpress.shipments.mensajeria.IdsComando
import com.rutaexpress.shipments.mensajeria.MensajeriaPublicador
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

class EnvioNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

class TransicionInvalidaException(mensaje: String) : RuntimeException(mensaje)

/**
 * Reglas de envíos. Cada operación persiste el cambio y publica sus mensajes dentro de la misma transacción:
 * el envío solo queda cambiado si el broker confirmó los comandos, eventos y solicitudes de notificación.
 * La reserva de capacidad es asíncrona: al aceptar se envía el comando capacity.reserve y catalog responde
 * con su resultado; la respuesta HTTP no espera ese procesamiento.
 *
 * Reglas de consistencia:
 *  - Un envío solo sale a bodega con la capacidad RESERVADA (P1-01).
 *  - Los comandos de capacidad van al final de la operación y con identificadores controlados (P1-04).
 *  - Un cliente solo ve sus propios envíos (P1-08).
 */
@Service
class EnvioServicio(
    private val repositorio: EnvioRepositorio,
    private val registros: RegistroAuditoriaRepositorio,
    private val catalogoCliente: CatalogoCliente,
    private val eventPublisher: EnvioEventPublisher,
    private val mensajeria: MensajeriaPublicador,
    private val transacciones: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(EnvioServicio::class.java)
    private val random = SecureRandom()

    /**
     * La consulta HTTP de capacidad a catalog se hace antes de abrir la transacción: así no se retiene una
     * conexión de base de datos mientras se espera a otro servicio (P2-02).
     */
    fun crear(request: CrearEnvioRequest, token: String?): EnvioResponse {
        val datos = request.normalizado()
        val serviceId = requireNotNull(datos.serviceId)
        val weightKg = requireNotNull(datos.weightKg)
        val volumeM3 = requireNotNull(datos.volumeM3)
        val actor = Actor.actual()

        var serviceCode: String? = null
        var vehicleType: String? = null

        if (catalogoCliente.habilitado) {
            // Validación de la regla de negocio: hay cupo en la zona. La reserva se pide al aceptar el envío.
            val capacidad = catalogoCliente.verificarCapacidad(
                serviceId, datos.zone, weightKg, volumeM3, datos.packagesCount, token
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
            zone = datos.zone,
            vehicleType = vehicleType,
            recipientName = datos.recipientName,
            recipientEmail = datos.recipientEmail,
            recipientPhone = datos.recipientPhone,
            originAddress = datos.originAddress,
            destinationAddress = datos.destinationAddress,
            weightKg = weightKg,
            volumeM3 = volumeM3,
            packagesCount = datos.packagesCount,
            price = datos.price,
            status = EstadoEnvio.CREADO,
            capacityStatus = EstadoCapacidad.NO_APLICA,
            ownerOid = actor.oid,
            customerEmail = actor.email,
        )

        val guardado = checkNotNull(
            transacciones.execute {
                val persistido = repositorio.saveAndFlush(envio)
                mensajeria.publicarEstado(persistido, actor.nombre)
                eventPublisher.publicarCambioEstado(persistido, actor.nombre)
                persistido
            }
        )
        logger.info("Envío {} creado (tracking {})", guardado.id, guardado.trackingCode)
        return EnvioResponse.desde(guardado)
    }

    @Transactional(readOnly = true)
    fun obtener(id: String): EnvioResponse = EnvioResponse.desde(buscarVisible(id, Actor.actual()))

    @Transactional(readOnly = true)
    fun listar(
        status: String?,
        capacidad: String?,
        desde: Instant?,
        hasta: Instant?,
        texto: String?,
        pagina: Int,
        tamano: Int,
    ): PaginaResponse<EnvioResponse> {
        val actor = Actor.actual()
        val filtros = EnvioFiltros.construir(
            estado = status?.takeIf { it.isNotBlank() }?.let { EstadoEnvio.desde(it) },
            capacidad = capacidad?.takeIf { it.isNotBlank() }?.let { estadoCapacidad(it) },
            desde = desde,
            hasta = hasta,
            texto = texto,
            dueno = actor.oid,
            soloDelDueno = actor.soloCliente,
        )
        val resultado = repositorio.findAll(
            filtros,
            PageRequest.of(pagina.coerceAtLeast(0), tamano.coerceIn(1, MAX_TAMANO_PAGINA), Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return PaginaResponse(
            content = resultado.content.map { EnvioResponse.desde(it) },
            page = resultado.number,
            size = resultado.size,
            totalElements = resultado.totalElements,
            totalPages = resultado.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun resumen(): ResumenEnviosResponse {
        val actor = Actor.actual()
        val propios = actor.soloCliente
        val dueno = actor.oid ?: ""
        val porEstado = (if (propios) repositorio.contarPorEstadoDe(dueno) else repositorio.contarPorEstado())
            .associate { (it[0] as EstadoEnvio).name to (it[1] as Number).toLong() }
        val porCapacidad = (if (propios) repositorio.contarPorCapacidadDe(dueno) else repositorio.contarPorCapacidad())
            .associate { (it[0] as EstadoCapacidad).name to (it[1] as Number).toLong() }
        val recientes = repositorio.findAll(
            EnvioFiltros.construir(null, null, null, null, null, actor.oid, propios),
            PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")),
        ).content.map { EnvioResponse.desde(it) }
        return ResumenEnviosResponse(
            total = porEstado.values.sum(),
            byStatus = porEstado,
            byCapacity = porCapacidad,
            recent = recientes,
        )
    }

    /** Línea de tiempo del envío según el registro de eventos RabbitMQ (roles internos, validado en el controlador). */
    @Transactional(readOnly = true)
    fun auditoria(id: String): List<RegistroAuditoriaResponse> {
        buscarVisible(id, Actor.actual())
        return registros.findByShipmentIdOrderByOcurridoEnAsc(id).map { RegistroAuditoriaResponse.desde(it) }
    }

    @Transactional
    fun cambiarEstado(id: String, nuevoEstadoTexto: String, token: String?): EnvioResponse {
        val actor = Actor.actual()
        val nuevoEstado = EstadoEnvio.desde(nuevoEstadoTexto)
        val envio = buscarVisible(id, actor)

        if (!envio.status.puedeTransicionarA(nuevoEstado)) {
            throw TransicionInvalidaException(
                "Transición inválida: ${envio.status} -> $nuevoEstado. " +
                    "Desde ${envio.status} solo se permite: " +
                    envio.status.transicionesPermitidas().joinToString(", ") { it.name }
            )
        }
        if (envio.status == EstadoEnvio.ACEPTADO && nuevoEstado == EstadoEnvio.EN_BODEGA && !envio.capacityStatus.permiteDespacho) {
            throw TransicionInvalidaException(envio.capacityStatus.motivoBloqueo())
        }

        val integrado = catalogoCliente.habilitado
        val habiaCupo = envio.capacityStatus == EstadoCapacidad.RESERVADA
        var comandoReserva: String? = null
        envio.status = nuevoEstado
        envio.updatedAt = Instant.now()
        if (nuevoEstado == EstadoEnvio.ACEPTADO && integrado) {
            comandoReserva = UUID.randomUUID().toString()
            envio.capacityStatus = EstadoCapacidad.PENDIENTE
            envio.lastReserveCommandId = comandoReserva
        }

        // Primero se persiste el estado (con bloqueo optimista); después se publica.
        val guardado = repositorio.saveAndFlush(envio)

        mensajeria.publicarEstado(guardado, actor.nombre)
        eventPublisher.publicarCambioEstado(guardado, actor.nombre)
        notificarSegunEstado(guardado)
        // Los comandos de capacidad se publican al final: si falla cualquier publicación anterior, la transacción se
        // revierte antes de haber pedido o devuelto cupo, y no quedan liberaciones huérfanas (P1-04).
        if (comandoReserva != null) {
            mensajeria.solicitarReserva(guardado, comandoReserva)
        }
        if (nuevoEstado == EstadoEnvio.CANCELADO && integrado && habiaCupo) {
            mensajeria.solicitarLiberacion(guardado, IdsComando.liberacionPorCancelacion(guardado.id))
        }

        return EnvioResponse.desde(guardado)
    }

    /** Vuelve a pedir la reserva de un envío ACEPTADO cuya capacidad fue RECHAZADA. */
    @Transactional
    fun reintentarReserva(id: String): EnvioResponse {
        val envio = buscarVisible(id, Actor.actual())
        if (!catalogoCliente.habilitado) {
            throw TransicionInvalidaException("La integración con el catálogo está deshabilitada; no hay reserva que reintentar")
        }
        if (envio.status != EstadoEnvio.ACEPTADO || envio.capacityStatus != EstadoCapacidad.RECHAZADA) {
            throw TransicionInvalidaException(
                "Solo se puede reintentar la reserva de un envío ACEPTADO con capacidad RECHAZADA " +
                    "(estado ${envio.status}, capacidad ${envio.capacityStatus})"
            )
        }
        val comandoReserva = UUID.randomUUID().toString()
        envio.capacityStatus = EstadoCapacidad.PENDIENTE
        envio.lastReserveCommandId = comandoReserva
        envio.updatedAt = Instant.now()
        val guardado = repositorio.saveAndFlush(envio)
        mensajeria.solicitarReserva(guardado, comandoReserva)
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

    /** Un cliente que no es dueño recibe el mismo 404 que un envío inexistente: no se revela que existe. */
    private fun buscarVisible(id: String, actor: Actor): Envio {
        val envio = repositorio.findById(id)
            .orElseThrow { EnvioNoEncontradoException("Envío no encontrado: $id") }
        if (actor.soloCliente && (actor.oid == null || envio.ownerOid != actor.oid)) {
            throw EnvioNoEncontradoException("Envío no encontrado: $id")
        }
        return envio
    }

    private fun estadoCapacidad(valor: String): EstadoCapacidad =
        EstadoCapacidad.entries.firstOrNull { it.name.equals(valor.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Estado de capacidad inválido: '$valor'. Valores permitidos: ${EstadoCapacidad.entries.joinToString(", ") { it.name }}"
            )

    private fun generarTrackingCode(): String {
        val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sufijo = (1..8).map { alfabeto[random.nextInt(alfabeto.length)] }.joinToString("")
        return "RX-$sufijo"
    }

    companion object {
        const val MAX_TAMANO_PAGINA = 100
    }
}
