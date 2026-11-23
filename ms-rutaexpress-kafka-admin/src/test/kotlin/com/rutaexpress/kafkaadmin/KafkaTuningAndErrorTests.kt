package com.rutaexpress.kafkaadmin

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaFuture
import org.springframework.web.server.ResponseStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.nio.charset.StandardCharsets

class KafkaTuningAndErrorTests {

    @Test
    fun `SlowConsumer contabiliza mensajes procesados y metadatos de particion y offset`() {
        val consumer = SlowConsumer(delayMs = 1L)
        consumer.listen("Sensor-Data-1", partition = 2, offset = 45L, key = "sensor-1")
        val stats = consumer.stats()

        assertThat(stats.groupId).isEqualTo("analytics-group")
        assertThat(stats.topic).isEqualTo("sensor-topic")
        assertThat(stats.processedCount).isEqualTo(1L)
        assertThat(stats.lastPartition).isEqualTo(2)
        assertThat(stats.lastOffset).isEqualTo(45L)
        assertThat(stats.lastKey).isEqualTo("sensor-1")
        assertThat(stats.lastProcessedAt).isNotNull()
        assertThat(stats.concurrency).isEqualTo(3)
        assertThat(stats.maxPollRecords).isEqualTo(500)
    }

    @Test
    fun `CorrelationIdProducerInterceptor inyecta header desde MDC y preserva header existente`() {
        val interceptor = CorrelationIdProducerInterceptor()
        interceptor.configure(mutableMapOf<String, Any>())

        MDC.put("correlationId", "corr-mdc-test")
        try {
            val rec = ProducerRecord("sensor-topic", "sensor-0", "data")
            val result = interceptor.onSend(rec)
            val headerVal = String(result.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8)
            assertThat(headerVal).isEqualTo("corr-mdc-test")
        } finally {
            MDC.remove("correlationId")
        }

        val recWithHeader = ProducerRecord("sensor-topic", "sensor-1", "data").apply {
            headers().add("X-Correlation-Id", "fixed-corr".toByteArray(StandardCharsets.UTF_8))
        }
        val result2 = interceptor.onSend(recWithHeader)
        assertThat(String(result2.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
            .isEqualTo("fixed-corr")
        interceptor.onAcknowledgement(null, null)
        interceptor.close()
    }

    @Test
    fun `LoadTestController ejecuta carga paralela en sensor-topic y reporta throughput`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val slowConsumer = SlowConsumer(delayMs = 0L)
        val controller = LoadTestController(template, slowConsumer, null)

        val summary = controller.startLoadTestGet(25)
        assertThat(summary).contains("Carga completada: 25 mensajes enviados")
        assertThat(summary).contains("Throughput:")

        verify(template, times(25)).send(eq("sensor-topic"), anyString(), anyString())

        val dto = controller.startLoadTestPost(10)
        assertThat(dto.topic).isEqualTo("sensor-topic")
        assertThat(dto.lastCount).isEqualTo(10)
        assertThat(dto.producerTuning["compression.type"]).isEqualTo("snappy")
        assertThat(dto.producerTuning["buffer.memory"]).isEqualTo("64 MB (67108864 bytes)")
        assertThat(dto.brokerRetention["log.retention.minutes"]).isEqualTo(5)
        assertThat(dto.brokerRetention["num.network.threads"]).isEqualTo(8)
        assertThat(dto.brokerRetention["num.io.threads"]).isEqualTo(8)

        assertThat(controller.health()).contains("Load Test Controller")

        val tutorialSummary = controller.startLoadTestGet(5, "tutorial-particiones")
        assertThat(tutorialSummary).contains("Carga completada: 5 mensajes enviados")
        verify(template, times(5)).send(eq("tutorial-particiones"), anyString(), anyString())

        assertThatThrownBy {
            controller.startLoadTestGet(5, "topico-no-permitido")
        }.isInstanceOf(ResponseStatusException::class.java)

        val sustainedMsg = controller.sustainedLoadGet(messagesPerSecond = 3, seconds = 1)
        assertThat(sustainedMsg).contains("Carga sostenida iniciada: 3 msg/sec durante 1 segundos")
        val deadline = System.currentTimeMillis() + 3000
        while (controller.status().sustainedLoad["active"] == true && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        verify(template, atLeast(3)).send(eq("sensor-topic"), anyString(), anyString())
    }

    @Test
    fun `KafkaErrorHandlingService cubre validacion JSR-380, poison pill, backoff transitorio, idempotencia y reprocesamiento DLT`() {
        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val validator = Validation.buildDefaultValidatorFactory().validator
        val service = KafkaErrorHandlingService(template, validator)

        // 1. Válido + Idempotencia duplicado
        val rValid = service.simularEscenario(SimulacionErrorRequest(scenario = "valido", messageId = "m-100", orderId = "ORD-100"))
        assertThat((rValid["result"] as Map<*, *>)["status"]).isEqualTo("PROCESSED")

        val rDup = service.simularEscenario(SimulacionErrorRequest(scenario = "duplicado"))
        assertThat((rDup["result"] as Map<*, *>)["status"]).isEqualTo("DUPLICATE_IGNORED")

        // 2. Payload inválido (JSR-380 -> DLT)
        val rInvalid = service.simularEscenario(SimulacionErrorRequest(scenario = "payload-invalido"))
        assertThat((rInvalid["result"] as Map<*, *>)["status"]).isEqualTo("SENT_TO_DLT")
        assertThat((rInvalid["result"] as Map<*, *>)["reason"]).isEqualTo("ValidationException")

        // 3. Poison pill (JSON corrupto -> DLT)
        val rPoison = service.simularEscenario(SimulacionErrorRequest(scenario = "poison-pill"))
        assertThat((rPoison["result"] as Map<*, *>)["status"]).isEqualTo("SENT_TO_DLT")
        assertThat((rPoison["result"] as Map<*, *>)["reason"]).isEqualTo("DeserializationException")

        // 4. Error transitorio (2 reintentos + recuperación)
        val rTrans = service.simularEscenario(SimulacionErrorRequest(scenario = "error-transitorio", messageId = "m-trans-1"))
        assertThat((rTrans["result"] as Map<*, *>)["status"]).isEqualTo("PROCESSED")

        // 5. Error definitivo (-> DLT)
        val rPerm = service.simularEscenario(SimulacionErrorRequest(scenario = "error-definitivo-dlt", messageId = "m-perm-1"))
        assertThat((rPerm["result"] as Map<*, *>)["status"]).isEqualTo("SENT_TO_DLT")
        assertThat((rPerm["result"] as Map<*, *>)["reason"]).isEqualTo("MaxAttemptsExhausted")

        // Verificar DLT acumulada y reprocesamiento completo
        val dltBefore = service.listarDlt()
        assertThat(dltBefore).hasSize(3)

        val reprocessed = service.reprocesarDlt(null)
        assertThat(reprocessed["reprocessedCount"]).isEqualTo(3)

        val state = service.estado()
        assertThat(state.totalProcessed).isGreaterThanOrEqualTo(5)
        assertThat(state.totalDuplicatesIgnored).isGreaterThanOrEqualTo(1)
        assertThat(state.totalRetries).isGreaterThanOrEqualTo(5)
        assertThat(state.totalSentToDlt).isEqualTo(3)
        assertThat(state.totalReprocessed).isEqualTo(3)
        assertThat(state.contextualLogs).isNotEmpty()
    }

    @Test
    fun `KafkaErrorHandlingService consumidor real lanza excepciones en fallos y consume DLT`() {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val service = KafkaErrorHandlingService(null, validator)

        assertThatThrownBy {
            service.consumirOrden(ConsumerRecord("ordenes-creadas", 0, 0L, "k1", "{corrupt"))
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            service.consumirOrden(ConsumerRecord("ordenes-creadas", 0, 1L, "k2", """{"messageId":"","orderId":"","customerEmail":"bad","packages":0,"amount":-1}"""))
        }.isInstanceOf(ConstraintViolationException::class.java)

        service.consumirDlt(
            ConsumerRecord("ordenes-creadas.DLT", 0, 0L, "k1", "{corrupt").apply {
                headers().add("X-Correlation-Id", "corr-dlt-1".toByteArray(StandardCharsets.UTF_8))
            },
            exFqcn = "DeserializationException".toByteArray(StandardCharsets.UTF_8),
            exMsg = "JSON invalido".toByteArray(StandardCharsets.UTF_8),
            exStack = "stacktrace".toByteArray(StandardCharsets.UTF_8)
        )
        assertThat(service.listarDlt()).hasSize(1)
        val firstDltId = service.listarDlt().first().dltId
        assertThat(service.reprocesarDlt(firstDltId)["reprocessedCount"]).isEqualTo(1)
    }

    @Test
    fun `KafkaAdminService reconcilia topicos de tuning y errores y admite analytics-group`() {
        val admin = mock(AdminClient::class.java, RETURNS_DEEP_STUBS)
        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(setOf("sensor-topic", "ordenes-creadas", "ordenes-creadas.DLT", "catalog.events.audit.DLT")))
        `when`(admin.listConsumerGroupOffsets("analytics-group").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))
        `when`(admin.listConsumerGroupOffsets("error-handling-group").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))

        val service = KafkaAdminService(admin, 3, 2)
        assertThat(service.reconcileTuningAndErrorTopics())
            .containsExactly("catalog.events.audit.DLT", "ordenes-creadas", "ordenes-creadas.DLT", "sensor-topic")
        assertThat(service.lag("analytics-group")).isEmpty()
        assertThat(service.lag("error-handling-group")).isEmpty()

        val clientConfig = KafkaClientConfig(LocalValidatorFactoryBean())
        clientConfig.configureKafkaListeners(KafkaListenerEndpointRegistrar())
        val producerProps = clientConfig.producerFactory("localhost:9092").configurationProperties
        assertThat(producerProps[ProducerConfig.BATCH_SIZE_CONFIG]).isEqualTo(32 * 1024)
        assertThat(producerProps[ProducerConfig.LINGER_MS_CONFIG]).isEqualTo(20)
        assertThat(producerProps[ProducerConfig.COMPRESSION_TYPE_CONFIG]).isEqualTo("snappy")
        assertThat(producerProps[ProducerConfig.BUFFER_MEMORY_CONFIG]).isEqualTo(64 * 1024 * 1024)
        val interceptor = clientConfig.kafkaMdcRecordInterceptor()
        @Suppress("UNCHECKED_CAST")
        val kafkaConsumer = mock(Consumer::class.java) as Consumer<Any, Any>
        val record = ConsumerRecord<Any, Any>("sensor-topic", 0, 1L, "sensor-0", "val")
        interceptor.intercept(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isEqualTo("sensor-0")
        interceptor.afterRecord(record, kafkaConsumer)
        assertThat(MDC.get("correlationId")).isNull()

        @Suppress("UNCHECKED_CAST")
        val template = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        assertThat(clientConfig.kafkaErrorHandler(template)).isNotNull()
    }
}

@WebMvcTest(KafkaController::class, LoadTestController::class)
@Import(SeguridadConfig::class, KafkaStreamsService::class, KafkaErrorHandlingService::class, SlowConsumer::class)
class KafkaTuningAndErrorWebMvcTests {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @MockitoBean lateinit var consumer: KafkaTutorialConsumer
    @MockitoBean lateinit var decoder: JwtDecoder

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `endpoints de load test y manejo de errores responden correctamente via HTTP`() {
        mvc.perform(get("/api/test/health"))
            .andExpect(status().isOk)

        mvc.perform(get("/api/test/start").param("count", "5"))
            .andExpect(status().isOk)

        mvc.perform(get("/api/test/start").param("count", "4").param("topic", "tutorial-particiones"))
            .andExpect(status().isOk)

        mvc.perform(get("/api/test/start").param("count", "4").param("topic", "topico-invalido"))
            .andExpect(status().isBadRequest)

        mvc.perform(post("/api/test/start").param("count", "3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topic").value("sensor-topic"))
            .andExpect(jsonPath("$.lastCount").value(3))

        mvc.perform(post("/api/test/sustained").param("messagesPerSecond", "2").param("seconds", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sustainedLoad.summary").exists())

        mvc.perform(get("/api/test/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.producerTuning['compression.type']").value("snappy"))
            .andExpect(jsonPath("$.producerTuning['buffer.memory']").value("64 MB (67108864 bytes)"))
            .andExpect(jsonPath("$.brokerRetention['num.network.threads']").value(8))
            .andExpect(jsonPath("$.brokerRetention['num.io.threads']").value(8))

        mvc.perform(post("/api/kafka/errors/simulate").param("scenario", "error-definitivo-dlt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result.status").value("SENT_TO_DLT"))

        mvc.perform(get("/api/kafka/errors/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSentToDlt").value(1))

        mvc.perform(get("/api/kafka/dlt"))
            .andExpect(status().isOk)

        mvc.perform(post("/api/kafka/dlt/reprocess"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reprocessedCount").value(1))

        mvc.perform(
            post("/api/kafka/errors/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scenario":"valido","messageId":"http-msg-1","orderId":"ORD-HTTP-1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.status").value("PROCESSED"))

        mvc.perform(post("/api/kafka/tuning/init"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }
}
