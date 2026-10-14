package com.rutaexpress.catalog.event

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

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

        try {
            kafkaTemplate.send(catalogTopic, key, envelope)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        logger.warn("Could not publish Kafka event {}: {}", envelope.type, ex.message)
                    } else {
                        logger.info(
                            "Published Kafka event {} with key {} to partition {}",
                            envelope.type, key, result?.recordMetadata?.partition()
                        )
                    }
                }
        } catch (ex: Exception) {
            logger.warn("Error attempting to send Kafka event {}: {}", envelope.type, ex.message)
        }
    }
}
