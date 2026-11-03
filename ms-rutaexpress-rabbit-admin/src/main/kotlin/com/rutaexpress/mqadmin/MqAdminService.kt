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

    /** Resumen por nodo: nombre, si corre, memoria y disco con sus alarmas, y tiempo activo. */
    fun cluster(): List<Map<String, Any?>> =
        (cliente.obtener("/api/nodes", List::class.java) ?: emptyList<Any>())
            .filterIsInstance<Map<*, *>>()
            .map {
                mapOf(
                    "name" to it["name"],
                    "running" to (it["running"] == true),
                    "memUsed" to it["mem_used"],
                    "memLimit" to it["mem_limit"],
                    "memAlarm" to (it["mem_alarm"] == true),
                    "diskFree" to it["disk_free"],
                    "diskFreeLimit" to it["disk_free_limit"],
                    "diskFreeAlarm" to (it["disk_free_alarm"] == true),
                    "uptimeMs" to it["uptime"],
                )
            }

    /**
     * Colas de la topología fija (una no declarada aparece con declared=false) y las demás colas del vhost, como las
     * que crea la API. Sin estas últimas, una cola recién creada no aparecería nunca en el listado.
     */
    fun colas(): List<Map<String, Any?>> = (topologia.colasDeMonitoreo() + otrasColas()).map { nombre ->
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
            "arrivedTotal" to llegados(datos),
        )
    }

    /** Colas del vhost que no son de la topología fija (las creadas desde la API), sin las internas amq.*. */
    private fun otrasColas(): List<String> {
        val fijas = topologia.colasDeMonitoreo().toSet()
        return cliente.lista(RutasGestion.colasDelVhost(vhost))
            .mapNotNull { it["name"] as? String }
            .filter { it !in fijas && !it.startsWith("amq.") }
            .sorted()
    }

    /**
     * Mensajes que han llegado a la cola desde que arrancó el nodo: los que ya confirmó su consumidor más los que
     * siguen dentro. Una DLQ consumida casi siempre muestra 0 mensajes, así que este acumulado es la métrica
     * que realmente refleja los fallos. Las estadísticas se reinician con el nodo.
     */
    private fun llegados(datos: Map<*, *>?): Long {
        val estadisticas = datos?.get("message_stats") as? Map<*, *>
        return numero(estadisticas, "ack") + numero(datos, "messages")
    }

    /** Llegadas por segundo en la ventana reciente de estadísticas de RabbitMQ. */
    private fun tasaLlegadas(datos: Map<*, *>?): Double {
        val estadisticas = datos?.get("message_stats") as? Map<*, *>
        val detalle = (estadisticas?.get("ack_details") ?: estadisticas?.get("publish_details")) as? Map<*, *>
        return (detalle?.get("rate") as? Number)?.toDouble() ?: 0.0
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
            "dlqArrivedTotal" to dlq.sumOf { llegados(it) },
            "dlqArrivedRate" to dlq.sumOf { tasaLlegadas(it) },
            "queues" to todas.map {
                mapOf(
                    "name" to it["name"],
                    "messages" to numero(it, "messages"),
                    "messagesReady" to numero(it, "messages_ready"),
                    "messagesUnacknowledged" to numero(it, "messages_unacknowledged"),
                    "consumers" to numero(it, "consumers"),
                    "policy" to it["policy"],
                    "durable" to it["durable"],
                    "deadLetterExchange" to ((it["arguments"] as? Map<*, *>)?.get("x-dead-letter-exchange") ?: (it["effective_policy_definition"] as? Map<*, *>)?.get("dead-letter-exchange")),
                )
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
