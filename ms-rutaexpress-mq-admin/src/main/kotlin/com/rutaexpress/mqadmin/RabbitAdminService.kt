package com.rutaexpress.mqadmin

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ShutdownSignalException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Binding.DestinationType
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.HeadersExchange
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.core.Declarable
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Lógica de administración de la topología: crea y elimina colas, exchanges y enlaces,
 * valida cada entrada antes de tocar el broker y traduce los errores a mensajes claros.
 */
@Service
class RabbitAdminService(
    private val admin: AmqpAdmin,
    private val rabbitTemplate: RabbitTemplate,
    private val cliente: ClienteGestion,
    private val topologia: RabbitTopology,
    private val monitor: MqAdminService,
    @Value("\${spring.rabbitmq.virtual-host:/}") private val vhost: String,
    @Value("\${app.mq.command-ttl-ms:86400000}") private val ttlComandos: Long,
    @Value("\${app.mq.dlq-ttl-ms:604800000}") private val ttlDlq: Long,
    @Value("\${app.mq.max-length:10000}") private val maxLength: Int,
) {

    /** Declara la topología fija y aplica las políticas de retención por defecto (POST /api/mq-admin/topology). */
    fun reconciliar(): Map<String, Any> {
        val declaraciones: List<Declarable> = topologia.declaraciones()
        declaraciones.filterIsInstance<Exchange>().forEach { admin.declareExchange(it) }
        declaraciones.filterIsInstance<Queue>().forEach { admin.declareQueue(it) }
        declaraciones.filterIsInstance<Binding>().forEach { admin.declareBinding(it) }
        monitor.aplicarRetencion(RetentionRequest(ttlComandos, ttlDlq, maxLength))
        return mapOf(
            "exchanges" to topologia.nombresExchangesProtegidos().sorted(),
            "queues" to topologia.colasDeMonitoreo(),
            "bindings" to declaraciones.filterIsInstance<Binding>().size,
        )
    }

    fun crearCola(solicitud: CrearColaRequest): Map<String, Any?> {
        val nombre = solicitud.name
        validarNombre("la cola", nombre)
        requireNotNull(nombre)
        val argumentos = mutableMapOf<String, Any?>()
        solicitud.ttlMs?.let { rango("ttlMs", it, 1000, 2_592_000_000L); argumentos["x-message-ttl"] = it }
        solicitud.maxLength?.let { rango("maxLength", it.toLong(), 1, 1_000_000); argumentos["x-max-length"] = it }
        solicitud.expiresMs?.let { rango("expiresMs", it, 1000, 2_592_000_000L); argumentos["x-expires"] = it }
        solicitud.overflow?.let {
            require(it in OVERFLOW) { "overflow debe ser reject-publish o drop-head" }
            argumentos["x-overflow"] = it
        }
        solicitud.deadLetterExchange?.let { validarNombre("el exchange dead-letter", it); argumentos["x-dead-letter-exchange"] = it }
        solicitud.deadLetterRoutingKey?.let {
            validarClave(it)
            require(solicitud.deadLetterExchange != null) { "deadLetterRoutingKey requiere deadLetterExchange" }
            argumentos["x-dead-letter-routing-key"] = it
        }
        if (cliente.existe(RutasGestion.cola(vhost, nombre))) {
            throw ConflictoException("La cola '$nombre' ya existe")
        }
        enRabbit("la cola", nombre) { admin.declareQueue(Queue(nombre, solicitud.durable, false, false, argumentos)) }
        return mapOf("name" to nombre, "durable" to solicitud.durable, "arguments" to argumentos, "created" to true)
    }

    fun eliminarCola(nombre: String, forzar: Boolean): Map<String, Any?> {
        validarNombre("la cola", nombre)
        if (!cliente.existe(RutasGestion.cola(vhost, nombre))) throw RecursoNoEncontradoException("La cola '$nombre' no existe")
        protegerSiEsNucleo(nombre in topologia.nombresColasProtegidas(), nombre, "la cola", forzar)
        enRabbit("la cola", nombre) { admin.deleteQueue(nombre) }
        return mapOf("name" to nombre, "deleted" to true)
    }

    fun crearExchange(solicitud: CrearExchangeRequest): Map<String, Any?> {
        val nombre = solicitud.name
        validarNombre("el exchange", nombre)
        requireNotNull(nombre)
        val tipo = solicitud.type ?: "direct"
        require(tipo in TIPOS_EXCHANGE) { "El tipo de exchange '$tipo' no es válido: use direct, topic, fanout o headers" }
        if (cliente.existe(RutasGestion.exchange(vhost, nombre))) throw ConflictoException("El exchange '$nombre' ya existe")
        enRabbit("el exchange", nombre) { admin.declareExchange(construirExchange(nombre, tipo, solicitud.durable)) }
        return mapOf("name" to nombre, "type" to tipo, "durable" to solicitud.durable, "created" to true)
    }

    fun eliminarExchange(nombre: String, forzar: Boolean): Map<String, Any?> {
        validarNombre("el exchange", nombre)
        if (!cliente.existe(RutasGestion.exchange(vhost, nombre))) throw RecursoNoEncontradoException("El exchange '$nombre' no existe")
        protegerSiEsNucleo(nombre in topologia.nombresExchangesProtegidos(), nombre, "el exchange", forzar)
        enRabbit("el exchange", nombre) { admin.deleteExchange(nombre) }
        return mapOf("name" to nombre, "deleted" to true)
    }

    fun crearEnlace(solicitud: CrearBindingRequest): Map<String, Any?> {
        val origen = requireNotNull(solicitud.source) { "El exchange de origen es obligatorio" }
        val destino = requireNotNull(solicitud.destination) { "El destino es obligatorio" }
        validarNombre("el exchange de origen", origen)
        validarNombre("el destino", destino)
        validarClave(solicitud.routingKey)
        require(solicitud.destinationType in TIPOS_DESTINO) { "destinationType debe ser queue o exchange" }
        val tipoDestino = if (solicitud.destinationType == "exchange") DestinationType.EXCHANGE else DestinationType.QUEUE
        if (!cliente.existe(RutasGestion.exchange(vhost, origen))) {
            throw RecursoNoEncontradoException("El exchange de origen '$origen' no existe")
        }
        val rutaDestino = if (tipoDestino == DestinationType.QUEUE) RutasGestion.cola(vhost, destino) else RutasGestion.exchange(vhost, destino)
        if (!cliente.existe(rutaDestino)) throw RecursoNoEncontradoException("El destino '$destino' (${solicitud.destinationType}) no existe")
        if (existeEnlace(origen, destino, solicitud.destinationType, solicitud.routingKey)) {
            throw ConflictoException("El enlace $origen -> $destino con routing key '${solicitud.routingKey}' ya existe")
        }
        enRabbit("el enlace", origen) {
            admin.declareBinding(Binding(destino, tipoDestino, origen, solicitud.routingKey, emptyMap()))
        }
        return mapOf(
            "source" to origen,
            "destination" to destino,
            "destinationType" to solicitud.destinationType,
            "routingKey" to solicitud.routingKey,
            "created" to true,
        )
    }

    fun eliminarEnlace(origen: String, destino: String, tipoDestino: String, clave: String, forzar: Boolean): Map<String, Any?> {
        validarNombre("el exchange de origen", origen)
        validarNombre("el destino", destino)
        validarClave(clave)
        require(tipoDestino in TIPOS_DESTINO) { "destinationType debe ser queue o exchange" }
        if (!existeEnlace(origen, destino, tipoDestino, clave)) {
            throw RecursoNoEncontradoException("No existe el enlace $origen -> $destino con routing key '$clave'")
        }
        protegerSiEsNucleo(topologia.esEnlaceNucleo(origen, destino), "$origen -> $destino", "el enlace", forzar)
        val tipo = if (tipoDestino == "exchange") DestinationType.EXCHANGE else DestinationType.QUEUE
        enRabbit("el enlace", origen) { admin.removeBinding(Binding(destino, tipo, origen, clave, emptyMap())) }
        return mapOf("source" to origen, "destination" to destino, "destinationType" to tipoDestino, "routingKey" to clave, "deleted" to true)
    }

    fun publicarMensaje(exchange: String, solicitud: PublicarMensajeRequest): Map<String, Any?> {
        validarNombre("el exchange", exchange)
        validarClave(solicitud.routingKey)
        val cuerpo = requireNotNull(solicitud.body) { "El cuerpo del mensaje es obligatorio" }
        if (!cliente.existe(RutasGestion.exchange(vhost, exchange))) throw RecursoNoEncontradoException("El exchange '$exchange' no existe")
        val mensaje = MessageBuilder.withBody(cuerpo.toByteArray(StandardCharsets.UTF_8))
            .setContentType("application/json")
            .setContentEncoding("utf-8")
            .setMessageId(UUID.randomUUID().toString())
            .build()
        val correlacion = CorrelationData(UUID.randomUUID().toString())
        try {
            rabbitTemplate.send(exchange, solicitud.routingKey, mensaje, correlacion)
            val confirmacion = correlacion.future.get(10, TimeUnit.SECONDS)
            if (!confirmacion.isAck) {
                throw BrokerNoDisponibleException("RabbitMQ no confirmó el mensaje: ${confirmacion.reason}")
            }
        } catch (ex: BrokerNoDisponibleException) {
            throw ex
        } catch (ex: Exception) {
            throw BrokerNoDisponibleException("No se pudo publicar en '$exchange': ${ex.message}", ex)
        }
        return mapOf(
            "exchange" to exchange,
            "routingKey" to solicitud.routingKey,
            "confirmed" to true,
            "routed" to (correlacion.returned == null),
        )
    }

    fun exchanges(): List<Map<String, Any?>> = cliente.lista(RutasGestion.exchangesDelVhost(vhost))
        .filter { val nombre = it["name"] as? String ?: ""; nombre.isNotEmpty() && !nombre.startsWith("amq.") }
        .map { mapOf("name" to it["name"], "type" to it["type"], "durable" to it["durable"]) }

    fun enlaces(): List<Map<String, Any?>> = cliente.lista(RutasGestion.enlacesDelVhost(vhost))
        .filter { (it["source"] as? String).orEmpty().isNotEmpty() }
        .map {
            mapOf(
                "source" to it["source"],
                "destination" to it["destination"],
                "destinationType" to it["destination_type"],
                "routingKey" to it["routing_key"],
            )
        }

    private fun existeEnlace(origen: String, destino: String, tipoDestino: String, clave: String): Boolean =
        cliente.lista(RutasGestion.enlacesDeOrigen(vhost, origen)).any {
            it["destination"] == destino && it["destination_type"] == tipoDestino && it["routing_key"] == clave
        }

    private fun protegerSiEsNucleo(esNucleo: Boolean, nombre: String, entidad: String, forzar: Boolean) {
        if (esNucleo && !forzar) {
            throw ConflictoException(
                "$entidad '$nombre' forma parte de la topología protegida del clúster; repita la operación con force=true si realmente desea eliminarla"
            )
        }
    }

    private fun construirExchange(nombre: String, tipo: String, durable: Boolean): Exchange = when (tipo) {
        "direct" -> DirectExchange(nombre, durable, false)
        "topic" -> TopicExchange(nombre, durable, false)
        "fanout" -> FanoutExchange(nombre, durable, false)
        else -> HeadersExchange(nombre, durable, false)
    }

    /** Traduce los fallos de declaración: 406 PRECONDITION_FAILED significa que existe con otros argumentos. */
    private fun <T> enRabbit(entidad: String, nombre: String, accion: () -> T): T = try {
        accion()
    } catch (ex: Exception) {
        if (esPrecondicionFallida(ex)) {
            throw ConflictoException("Ya existe $entidad '$nombre' con argumentos distintos (RabbitMQ 406 PRECONDITION_FAILED)")
        }
        throw BrokerNoDisponibleException("No se pudo completar la operación sobre $entidad '$nombre' en RabbitMQ: ${ex.message}", ex)
    }

    private fun esPrecondicionFallida(ex: Throwable): Boolean = generateSequence(ex) { it.cause }
        .any { causa -> causa is ShutdownSignalException && (causa.reason as? AMQP.Channel.Close)?.replyCode == 406 }

    private fun validarNombre(entidad: String, nombre: String?) {
        require(!nombre.isNullOrBlank()) { "El nombre de $entidad es obligatorio y no puede estar vacío" }
        require(nombre.length <= 255) { "El nombre de $entidad no puede superar 255 caracteres" }
        require(!nombre.startsWith("amq.")) { "El prefijo 'amq.' está reservado por RabbitMQ; elija otro nombre para $entidad" }
        require(NOMBRE.matches(nombre)) {
            "El nombre de $entidad '$nombre' no es válido: use letras, números, '.', '-' o '_', empezando por letra o número"
        }
    }

    private fun validarClave(clave: String) {
        require(clave.length <= 255 && CLAVE.matches(clave)) {
            "La routing key '$clave' no es válida: use letras, números, '.', '-', '_', '*' o '#' (máximo 255 caracteres)"
        }
    }

    private fun rango(campo: String, valor: Long, minimo: Long, maximo: Long) {
        require(valor in minimo..maximo) { "$campo debe estar entre $minimo y $maximo" }
    }

    companion object {
        private val NOMBRE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
        private val CLAVE = Regex("^[A-Za-z0-9*#._-]*$")
        private val TIPOS_EXCHANGE = setOf("direct", "topic", "fanout", "headers")
        private val TIPOS_DESTINO = setOf("queue", "exchange")
        private val OVERFLOW = setOf("reject-publish", "drop-head")
    }
}
