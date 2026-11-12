package com.rutaexpress.audit
import com.rutaexpress.audit.configuracion.KafkaErrorConfig
import com.rutaexpress.audit.consumidor.AuditoriaKafkaConsumidor
import com.rutaexpress.audit.dominio.EventoAuditoria
import com.rutaexpress.audit.dominio.ProcessedMessage
import com.rutaexpress.audit.repositorio.AuditoriaRepositorio
import com.rutaexpress.audit.repositorio.ProcessedMessageRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets

class AuditConsumerTests {
    private val repository = mock(AuditoriaRepositorio::class.java)
    private val consumer = AuditoriaKafkaConsumidor(repository, JsonMapper.builder().findAndAddModules().build())
    private val json = """{"eventId":"evt-1","shipmentId":"env-1","timestamp":"2026-10-14T12:00:00Z","actor":"dispatcher","status":"ACEPTADO"}"""
    @Test fun `conserva identidad y fecha del evento original`() {
        consumer.consumirEvento(ConsumerRecord("shipments.events",0,0,"env-1",json))
        val captured = ArgumentCaptor.forClass(EventoAuditoria::class.java)
        verify(repository).save(captured.capture())
        assertThat(captured.value.usuarioResponsable).isEqualTo("dispatcher")
        assertThat(captured.value.eventId).isEqualTo("evt-1")
        assertThat(captured.value.idEntidad).isEqualTo("env-1")
    }
    @Test fun `omite evento ya persistido desde otro topic`() {
        `when`(repository.existsByEventId("evt-1")).thenReturn(true)
        consumer.consumirEvento(ConsumerRecord("audit.timeline",0,0,"evt-1",json))
        verify(repository,never()).save(any(EventoAuditoria::class.java))
    }
    @Test fun `propaga evento invalido para reintentar y enviar a DLT`() {
        assertThrows(IllegalArgumentException::class.java) { consumer.consumirEvento(ConsumerRecord("shipments.events",0,0,"env-1","{}")) }
    }

    @Test fun `idempotencia Check-then-Act registra en processed_messages e ignora duplicados y colisiones de constraint`() {
        val processedRepo = mock(ProcessedMessageRepositorio::class.java)
        val validator = Validation.buildDefaultValidatorFactory().validator
        val idempotentConsumer = AuditoriaKafkaConsumidor(
            repository,
            JsonMapper.builder().findAndAddModules().build(),
            processedRepo,
            validator
        )
        val record = ConsumerRecord("shipments.events", 0, 1L, "env-1", json).apply {
            headers().add("X-Correlation-Id", "corr-123".toByteArray(StandardCharsets.UTF_8))
        }

        // Primera vez: guarda en processed_messages y auditoria
        `when`(processedRepo.existsById("evt-1")).thenReturn(false)
        `when`(repository.existsByEventId("evt-1")).thenReturn(false)
        idempotentConsumer.consumirEvento(record)
        verify(processedRepo).save(any(ProcessedMessage::class.java))
        verify(repository).save(any(EventoAuditoria::class.java))
        assertThat(MDC.get("correlationId")).isNull()

        // Segunda vez: detectado en processed_messages -> no vuelve a persistir
        `when`(processedRepo.existsById("evt-1")).thenReturn(true)
        idempotentConsumer.consumirEvento(record)
        verify(repository, times(1)).save(any(EventoAuditoria::class.java))

        // Condición de carrera: UniqueConstraintViolation -> ignora silenciosamente
        `when`(processedRepo.existsById("evt-1")).thenReturn(false)
        `when`(processedRepo.save(any(ProcessedMessage::class.java))).thenThrow(DataIntegrityViolationException("duplicate PK"))
        idempotentConsumer.consumirEvento(record)
        verify(repository, times(1)).save(any(EventoAuditoria::class.java))
    }

    @Test fun `validacion JSR-380 falla cuando timestamp esta vacio`() {
        val processedRepo = mock(ProcessedMessageRepositorio::class.java)
        val validator = Validation.buildDefaultValidatorFactory().validator
        val idempotentConsumer = AuditoriaKafkaConsumidor(
            repository,
            JsonMapper.builder().findAndAddModules().build(),
            processedRepo,
            validator
        )
        val badJson = """{"eventId":"evt-2","shipmentId":"env-2","timestamp":""}"""
        assertThrows(ConstraintViolationException::class.java) {
            idempotentConsumer.consumirEvento(ConsumerRecord("shipments.events", 0, 2L, "env-2", badJson))
        }
    }

    @Test fun `KafkaErrorConfig registra validador interceptor MDC y error handler`() {
        val validatorBean = mock(LocalValidatorFactoryBean::class.java)
        val config = KafkaErrorConfig(validatorBean)
        val registrar = KafkaListenerEndpointRegistrar()
        config.configureKafkaListeners(registrar)

        val interceptor = config.kafkaMdcRecordInterceptor()
        val kafkaConsumer = mock(Consumer::class.java) as Consumer<Any, Any>
        val record = ConsumerRecord<Any, Any>("shipments.events", 0, 5L, "key-9", "payload").apply {
            headers().add("X-Correlation-Id", "corr-mdc-9".toByteArray(StandardCharsets.UTF_8))
        }
        interceptor.intercept(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isEqualTo("corr-mdc-9")
        interceptor.afterRecord(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isNull()

        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<Any, Any>
        val handler = config.kafkaErrorHandler(template)
        assertThat(handler).isNotNull()
    }
}
