package com.rutaexpress.report.configuracion

import jakarta.validation.ConstraintViolationException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListenerConfigurer
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.util.backoff.ExponentialBackOff
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.nio.charset.StandardCharsets

@Configuration
@EnableKafka
class KafkaErrorConfig(
    @Autowired(required = false)
    private val validator: LocalValidatorFactoryBean? = null
) : KafkaListenerConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun configureKafkaListeners(registrar: KafkaListenerEndpointRegistrar) {
        if (validator != null) {
            registrar.setValidator(validator)
        }
    }

    @Bean
    fun kafkaMdcRecordInterceptor(): RecordInterceptor<Any, Any> = object : RecordInterceptor<Any, Any> {
        override fun intercept(record: ConsumerRecord<Any, Any>, consumer: org.apache.kafka.clients.consumer.Consumer<Any, Any>): ConsumerRecord<Any, Any> {
            val header = record.headers().lastHeader("X-Correlation-Id") ?: record.headers().lastHeader("X-Trace-Id")
            val correlationId = header?.value()?.let { String(it, StandardCharsets.UTF_8) }
                ?: record.key()?.toString()
                ?: "${record.topic()}-${record.partition()}-${record.offset()}"
            MDC.put("correlationId", correlationId)
            return record
        }

        override fun afterRecord(record: ConsumerRecord<Any, Any>, consumer: org.apache.kafka.clients.consumer.Consumer<Any, Any>) {
            MDC.remove("correlationId")
        }
    }

    @Bean
    fun kafkaErrorHandler(template: KafkaTemplate<Any, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(template) { record, ex ->
            val correlationId = MDC.get("correlationId") ?: record.key()?.toString() ?: "N/A"
            logger.error(
                "Fallo definitivo ID: {}, enviando a DLT ({}.report.DLT): {}",
                correlationId, record.topic(), ex.message
            )
            TopicPartition(record.topic() + ".report.DLT", record.partition())
        }
        recoverer.setFailIfSendResultIsError(true)

        val backOff = ExponentialBackOff(1000L, 2.0).apply {
            maxInterval = 8000L
            maxElapsedTime = 7000L
        }

        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(
                DeserializationException::class.java,
                MethodArgumentNotValidException::class.java,
                ConstraintViolationException::class.java,
                IllegalArgumentException::class.java
            )
            setRetryListeners(RetryListener { record, ex, deliveryAttempt ->
                val correlationId = MDC.get("correlationId") ?: record.key()?.toString() ?: "N/A"
                logger.warn(
                    "Fallo al procesar ID: {}, reintentando (intento {})... causa: {}",
                    correlationId, deliveryAttempt, ex?.message
                )
            })
        }
    }
}
