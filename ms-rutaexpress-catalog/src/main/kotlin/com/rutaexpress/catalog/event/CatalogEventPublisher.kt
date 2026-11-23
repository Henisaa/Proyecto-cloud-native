package com.rutaexpress.catalog.event

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class CatalogEventPublisher(
    @Autowired(required = false)
    private val kafkaTemplate: KafkaTemplate<String, Any>?,
    @Value("\${app.kafka.topics.catalog-events:catalog.events}")
    private val catalogTopic: String = "catalog.events",
    @Value("\${app.kafka.enabled:true}")
    private val kafkaEnabled: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(CatalogEventPublisher::class.java)

    private val ejecutor = ThreadPoolExecutor(
        1, 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue(1000),
        { tarea -> Thread(tarea, "kafka-catalog-eventos").apply { isDaemon = true } },
        { _, _ -> logger.warn("Cola de eventos Kafka de catálogo llena; se omite publicación para no bloquear el servicio") }
    )

    fun publishServiceCreated(serviceId: Long, code: String, name: String, category: String, basePrice: Double, estimatedHours: Int) {
        val payload = ServiceCreatedPayload(serviceId, code, name, category, basePrice, estimatedHours)
        val envelope = EventEnvelope(
            type = "catalog.service.created",
            payload = payload
        )
        sendEvent(code, envelope)
    }

    fun publishServiceUpdated(serviceId: Long, code: String, status: String, active: Boolean) {
        val payload = ServiceUpdatedPayload(serviceId, code, status, active)
        val envelope = EventEnvelope(
            type = "catalog.service.updated",
            payload = payload
        )
        sendEvent(code, envelope)
    }

    fun publishCapacityUpdated(
        serviceId: Long,
        capacityId: Long,
        zone: String,
        vehicleType: String,
        maxDailyPackages: Int,
        currentBookedPackages: Int,
        availablePackages: Int
    ) {
        val payload = CapacityUpdatedPayload(
            serviceId,
            capacityId,
            zone,
            vehicleType,
            maxDailyPackages,
            currentBookedPackages,
            availablePackages
        )
        val envelope = EventEnvelope(
            type = "catalog.capacity.updated",
            payload = payload
        )
        sendEvent("capacity-$capacityId", envelope)
    }

    fun publishTariffUpdated(
        serviceId: Long,
        tariffId: Long,
        originZone: String,
        destinationZone: String,
        baseFare: Double,
        perKmRate: Double,
        perKgRate: Double,
        minFare: Double
    ) {
        val payload = TariffUpdatedPayload(
            serviceId,
            tariffId,
            originZone,
            destinationZone,
            baseFare,
            perKmRate,
            perKgRate,
            minFare
        )
        val envelope = EventEnvelope(
            type = "catalog.tariff.updated",
            payload = payload
        )
        sendEvent("tariff-$tariffId", envelope)
    }

    private fun sendEvent(key: String, envelope: EventEnvelope<*>) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            logger.debug("Kafka publishing disabled or template not present. Skipping event {}", envelope.type)
            return
        }
        despuesDelCommit {
            publicarRegistro(key, envelope)
        }
    }

    private fun despuesDelCommit(accion: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = ejecutor.execute(accion)
            })
        } else {
            accion()
        }
    }

    private fun publicarRegistro(key: String, envelope: EventEnvelope<*>) {
        if (kafkaTemplate == null) return
        val correlationId = envelope.correlationId ?: key
        val prevCorrelation = MDC.get("correlationId")
        MDC.put("correlationId", correlationId)
        try {
            val record = ProducerRecord<String, Any>(catalogTopic, key, envelope)
            record.headers().add("X-Correlation-Id", correlationId.toByteArray(StandardCharsets.UTF_8))
            record.headers().add("X-Trace-Id", envelope.traceId.toByteArray(StandardCharsets.UTF_8))
            record.headers().add("X-Event-Id", envelope.eventId.toByteArray(StandardCharsets.UTF_8))
            kafkaTemplate.send(record)
                .whenComplete { result, ex ->
                    MDC.put("correlationId", correlationId)
                    try {
                        if (ex != null) {
                            logger.warn("Could not publish Kafka event {} [correlationId={}]: {}", envelope.type, correlationId, ex.message)
                        } else {
                            logger.info(
                                "Published Kafka event {} with key {} to partition {} [correlationId={}]",
                                envelope.type, key, result?.recordMetadata?.partition(), correlationId
                            )
                        }
                    } finally {
                        MDC.remove("correlationId")
                    }
                }
        } catch (ex: Exception) {
            logger.warn("Error attempting to send Kafka event {} [correlationId={}]: {}", envelope.type, correlationId, ex.message)
        } finally {
            if (prevCorrelation != null) MDC.put("correlationId", prevCorrelation) else MDC.remove("correlationId")
        }
    }
}
