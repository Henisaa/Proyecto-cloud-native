package com.rutaexpress.kafkaadmin

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class SlowConsumerStats(
    val groupId: String,
    val topic: String,
    val delayMs: Long,
    val processedCount: Long,
    val lastPartition: Int?,
    val lastOffset: Long?,
    val lastKey: String?,
    val lastProcessedAt: Instant?
)

/**
 * Consumidor con Retardo Simulado (Guía Tuning y Pruebas de Carga):
 * Simula procesamiento pesado (10ms por mensaje = máx ~100 mensajes/segundo)
 * para observar Consumer Lag en el grupo 'analytics-group'.
 */
@Service
class SlowConsumer(
    @Value("\${app.kafka.tuning.slow-consumer-delay-ms:10}")
    private val delayMs: Long = 10L
) {
    private val processedCount = AtomicLong(0)
    private val lastPartition = AtomicReference<Int?>(null)
    private val lastOffset = AtomicReference<Long?>(null)
    private val lastKey = AtomicReference<String?>(null)
    private val lastProcessedAt = AtomicReference<Instant?>(null)

    @KafkaListener(topics = ["sensor-topic"], groupId = "analytics-group")
    fun listen(
        @Payload message: String,
        @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) partition: Int? = null,
        @Header(name = KafkaHeaders.OFFSET, required = false) offset: Long? = null,
        @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) key: String? = null
    ) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        processedCount.incrementAndGet()
        lastPartition.set(partition)
        lastOffset.set(offset)
        lastKey.set(key)
        lastProcessedAt.set(Instant.now())
    }

    fun stats(): SlowConsumerStats = SlowConsumerStats(
        groupId = "analytics-group",
        topic = "sensor-topic",
        delayMs = delayMs,
        processedCount = processedCount.get(),
        lastPartition = lastPartition.get(),
        lastOffset = lastOffset.get(),
        lastKey = lastKey.get(),
        lastProcessedAt = lastProcessedAt.get()
    )
}
