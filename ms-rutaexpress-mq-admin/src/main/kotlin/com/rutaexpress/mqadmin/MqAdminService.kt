package com.rutaexpress.mqadmin

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitoreo y políticas de retención del clúster: colas de la topología, DLQ con alerta,
 * métricas agregadas y políticas que limitan tamaño y expiración de los mensajes.
 */
@Service
class MqAdminService(
    private val cliente: ClienteGestion,
    private val topologia: RabbitTopology,
    registro: MeterRegistry,
    @Value("\${spring.rabbitmq.virtual-host:/}") private val vhost: String,
    @Value("\${app.mq.alert-threshold:1}") private val umbralAlerta: Int,
) {
    private val log = LoggerFactory.getLogger(MqAdminService::class.java)
    private val profundidadDlq = ConcurrentHashMap<String, AtomicLong>()

    init {
        topologia.dlqs().forEach { dlq ->
            val valor = AtomicLong()
            profundidadDlq[dlq] = valor
            registro.gauge("rutaexpress.mq.dlq.depth", listOf(Tag.of("queue", dlq)), valor)
        }
    }

    @Scheduled(fixedDelayString = "\${app.mq.poll-interval-ms:30000}", initialDelay = 10000)
    fun monitorizar() {
        try {
            alertas().forEach { log.warn("DLQ {} tiene {} mensajes pendientes", it["name"], it["messages"]) }
        } catch (ex: BrokerNoDisponibleException) {
            log.warn("No se pudo muestrear RabbitMQ: {}", ex.message)
        }
    }

    fun cluster(): Any = cliente.obtener("/api/nodes", List::class.java) ?: emptyList<Any>()

    /** Estado de las colas de la topología fija. Una cola no declarada aparece con declared=false. */
    fun colas(): List<Map<String, Any?>> = topologia.colasDeMonitoreo().map { nombre ->
        val datos = cliente.objeto(RutasGestion.cola(vhost, nombre))
        val mensajes = numero(datos, "messages")
        profundidadDlq[nombre]?.set(mensajes)
        mapOf(
            "name" to nombre,
            "declared" to (datos != null),
            "messages" to mensajes,
            "messagesReady" to numero(datos, "messages_ready"),
            "unacknowledged" to numero(datos, "messages_unacknowledged"),
            "consumers" to numero(datos, "consumers"),
            "alert" to (nombre.endsWith(".dlq") && mensajes >= umbralAlerta),
        )
    }

    fun alertas(): List<Map<String, Any?>> = colas().filter { it["alert"] == true }

    /** Totales del vhost: colas activas, mensajes pendientes, sin confirmar, consumidores y mensajes en DLQ. */
    fun metricas(): Map<String, Any> {
        val todas = cliente.lista(RutasGestion.colasDelVhost(vhost))
        val dlq = todas.filter { (it["name"] as? String)?.endsWith(".dlq") == true }
        return mapOf(
            "activeQueues" to todas.size,
            "messagesReady" to todas.sumOf { numero(it, "messages_ready") },
            "messagesUnacknowledged" to todas.sumOf { numero(it, "messages_unacknowledged") },
            "consumers" to todas.sumOf { numero(it, "consumers") },
            "dlqQueues" to dlq.size,
            "dlqMessages" to dlq.sumOf { numero(it, "messages") },
            "queues" to todas.map {
                mapOf("name" to it["name"], "messages" to numero(it, "messages"), "consumers" to numero(it, "consumers"))
            },
        )
    }

    /** Política para colas de trabajo (rechaza publicar al llegar al límite) y otra para DLQ (descarta lo más antiguo). */
    fun aplicarRetencion(solicitud: RetentionRequest): RetentionRequest {
        require(solicitud.commandTtlMs in 1000..2_592_000_000L) { "commandTtlMs debe estar entre 1000 ms y 30 días" }
        require(solicitud.dlqTtlMs in 1000..2_592_000_000L) { "dlqTtlMs debe estar entre 1000 ms y 30 días" }
        require(solicitud.maxLength in 1..1_000_000) { "maxLength debe estar entre 1 y 1.000.000" }
        cliente.escribir(
            RutasGestion.politica(vhost, "rutaexpress.trabajo"),
            mapOf(
                "pattern" to patron(topologia.colasDeTrabajo()),
                "apply-to" to "queues",
                "priority" to 10,
                "definition" to mapOf(
                    "message-ttl" to solicitud.commandTtlMs,
                    "max-length" to solicitud.maxLength,
                    "overflow" to "reject-publish",
                ),
            ),
        )
        cliente.escribir(
            RutasGestion.politica(vhost, "rutaexpress.dlq"),
            mapOf(
                "pattern" to patron(topologia.dlqs()),
                "apply-to" to "queues",
                "priority" to 10,
                "definition" to mapOf(
                    "message-ttl" to solicitud.dlqTtlMs,
                    "max-length" to solicitud.maxLength,
                    "overflow" to "drop-head",
                ),
            ),
        )
        return solicitud
    }

    private fun patron(nombres: List<String>): String =
        "^(" + nombres.joinToString("|") { it.replace(".", "\\.") } + ")$"

    private fun numero(datos: Map<*, *>?, clave: String): Long = (datos?.get(clave) as? Number)?.toLong() ?: 0L
}
