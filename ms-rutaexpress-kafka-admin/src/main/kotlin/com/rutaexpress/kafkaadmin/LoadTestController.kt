package com.rutaexpress.kafkaadmin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream

data class LoadTestStatusDto(
    val topic: String,
    val lastCount: Int,
    val lastDurationMs: Long,
    val lastThroughputMsgSec: Double,
    val lastSummary: String?,
    val executedAt: Instant?,
    val producerTuning: Map<String, Any>,
    val brokerRetention: Map<String, Any>,
    val slowConsumer: SlowConsumerStats,
    val sustainedLoad: Map<String, Any> = emptyMap()
)

/**
 * Generador de Carga (Load Tester) según la Guía de Tuning y Pruebas de Carga:
 * Inunda 'sensor-topic' en paralelo usando la configuración optimizada del productor
 * (batch.size = 32KB, linger.ms = 20ms, compression.type = snappy, buffer.memory = 64MB).
 */
@RestController
@RequestMapping("/api/test", "/api/kafka/test")
class LoadTestController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Autowired(required = false) private val slowConsumer: SlowConsumer? = null,
    @Autowired(required = false) private val adminService: KafkaAdminService? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val allowedTestTopics = setOf("sensor-topic", "tutorial-particiones")

    private val lastTopic = AtomicReference("sensor-topic")
    private val lastCount = AtomicReference(0)
    private val lastDurationMs = AtomicReference(0L)
    private val lastThroughput = AtomicReference(0.0)
    private val lastSummary = AtomicReference<String?>(null)
    private val lastExecutedAt = AtomicReference<Instant?>(null)

    private val sustainedActive = AtomicBoolean(false)
    private val sustainedSentCount = AtomicLong(0L)
    private val sustainedSummary = AtomicReference<String?>(null)

    @GetMapping("/health")
    fun health(): String = "✓ Load Test Controller está corriendo en puerto 8080"

    @GetMapping("/start")
    fun startLoadTestGet(
        @RequestParam(defaultValue = "10000") count: Int,
        @RequestParam(defaultValue = "sensor-topic") topic: String = "sensor-topic"
    ): String = ejecutarCarga(count, topic)

    @PostMapping("/start")
    fun startLoadTestPost(
        @RequestParam(defaultValue = "10000") count: Int,
        @RequestParam(defaultValue = "sensor-topic") topic: String = "sensor-topic"
    ): LoadTestStatusDto {
        ejecutarCarga(count, topic)
        return status()
    }

    @GetMapping("/sustained")
    fun sustainedLoadGet(
        @RequestParam(defaultValue = "1000") messagesPerSecond: Int = 1000,
        @RequestParam(defaultValue = "60") seconds: Int = 60,
        @RequestParam(defaultValue = "sensor-topic") topic: String = "sensor-topic"
    ): String = iniciarCargaSostenida(messagesPerSecond, seconds, topic)

    @PostMapping("/sustained")
    fun sustainedLoadPost(
        @RequestParam(defaultValue = "1000") messagesPerSecond: Int = 1000,
        @RequestParam(defaultValue = "60") seconds: Int = 60,
        @RequestParam(defaultValue = "sensor-topic") topic: String = "sensor-topic"
    ): LoadTestStatusDto {
        iniciarCargaSostenida(messagesPerSecond, seconds, topic)
        return status()
    }

    @GetMapping("/status")
    fun status(): LoadTestStatusDto = LoadTestStatusDto(
        topic = lastTopic.get(),
        lastCount = lastCount.get(),
        lastDurationMs = lastDurationMs.get(),
        lastThroughputMsgSec = lastThroughput.get(),
        lastSummary = lastSummary.get(),
        executedAt = lastExecutedAt.get(),
        producerTuning = mapOf(
            "batch.size" to "32 KB (32768 bytes)",
            "linger.ms" to 20,
            "compression.type" to "snappy",
            "buffer.memory" to "64 MB (67108864 bytes)",
            "acks" to "all",
            "enable.idempotence" to true
        ),
        brokerRetention = mapOf(
            "log.retention.hours" to 0,
            "log.retention.minutes" to 5,
            "log.retention.bytes" to "50 MB (52428800 bytes)",
            "log.retention.check.interval.ms" to 60000,
            "num.network.threads" to 8,
            "num.io.threads" to 8
        ),
        slowConsumer = slowConsumer?.stats() ?: SlowConsumerStats(
            groupId = "analytics-group",
            topic = "sensor-topic",
            delayMs = 10L,
            processedCount = 0L,
            lastPartition = null,
            lastOffset = null,
            lastKey = null,
            lastProcessedAt = null,
            concurrency = 3,
            maxPollRecords = 500
        ),
        sustainedLoad = mapOf(
            "active" to sustainedActive.get(),
            "sentCount" to sustainedSentCount.get(),
            "summary" to (sustainedSummary.get() ?: "Sin carga sostenida reciente")
        )
    )

    private fun validateTopic(rawTopic: String): String {
        val clean = rawTopic.trim()
        if (clean !in allowedTestTopics) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tópico de prueba no permitido: $rawTopic")
        }
        return clean
    }

    private fun ensureTopics() {
        try {
            adminService?.reconcileTuningAndErrorTopics()
        } catch (_: Exception) {
        }
    }

    private fun ejecutarCarga(rawCount: Int, rawTopic: String): String {
        val targetTopic = validateTopic(rawTopic)
        val count = rawCount.coerceIn(1, 500_000)
        ensureTopics()

        val startTime = System.currentTimeMillis()
        IntStream.range(0, count).parallel().forEach { i ->
            val message = "Sensor-Data-$i-Payload-Voluminoso-Para-Llenar-Bytes"
            kafkaTemplate.send(targetTopic, "sensor-${i % 5}", message)
        }
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime).coerceAtLeast(1L)
        val throughput = count / (duration / 1000.0)
        val summary = String.format(
            "🚀 Carga completada: %d mensajes enviados en %d ms. Throughput: %.2f msg/sec",
            count, duration, throughput
        )

        lastTopic.set(targetTopic)
        lastCount.set(count)
        lastDurationMs.set(duration)
        lastThroughput.set(throughput)
        lastSummary.set(summary)
        lastExecutedAt.set(Instant.now())
        return summary
    }

    private fun iniciarCargaSostenida(rawRate: Int, rawSeconds: Int, rawTopic: String): String {
        val targetTopic = validateTopic(rawTopic)
        val rate = rawRate.coerceIn(1, 10_000)
        val durationSec = rawSeconds.coerceIn(1, 300)
        if (!sustainedActive.compareAndSet(false, true)) {
            return "Carga sostenida ya en ejecución (${sustainedSentCount.get()} mensajes enviados)"
        }

        ensureTopics()
        sustainedSentCount.set(0L)
        val msg = "Carga sostenida iniciada: $rate msg/sec durante $durationSec segundos"
        sustainedSummary.set(msg)

        val thread = Thread({
            try {
                for (sec in 0 until durationSec) {
                    val secStart = System.currentTimeMillis()
                    for (i in 0 until rate) {
                        val payload = "Sustained-Load-$i-Timestamp-${System.currentTimeMillis()}"
                        kafkaTemplate.send(targetTopic, "sensor-${i % 5}", payload)
                        sustainedSentCount.incrementAndGet()
                    }
                    val elapsed = System.currentTimeMillis() - secStart
                    if (elapsed < 1000L) {
                        try {
                            Thread.sleep(1000L - elapsed)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                    logger.debug("Carga sostenida segundo {}/{} completado", sec + 1, durationSec)
                }
                sustainedSummary.set("Carga sostenida completada: ${sustainedSentCount.get()} mensajes enviados en $durationSec s")
            } finally {
                sustainedActive.set(false)
            }
        }, "kafka-sustained-load")
        thread.isDaemon = true
        thread.start()
        return msg
    }
}
