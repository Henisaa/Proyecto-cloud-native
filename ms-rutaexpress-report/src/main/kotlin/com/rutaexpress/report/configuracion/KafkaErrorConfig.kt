package com.rutaexpress.report.configuracion

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaErrorConfig {
    @Bean
    fun kafkaErrorHandler(template: KafkaTemplate<Any, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(template) { record, _ ->
            TopicPartition(record.topic() + ".report.DLT", record.partition())
        }
        recoverer.setFailIfSendResultIsError(true)
        return DefaultErrorHandler(recoverer, FixedBackOff(1000L, 2L))
    }
}
