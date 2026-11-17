package com.rutaexpress.catalog.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
    }
}
