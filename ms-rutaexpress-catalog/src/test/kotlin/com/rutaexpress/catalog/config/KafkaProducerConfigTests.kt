package com.rutaexpress.catalog.config

import com.rutaexpress.catalog.event.CatalogEventPublisher
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class KafkaProducerConfigTests {

    @Test
    fun `KafkaProducerConfig aplica tuning de batch size 32KB, linger 20ms, snappy, buffer 64MB e idempotencia`() {
        val config = KafkaProducerConfig("localhost:9092")
        val props = config.producerFactory().configurationProperties

        assertThat(props[ProducerConfig.BATCH_SIZE_CONFIG]).isEqualTo(32 * 1024)
        assertThat(props[ProducerConfig.LINGER_MS_CONFIG]).isEqualTo(20)
        assertThat(props[ProducerConfig.COMPRESSION_TYPE_CONFIG]).isEqualTo("snappy")
        assertThat(props[ProducerConfig.BUFFER_MEMORY_CONFIG]).isEqualTo(64 * 1024 * 1024)
        assertThat(props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG]).isEqualTo(true)

        val topic = config.catalogEventsTopic()
        assertThat(topic.name()).isEqualTo("catalog.events")
        assertThat(topic.numPartitions()).isEqualTo(3)
        assertThat(topic.replicationFactor()).isEqualTo(3.toShort())
        assertThat(topic.configs()["retention.ms"]).isEqualTo("604800000")
        assertThat(topic.configs()["cleanup.policy"]).isEqualTo("delete")
        assertThat(topic.configs()["min.insync.replicas"]).isEqualTo("2")

        val single = KafkaProducerConfig("localhost:9092", 1).catalogEventsTopic()
        assertThat(single.configs()["min.insync.replicas"]).isEqualTo("1")
    }

    @Test
    fun `CatalogEventPublisher envia ProducerRecord con headers de trazabilidad y respeta flag deshabilitado`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val records = mutableListOf<ProducerRecord<String, Any>>()
        whenever(template.send(any<ProducerRecord<String, Any>>())).thenAnswer { inv ->
            val rec = inv.getArgument<ProducerRecord<String, Any>>(0)
            records.add(rec)
            @Suppress("UNCHECKED_CAST")
            CompletableFuture.completedFuture(null) as CompletableFuture<SendResult<String, Any>>
        }

        val publisher = CatalogEventPublisher(template, "catalog.events", true)
        publisher.publishServiceCreated(10L, "EXPRESS", "Envío Express", "EXPRESS", 5000.0, 12)
        publisher.publishCapacityUpdated(10L, 1L, "URBAN_CENTER", "VAN", 50, 10, 40)

        assertThat(records).hasSize(2)
        assertThat(records[0].topic()).isEqualTo("catalog.events")
        assertThat(records[0].key()).isEqualTo("EXPRESS")
        assertThat(String(records[0].headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8)).isEqualTo("EXPRESS")
        assertThat(String(records[0].headers().lastHeader("X-Trace-Id").value(), StandardCharsets.UTF_8)).isNotBlank()
        assertThat(String(records[0].headers().lastHeader("X-Event-Id").value(), StandardCharsets.UTF_8)).isNotBlank()

        @Suppress("UNCHECKED_CAST")
        val disabledTemplate = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val disabledPublisher = CatalogEventPublisher(disabledTemplate, "catalog.events", false)
        disabledPublisher.publishServiceUpdated(10L, "EXPRESS", "ACTIVE", true)
        verifyNoInteractions(disabledTemplate)
    }

    @Test
    fun `CatalogEventPublisher publica solo despues del commit cuando hay transaccion activa`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val records = mutableListOf<ProducerRecord<String, Any>>()
        whenever(template.send(any<ProducerRecord<String, Any>>())).thenAnswer { inv ->
            val rec = inv.getArgument<ProducerRecord<String, Any>>(0)
            records.add(rec)
            @Suppress("UNCHECKED_CAST")
            CompletableFuture.completedFuture(null) as CompletableFuture<SendResult<String, Any>>
        }

        val publisher = CatalogEventPublisher(template, "catalog.events", true)
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            publisher.publishTariffUpdated(10L, 5L, "URBAN_CENTER", "RURAL", 3500.0, 200.0, 300.0, 4000.0)
            // Antes del commit no se debe haber enviado a Kafka
            assertThat(records).isEmpty()

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCommit() }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }

        val deadline = System.currentTimeMillis() + 3000
        while (records.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertThat(records).hasSize(1)
        assertThat(records[0].key()).isEqualTo("tariff-5")
    }
}
