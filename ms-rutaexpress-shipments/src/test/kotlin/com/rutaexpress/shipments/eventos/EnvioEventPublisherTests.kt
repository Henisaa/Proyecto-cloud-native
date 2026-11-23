package com.rutaexpress.shipments.eventos

import com.rutaexpress.shipments.configuracion.KafkaProducerConfig
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EstadoEnvio
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

class EnvioEventPublisherTests {

    @Test
    fun `KafkaProducerConfig aplica tuning de batch size 32KB, linger 20ms, snappy e idempotencia`() {
        val config = KafkaProducerConfig("localhost:9092")
        val factory = config.producerFactory()
        val props = factory.configurationProperties

        assertThat(props[ProducerConfig.BATCH_SIZE_CONFIG]).isEqualTo(32 * 1024)
        assertThat(props[ProducerConfig.LINGER_MS_CONFIG]).isEqualTo(20)
        assertThat(props[ProducerConfig.COMPRESSION_TYPE_CONFIG]).isEqualTo("snappy")
        assertThat(props[ProducerConfig.BUFFER_MEMORY_CONFIG]).isEqualTo(64 * 1024 * 1024)
        assertThat(props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG]).isEqualTo(true)
        assertThat(props[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] as Int)
            .isGreaterThanOrEqualTo((props[ProducerConfig.LINGER_MS_CONFIG] as Int) + (props[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] as Int))

        val shipmentsTopic = config.shipmentsEventsTopic()
        assertThat(shipmentsTopic.name()).isEqualTo("shipments.events")
        assertThat(shipmentsTopic.numPartitions()).isEqualTo(3)
        assertThat(shipmentsTopic.replicationFactor()).isEqualTo(3.toShort())
        assertThat(shipmentsTopic.configs()["retention.ms"]).isEqualTo("604800000")
        assertThat(shipmentsTopic.configs()["cleanup.policy"]).isEqualTo("delete")
        assertThat(shipmentsTopic.configs()["min.insync.replicas"]).isEqualTo("2")

        val timelineTopic = config.auditTimelineTopic()
        assertThat(timelineTopic.name()).isEqualTo("audit.timeline")
        assertThat(timelineTopic.configs()["cleanup.policy"]).isEqualTo("compact,delete")
        assertThat(timelineTopic.configs()["retention.ms"]).isEqualTo("2592000000")

        val singleBroker = KafkaProducerConfig("localhost:9092", 1)
        assertThat(singleBroker.shipmentsEventsTopic().configs()["min.insync.replicas"]).isEqualTo("1")
        assertThat(singleBroker.auditTimelineTopic().configs()["min.insync.replicas"]).isEqualTo("1")
    }

    @Test
    fun `publica en shipments_events y audit_timeline con headers de trazabilidad MDC`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val enviados = mutableListOf<ProducerRecord<String, Any>>()
        whenever(template.send(any<ProducerRecord<String, Any>>())).thenAnswer { inv ->
            val rec = inv.getArgument<ProducerRecord<String, Any>>(0)
            enviados.add(rec)
            @Suppress("UNCHECKED_CAST")
            CompletableFuture.completedFuture(null) as CompletableFuture<SendResult<String, Any>>
        }

        val publisher = EnvioEventPublisher(template, "shipments.events", "audit.timeline", true)
        val envio = Envio(
            id = "env-corr-1",
            trackingCode = "RX-CORR1",
            serviceId = 1L,
            serviceType = "EXPRESS",
            zone = "NORTE",
            recipientName = "Ana",
            originAddress = "A",
            destinationAddress = "B",
            status = EstadoEnvio.ACEPTADO,
        )

        publisher.publicarCambioEstado(envio, "admin@rutaexpress.cl")

        // Esperar brevemente al hilo daemon del ejecutor
        val deadline = System.currentTimeMillis() + 3000
        while (enviados.size < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        assertThat(enviados).hasSize(2)
        enviados.forEach { rec ->
            val corr = String(rec.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8)
            val trace = String(rec.headers().lastHeader("X-Trace-Id").value(), StandardCharsets.UTF_8)
            val eventId = String(rec.headers().lastHeader("X-Event-Id").value(), StandardCharsets.UTF_8)
            assertThat(corr).isEqualTo("env-corr-1")
            assertThat(trace).isNotBlank()
            assertThat(eventId).isNotBlank()
        }
    }

    @Test
    fun `con kafka deshabilitado no intenta enviar registros`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val publisher = EnvioEventPublisher(template, "shipments.events", "audit.timeline", false)
        publisher.publicarCambioEstado(
            Envio(
                trackingCode = "RX-OFF",
                serviceId = 1L,
                zone = "CENTRO",
                recipientName = "Ana",
                originAddress = "A",
                destinationAddress = "B",
                status = EstadoEnvio.CREADO
            )
        )
        verifyNoInteractions(template)
    }

    @Test
    fun `publicarCambioEstado espera a afterCommit cuando hay transaccion activa`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val enviados = mutableListOf<ProducerRecord<String, Any>>()
        whenever(template.send(any<ProducerRecord<String, Any>>())).thenAnswer { inv ->
            val rec = inv.getArgument<ProducerRecord<String, Any>>(0)
            enviados.add(rec)
            @Suppress("UNCHECKED_CAST")
            CompletableFuture.completedFuture(null) as CompletableFuture<SendResult<String, Any>>
        }

        val publisher = EnvioEventPublisher(template, "shipments.events", "audit.timeline", true)
        val envio = Envio(
            id = "env-tx-1",
            trackingCode = "RX-TX1",
            serviceId = 1L,
            serviceType = "ESTANDAR",
            zone = "SUR",
            recipientName = "Luis",
            originAddress = "A",
            destinationAddress = "B",
            status = EstadoEnvio.EN_RUTA
        )

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            publisher.publicarCambioEstado(envio, "despachador@rutaexpress.cl")
            assertThat(enviados).isEmpty()

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCommit() }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }

        val deadline = System.currentTimeMillis() + 3000
        while (enviados.size < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertThat(enviados).hasSize(2)
    }
}
