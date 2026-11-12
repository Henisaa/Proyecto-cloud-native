package com.rutaexpress.kafkaadmin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
import java.time.Instant
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
    val slowConsumer: SlowConsumerStats
)

/**
 * Generador de Carga (Load Tester) según la Guía de Tuning y Pruebas de Carga:
 * Inunda 'sensor-topic' en paralelo usando la configuración optimizada del productor
 * (batch.size = 32KB, linger.ms = 20ms, compression.type = snappy).
 */
@RestController
@RequestMapping("/api/test", "/api/kafka/test")
class LoadTestController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Autowired(required = false) private val slowConsumer: SlowConsumer? = null,
    @Autowired(required = false) private val adminService: KafkaAdminService? = null
) {
    private val lastCount = AtomicReference(0)
    private val lastDurationMs = AtomicReference(0L)
    private val lastThroughput = AtomicReference(0.0)
    private val lastSummary = AtomicReference<String?>(null)
    private val lastExecutedAt = AtomicReference<Instant?>(null)

    @GetMapping("/start")
    fun startLoadTestGet(@RequestParam(defaultValue = "10000") count: Int): String = ejecutarCarga(count)

    @PostMapping("/start")
    fun startLoadTestPost(@RequestParam(defaultValue = "10000") count: Int): LoadTestStatusDto {
        ejecutarCarga(count)
        return status()
    }

    @GetMapping("/status")
    fun status(): LoadTestStatusDto = LoadTestStatusDto(
        topic = "sensor-topic",
        lastCount = lastCount.get(),
        lastDurationMs = lastDurationMs.get(),
        lastThroughputMsgSec = lastThroughput.get(),
        lastSummary = lastSummary.get(),
        executedAt = lastExecutedAt.get(),
        producerTuning = mapOf(
            "batch.size" to "32 KB (32768 bytes)",
            "linger.ms" to 20,
            "compression.type" to "snappy",
            "enable.idempotence" to true
        ),
        brokerRetention = mapOf(
            "log.retention.hours" to 0,
            "log.retention.minutes" to 5,
            "log.retention.bytes" to "50 MB (52428800 bytes)",
            "log.retention.check.interval.ms" to 60000
        ),
        slowConsumer = slowConsumer?.stats() ?: SlowConsumerStats(
            groupId = "analytics-group",
            topic = "sensor-topic",
            delayMs = 10L,
            processedCount = 0L,
            lastPartition = null,
            lastOffset = null,
            lastKey = null,
            lastProcessedAt = null
        )
    )

    private fun ejecutarCarga(rawCount: Int): String {
        val count = rawCount.coerceIn(1, 500_000)
        try {
            adminService?.reconcileTuningAndErrorTopics()
        } catch (_: Exception) {
        }

        val startTime = System.currentTimeMillis()
        IntStream.range(0, count).parallel().forEach { i ->
            val message = "Sensor-Data-$i-Payload-Voluminoso-Para-Llenar-Bytes"
            kafkaTemplate.send("sensor-topic", "sensor-${i % 5}", message)
        }
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime).coerceAtLeast(1L)
        val throughput = count / (duration / 1000.0)
        val summary = String.format(
            "🚀 Carga completada: %d mensajes enviados en %d ms. Throughput: %.2f msg/sec",
            count, duration, throughput
        )

        lastCount.set(count)
        lastDurationMs.set(duration)
        lastThroughput.set(throughput)
        lastSummary.set(summary)
        lastExecutedAt.set(Instant.now())
        return summary
    }
}
