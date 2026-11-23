package com.rutaexpress.report
import com.rutaexpress.report.configuracion.KafkaErrorConfig
import com.rutaexpress.report.consumidor.EnvioEventoConsumidor
import com.rutaexpress.report.dominio.SnapshotEnvio
import com.rutaexpress.report.repositorio.ReporteEnvioRepositorio
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.*
import org.slf4j.MDC
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.Optional

class ReportConsumerTests {
    @Test fun `propaga JSON invalido para recuperar mediante DLT`() {
        val consumer = EnvioEventoConsumidor(mock(ReporteEnvioRepositorio::class.java), JsonMapper.builder().findAndAddModules().build())
        assertThrows(Exception::class.java) { consumer.consumirEventoEnvio("invalid-json") }
    }

    @Test fun `valida JSR-380 y maneja idempotencia temporal y lead time de entrega`() {
        val repo = mock(ReporteEnvioRepositorio::class.java)
        val validator = Validation.buildDefaultValidatorFactory().validator
        val consumer = EnvioEventoConsumidor(repo, JsonMapper.builder().findAndAddModules().build(), validator)

        val creado = OffsetDateTime.parse("2026-10-14T10:00:00Z")
        val actualizado = OffsetDateTime.parse("2026-10-14T10:45:00Z")
        val snapshot = SnapshotEnvio("env-10", "EXPRESS", "ACEPTADO", createdAt = creado, updatedAt = actualizado)

        `when`(repo.findById("env-10")).thenReturn(Optional.of(snapshot))

        // Evento antiguo/duplicado debe ignorarse (idempotencia)
        val viejoJson = """{"eventId":"e-1","shipmentId":"env-10","serviceType":"EXPRESS","status":"ACEPTADO","timestamp":"2026-10-14T10:15:00Z"}"""
        consumer.consumirEventoEnvio(viejoJson)
        verify(repo, never()).save(any(SnapshotEnvio::class.java))

        // Evento de entrega posterior actualiza leadTimeMinutes
        val entregadoJson = """{"eventId":"e-2","shipmentId":"env-10","serviceType":"EXPRESS","status":"ENTREGADO","timestamp":"2026-10-14T11:00:00Z"}"""
        consumer.consumirEventoEnvio(entregadoJson)
        verify(repo).save(snapshot)
        assertThat(snapshot.currentStatus).isEqualTo("ENTREGADO")
        assertThat(snapshot.leadTimeMinutes).isEqualByComparingTo("60")
        assertThat(snapshot.lastEventId).isEqualTo("e-2")
        assertThat(MDC.get("correlationId")).isNull()

        // Mismo evento con mismo eventId y mismo timestamp se ignora por deduplicación de eventId
        consumer.consumirEventoEnvio(entregadoJson)
        verify(repo, times(1)).save(snapshot)

        // Payload inválido JSR-380 (status en blanco)
        val badJson = """{"shipmentId":"env-10","status":""}"""
        assertThrows(ConstraintViolationException::class.java) { consumer.consumirEventoEnvio(badJson) }
    }

    @Test fun `KafkaErrorConfig de report configura validador interceptor MDC y error handler`() {
        val validatorBean = mock(LocalValidatorFactoryBean::class.java)
        val config = KafkaErrorConfig(validatorBean)
        config.configureKafkaListeners(KafkaListenerEndpointRegistrar())

        val interceptor = config.kafkaMdcRecordInterceptor()
        val kafkaConsumer = mock(Consumer::class.java) as Consumer<Any, Any>
        val record = ConsumerRecord<Any, Any>("shipments.events", 0, 1L, "env-99", "payload").apply {
            headers().add("X-Trace-Id", "trace-99".toByteArray(StandardCharsets.UTF_8))
        }
        interceptor.intercept(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isEqualTo("trace-99")
        interceptor.afterRecord(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isNull()

        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<Any, Any>
        assertThat(config.kafkaErrorHandler(template)).isNotNull()

        val dlt = config.shipmentsEventsReportDltTopic(3)
        assertThat(dlt.name()).isEqualTo("shipments.events.report.DLT")
        assertThat(dlt.numPartitions()).isEqualTo(3)
        assertThat(dlt.replicationFactor()).isEqualTo(3.toShort())
        assertThat(dlt.configs()["min.insync.replicas"]).isEqualTo("2")

        val singleDlt = config.shipmentsEventsReportDltTopic(1)
        assertThat(singleDlt.configs()["min.insync.replicas"]).isEqualTo("1")
    }

    @Test fun `consumirRegistro lee header X-Correlation-Id y delega al servicio de dominio`() {
        val repo = mock(ReporteEnvioRepositorio::class.java)
        val validator = Validation.buildDefaultValidatorFactory().validator
        val consumer = EnvioEventoConsumidor(repo, JsonMapper.builder().findAndAddModules().build(), validator)
        `when`(repo.findById("env-header-1")).thenReturn(Optional.empty())

        val record = ConsumerRecord(
            "shipments.events",
            1,
            42L,
            "env-header-1",
            """{"eventId":"evt-h1","shipmentId":"env-header-1","serviceType":"ESTANDAR","status":"CREADO","timestamp":"2026-10-14T12:00:00Z"}"""
        ).apply {
            headers().add("X-Correlation-Id", "corr-hdr-42".toByteArray(StandardCharsets.UTF_8))
        }

        consumer.consumirRegistro(record)
        verify(repo).save(any(SnapshotEnvio::class.java))
        assertThat(MDC.get("correlationId")).isNull()
    }
}
