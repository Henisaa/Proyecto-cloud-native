package com.rutaexpress.mqadmin

import io.micrometer.core.instrument.MeterRegistry
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

data class RetentionRequest(
    @field:Min(1000) @field:Max(2592000000) val commandTtlMs: Long = 86400000,
    @field:Min(1000) @field:Max(2592000000) val dlqTtlMs: Long = 604800000,
    @field:Min(1) @field:Max(1000000) val maxLength: Int = 10000
)

@Service
class MqAdminService(
    private val admin: AmqpAdmin,
    registry: MeterRegistry,
    @Value("\${app.mq.management-url}") url: String,
    @Value("\${spring.rabbitmq.username}") username: String,
    @Value("\${spring.rabbitmq.password}") password: String,
    @Value("\${spring.rabbitmq.virtual-host:/}") private val vhost: String,
    @Value("\${app.mq.alert-threshold:1}") private val threshold: Int,
    @Value("\${app.mq.command-ttl-ms:86400000}") private val commandTtl: Long,
    @Value("\${app.mq.dlq-ttl-ms:604800000}") private val dlqTtl: Long,
    @Value("\${app.mq.max-length:10000}") private val maxLength: Int
) {
    private val client = RestClient.builder().baseUrl(url)
        .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()).apply { setReadTimeout(Duration.ofSeconds(10)) })
        .defaultHeaders { it.setBasicAuth(username, password) }.build()
    private val observed = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    init {
        RabbitTopology.queues.filter { it.endsWith(".dlq") }.forEach { name ->
            val count = java.util.concurrent.atomic.AtomicLong()
            observed[name] = count
            registry.gauge("rutaexpress.mq.dlq.depth", listOf(io.micrometer.core.instrument.Tag.of("queue", name)), count)
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="\${app.mq.poll-interval-ms:30000}", initialDelay=10000)
    fun monitor() {
        try {
            queues().filter { it["alert"] == true }.forEach {
                org.slf4j.LoggerFactory.getLogger(javaClass).warn("DLQ {} tiene {} mensajes pendientes", it["name"], it["messages"])
            }
        } catch(ex: org.springframework.web.client.RestClientException) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("No se pudo muestrear RabbitMQ: {}", ex.javaClass.simpleName)
        }
    }

    fun cluster(): Any = client.get().uri("/api/nodes").retrieve().body(Any::class.java) ?: emptyList<Any>()
    fun queues(): List<Map<String, Any>> = RabbitTopology.queues.sorted().map { name ->
        val data = client.get().uri("/api/queues/{vhost}/{name}", vhost, name).retrieve().body(Map::class.java) ?: emptyMap<String, Any>()
        val messages = (data["messages"] as? Number)?.toLong() ?: 0L
        observed[name]?.set(messages)
        mapOf("name" to name, "messages" to messages,
            "messagesReady" to ((data["messages_ready"] as? Number)?.toLong() ?: 0L),
            "unacknowledged" to ((data["messages_unacknowledged"] as? Number)?.toLong() ?: 0L),
            "consumers" to ((data["consumers"] as? Number)?.toLong() ?: 0L),
            "alert" to (name.endsWith(".dlq") && messages >= threshold))
    }
    fun reconcile(): Map<String, Any> {
        val declarations = RabbitTopology().topology().declarables
        declarations.filterIsInstance<org.springframework.amqp.core.Exchange>().forEach { admin.declareExchange(it) }
        declarations.filterIsInstance<org.springframework.amqp.core.Queue>().forEach { admin.declareQueue(it) }
        declarations.filterIsInstance<org.springframework.amqp.core.Binding>().forEach { admin.declareBinding(it) }
        retention(RetentionRequest(commandTtl, dlqTtl, maxLength))
        return mapOf("exchanges" to listOf("cmd.direct", "cmd.topic", "cmd.dead.dlx"), "queues" to RabbitTopology.queues)
    }
    fun retention(request: RetentionRequest): RetentionRequest {
        val validator = jakarta.validation.Validation.buildDefaultValidatorFactory()
        validator.use { require(it.validator.validate(request).isEmpty()) { "Retención fuera de límites" } }
        policy("rutaexpress.commands", "^q\\.cmd\\.(email|warehouse|label)$", request.commandTtlMs, request.maxLength)
        policy("rutaexpress.dlq", "^q\\.cmd\\.(email|warehouse|label)\\.dlq$", request.dlqTtlMs, request.maxLength)
        return request
    }
    private fun policy(name: String, pattern: String, ttl: Long, length: Int) {
        client.put().uri("/api/policies/{vhost}/{name}", vhost, name).body(mapOf(
            "pattern" to pattern, "apply-to" to "queues", "priority" to 10,
            "definition" to mapOf("message-ttl" to ttl, "max-length" to length, "overflow" to "reject-publish")
        )).retrieve().toBodilessEntity()
    }
}
