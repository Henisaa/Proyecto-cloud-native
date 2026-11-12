package com.rutaexpress.kafkaadmin

import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.MDC
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Interceptor del Productor que garantiza un Correlation ID (Trace ID) en los Headers de Kafka
 * para trazabilidad distribuida junto con SLF4J MDC.
 */
class CorrelationIdProducerInterceptor : ProducerInterceptor<String, String> {
    override fun onSend(record: ProducerRecord<String, String>): ProducerRecord<String, String> {
        if (record.headers().lastHeader("X-Correlation-Id") == null) {
            val correlationId = MDC.get("correlationId") ?: UUID.randomUUID().toString()
            record.headers().add("X-Correlation-Id", correlationId.toByteArray(StandardCharsets.UTF_8))
        }
        return record
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) {}
    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?) {}
}
