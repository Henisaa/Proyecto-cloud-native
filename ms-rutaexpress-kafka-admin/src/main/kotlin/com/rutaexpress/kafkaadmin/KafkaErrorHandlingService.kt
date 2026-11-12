package com.rutaexpress.kafkaadmin

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.Validator
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class EventoOrdenStreamingDto(
    @field:NotBlank(message = "messageId es obligatorio")
    val messageId: String = "",
    @field:NotBlank(message = "orderId es obligatorio")
    val orderId: String = "",
    @field:Email(message = "customerEmail debe ser un correo válido")
    @field:NotBlank(message = "customerEmail es obligatorio")
    val customerEmail: String = "",
    @field:Min(value = 1, message = "packages debe ser al menos 1")
    val packages: Int = 1,
    @field:Positive(message = "amount debe ser positivo")
    val amount: Double = 0.0,
    val simulateError: String? = null,
    val correlationId: String? = null
)

data class ProcessedMessageRegistro(
    val messageId: String,
    val orderId: String,
    val topic: String,
    val processedAt: Instant = Instant.now()
)

data class DeadLetterMensajeDto(
    val dltId: String,
    val originalTopic: String,
    val targetDltTopic: String,
    val key: String?,
    val payload: String,
    val correlationId: String,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stacktrace: String,
    val failedAt: Instant = Instant.now(),
    val reprocessed: Boolean = false
)

data class LogContextualDto(
    val level: String,
    val correlationId: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)

data class ErrorHandlingStateDto(
    val mainTopic: String,
    val dltTopic: String,
    val consumerGroup: String,
    val totalReceived: Long,
    val totalProcessed: Long,
    val totalDuplicatesIgnored: Long,
    val totalRetries: Long,
    val totalSentToDlt: Long,
    val totalReprocessed: Long,
    val processedMessages: List<ProcessedMessageRegistro>,
    val dltMessages: List<DeadLetterMensajeDto>,
    val contextualLogs: List<LogContextualDto>
)

data class SimulacionErrorRequest(
    val scenario: String = "valido",
    val messageId: String? = null,
    val orderId: String? = null,
    val customerEmail: String? = null,
    val packages: Int? = null,
    val amount: Double? = null
)

/**
 * Servicio que implementa los 5 pilares de la guía "Manejo de Errores en Arquitecturas de Streaming":
 * 1. Validación de Payloads (JSR-380 @Valid + ErrorHandlingDeserializer).
 * 2. Control de Reintentos (DefaultErrorHandler + ExponentialBackOff 1s, 2s, 4s).
 * 3. Dead Letter Topics (.DLT) con Header Enrichment y Herramienta de Reprocesamiento.
 * 4. Idempotencia del Consumidor (patrón Check-then-Act sobre tabla/registro processed_messages).
 * 5. Observabilidad y Logging Contextual (Correlation ID en Headers Kafka + SLF4J MDC).
 */
@Service
class KafkaErrorHandlingService(
    @Autowired(required = false) private val kafkaTemplate: KafkaTemplate<String, String>? = null,
    @Autowired(required = false) private val validator: Validator? = null,
    @Autowired(required = false) objectMapper: ObjectMapper? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper: ObjectMapper = objectMapper ?: JsonMapper.builder().findAndAddModules().build()

    companion object {
        const val TOPIC_PRINCIPAL = "ordenes-creadas"
        const val TOPIC_DLT = "ordenes-creadas.DLT"
        const val CONSUMER_GROUP = "error-handling-group"
    }

    // Tabla de control equivalente a processed_messages (message_id PK, timestamp)
    private val processedMessages = ConcurrentHashMap<String, ProcessedMessageRegistro>()
    private val dltStore = ConcurrentLinkedDeque<DeadLetterMensajeDto>()
    private val logHistory = ConcurrentLinkedDeque<LogContextualDto>()
    private val transientRetryAttempts = ConcurrentHashMap<String, AtomicInteger>()

    private val totalReceived = AtomicLong(0)
    private val totalProcessed = AtomicLong(0)
    private val totalDuplicatesIgnored = AtomicLong(0)
    private val totalRetries = AtomicLong(0)
    private val totalSentToDlt = AtomicLong(0)
    private val totalReprocessed = AtomicLong(0)

    @KafkaListener(topics = [TOPIC_PRINCIPAL], groupId = CONSUMER_GROUP)
    fun consumirOrden(record: ConsumerRecord<String, String>) {
        val headerCorr = record.headers().lastHeader("X-Correlation-Id")?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }
        procesarMensaje(record.topic(), record.key(), record.value(), headerCorr, enviarADltSiFalla = false)
    }

    @KafkaListener(topics = [TOPIC_DLT], groupId = "dlt-audit-group")
    fun consumirDlt(
        record: ConsumerRecord<String, String>,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) exFqcn: ByteArray? = null,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) exMsg: ByteArray? = null,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_STACKTRACE, required = false) exStack: ByteArray? = null
    ) {
        val correlationId = record.headers().lastHeader("X-Correlation-Id")?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }
            ?: record.key()
            ?: "N/A"
        val fqcn = exFqcn?.let { String(it, StandardCharsets.UTF_8) } ?: "StreamingProcessingException"
        val msg = exMsg?.let { String(it, StandardCharsets.UTF_8) } ?: "Error definitivo en consumidor"
        val stack = exStack?.let { String(it, StandardCharsets.UTF_8) } ?: ""

        registrarEnDlt(
            originalTopic = TOPIC_PRINCIPAL,
            targetDltTopic = record.topic(),
            key = record.key(),
            payload = record.value(),
            correlationId = correlationId,
            exClass = fqcn,
            exMessage = msg,
            stacktrace = stack
        )
    }

    fun procesarMensaje(
        topic: String,
        key: String?,
        rawPayload: String,
        headerCorrelationId: String? = null,
        enviarADltSiFalla: Boolean = true
    ): Map<String, Any> {
        totalReceived.incrementAndGet()
        var correlationId = headerCorrelationId ?: key ?: UUID.randomUUID().toString()
        val prevMdc = MDC.get("correlationId")
        MDC.put("correlationId", correlationId)

        try {
            // Paso 1: Deserialización (Equivalente a ErrorHandlingDeserializer ante Poison Pill)
            val dto = try {
                mapper.readValue(rawPayload, EventoOrdenStreamingDto::class.java)
            } catch (ex: Exception) {
                logContextual("ERROR", correlationId, "Error de deserialización (Poison Pill) ID: $correlationId, enviando a DLT: ${ex.message}")
                if (enviarADltSiFalla) {
                    enviarADlt(topic, key, rawPayload, correlationId, ex)
                    return mapOf("status" to "SENT_TO_DLT", "reason" to "DeserializationException", "correlationId" to correlationId)
                }
                throw IllegalArgumentException("Poison pill / JSON inválido: ${ex.message}", ex)
            }

            correlationId = dto.correlationId ?: headerCorrelationId ?: dto.messageId.ifBlank { correlationId }
            MDC.put("correlationId", correlationId)

            logContextual("INFO", correlationId, "Evento OrderCreated recibido ID: ${dto.orderId.ifBlank { dto.messageId }}")

            // Paso 2: Validación de Payload con JSR-380 (Bean Validation)
            if (validator != null) {
                val violations = validator.validate(dto)
                if (violations.isNotEmpty()) {
                    val detalle = violations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
                    val ex = ConstraintViolationException("Payload inválido: $detalle", violations)
                    logContextual("ERROR", correlationId, "Validación JSR-380 fallida ID: ${dto.messageId} -> $detalle. Enviando a DLT.")
                    if (enviarADltSiFalla) {
                        enviarADlt(topic, key, rawPayload, correlationId, ex)
                        return mapOf("status" to "SENT_TO_DLT", "reason" to "ValidationException", "detail" to detalle, "correlationId" to correlationId)
                    }
                    throw ex
                }
            } else if (dto.messageId.isBlank() || dto.orderId.isBlank() || dto.packages < 1 || dto.amount <= 0.0 || !dto.customerEmail.contains("@")) {
                val detalle = "Payload inválido según reglas JSR-380"
                val ex = IllegalArgumentException(detalle)
                logContextual("ERROR", correlationId, "Validación fallida ID: ${dto.messageId}, enviando a DLT.")
                if (enviarADltSiFalla) {
                    enviarADlt(topic, key, rawPayload, correlationId, ex)
                    return mapOf("status" to "SENT_TO_DLT", "reason" to "ValidationException", "detail" to detalle, "correlationId" to correlationId)
                }
                throw ex
            }

            // Paso 3: Manejo de Errores Transitorios (Exponential Backoff) o Definitivos
            if (dto.simulateError == "TRANSIENT") {
                val attempts = transientRetryAttempts.computeIfAbsent(dto.messageId) { AtomicInteger(0) }.incrementAndGet()
                if (attempts < 3) {
                    totalRetries.incrementAndGet()
                    logContextual("WARN", correlationId, "Fallo al procesar ID: ${dto.orderId}, reintentando (intento $attempts/3)...")
                    if (!enviarADltSiFalla) {
                        throw IllegalStateException("Fallo transitorio simulado (intento $attempts/3)")
                    }
                    // Simula backoff exponencial corto (50ms, 100ms) en invocación síncrona de demostración
                    return procesarMensaje(topic, key, rawPayload, correlationId, enviarADltSiFalla = true)
                }
                transientRetryAttempts.remove(dto.messageId)
                logContextual("INFO", correlationId, "Recuperado tras reintentos exponenciales para ID: ${dto.orderId}")
            } else if (dto.simulateError == "PERMANENT") {
                for (intento in 1..3) {
                    totalRetries.incrementAndGet()
                    logContextual("WARN", correlationId, "Fallo al procesar ID: ${dto.orderId}, reintentando (intento $intento/3)...")
                }
                val ex = IllegalStateException("Error de negocio permanente: stock/crédito rechazado para orden ${dto.orderId}")
                logContextual("ERROR", correlationId, "Fallo definitivo ID: ${dto.orderId}, enviando a DLT")
                if (enviarADltSiFalla) {
                    enviarADlt(topic, key, rawPayload, correlationId, ex)
                    return mapOf("status" to "SENT_TO_DLT", "reason" to "MaxAttemptsExhausted", "correlationId" to correlationId)
                }
                throw ex
            }

            // Paso 4: Idempotencia del Consumidor (Check-then-Act sobre processed_messages)
            val registro = ProcessedMessageRegistro(
                messageId = dto.messageId,
                orderId = dto.orderId,
                topic = topic
            )
            val existente = processedMessages.putIfAbsent(dto.messageId, registro)
            if (existente != null) {
                totalDuplicatesIgnored.incrementAndGet()
                logContextual("INFO", correlationId, "Idempotencia: mensaje duplicado ID: ${dto.messageId} ya existe en processed_messages -> Ignorado (f(f(x))=f(x))")
                return mapOf("status" to "DUPLICATE_IGNORED", "messageId" to dto.messageId, "correlationId" to correlationId)
            }

            totalProcessed.incrementAndGet()
            logContextual("INFO", correlationId, "Orden ${dto.orderId} procesada y confirmada en processed_messages (ID=${dto.messageId})")
            return mapOf("status" to "PROCESSED", "messageId" to dto.messageId, "orderId" to dto.orderId, "correlationId" to correlationId)
        } finally {
            if (prevMdc != null) MDC.put("correlationId", prevMdc) else MDC.remove("correlationId")
        }
    }

    fun simularEscenario(req: SimulacionErrorRequest): Map<String, Any> {
        val scenario = req.scenario.lowercase()
        val msgId = req.messageId?.takeIf { it.isNotBlank() } ?: "msg-${UUID.randomUUID().toString().take(8)}"
        val orderId = req.orderId?.takeIf { it.isNotBlank() } ?: "ORD-${(1000..9999).random()}"
        val corrId = "corr-${UUID.randomUUID().toString().take(8)}"

        val payload: String = when (scenario) {
            "poison-pill" -> "{corrupt-json-enviar-texto-a-numerico"
            "payload-invalido" -> mapper.writeValueAsString(
                EventoOrdenStreamingDto(
                    messageId = msgId,
                    orderId = orderId,
                    customerEmail = req.customerEmail ?: "correo-invalido",
                    packages = req.packages ?: 0,
                    amount = req.amount ?: -10.0,
                    correlationId = corrId
                )
            )
            "error-transitorio" -> mapper.writeValueAsString(
                EventoOrdenStreamingDto(
                    messageId = msgId,
                    orderId = orderId,
                    customerEmail = req.customerEmail ?: "cliente@rutaexpress.cl",
                    packages = req.packages ?: 2,
                    amount = req.amount ?: 15000.0,
                    simulateError = "TRANSIENT",
                    correlationId = corrId
                )
            )
            "error-definitivo-dlt" -> mapper.writeValueAsString(
                EventoOrdenStreamingDto(
                    messageId = msgId,
                    orderId = orderId,
                    customerEmail = req.customerEmail ?: "cliente@rutaexpress.cl",
                    packages = req.packages ?: 2,
                    amount = req.amount ?: 15000.0,
                    simulateError = "PERMANENT",
                    correlationId = corrId
                )
            )
            "duplicado" -> {
                val existingId = processedMessages.keys.firstOrNull() ?: msgId
                val existingOrder = processedMessages[existingId]?.orderId ?: orderId
                val json = mapper.writeValueAsString(
                    EventoOrdenStreamingDto(
                        messageId = existingId,
                        orderId = existingOrder,
                        customerEmail = "cliente@rutaexpress.cl",
                        packages = 1,
                        amount = 10000.0,
                        correlationId = corrId
                    )
                )
                if (!processedMessages.containsKey(existingId)) {
                    procesarMensaje(TOPIC_PRINCIPAL, existingOrder, json, corrId, enviarADltSiFalla = true)
                }
                json
            }
            else -> mapper.writeValueAsString(
                EventoOrdenStreamingDto(
                    messageId = msgId,
                    orderId = orderId,
                    customerEmail = req.customerEmail ?: "cliente@rutaexpress.cl",
                    packages = req.packages ?: 3,
                    amount = req.amount ?: 24500.0,
                    correlationId = corrId
                )
            )
        }

        // Publicar al tópico real si KafkaTemplate está disponible, y procesar localmente para reflejo instantáneo
        if (kafkaTemplate != null) {
            try {
                val record = ProducerRecord(TOPIC_PRINCIPAL, orderId, payload)
                record.headers().add("X-Correlation-Id", corrId.toByteArray(StandardCharsets.UTF_8))
                kafkaTemplate.send(record)
            } catch (_: Exception) {
            }
        }

        val resultado = procesarMensaje(TOPIC_PRINCIPAL, orderId, payload, corrId, enviarADltSiFalla = true)
        return mapOf(
            "success" to true,
            "scenario" to scenario,
            "result" to resultado,
            "state" to estado()
        )
    }

    fun listarDlt(): List<DeadLetterMensajeDto> = dltStore.toList()

    fun reprocesarDlt(dltId: String? = null): Map<String, Any> {
        val objetivos = if (dltId.isNullOrBlank()) {
            dltStore.filter { !it.reprocessed }
        } else {
            dltStore.filter { it.dltId == dltId && !it.reprocessed }
        }

        var reprocesados = 0
        objetivos.forEach { item ->
            val corregido = normalizarPayloadParaReproceso(item.payload, item.correlationId)
            if (kafkaTemplate != null) {
                try {
                    val record = ProducerRecord(item.originalTopic, item.key ?: item.correlationId, corregido)
                    record.headers().add("X-Correlation-Id", item.correlationId.toByteArray(StandardCharsets.UTF_8))
                    kafkaTemplate.send(record)
                } catch (_: Exception) {
                }
            }
            procesarMensaje(item.originalTopic, item.key, corregido, item.correlationId, enviarADltSiFalla = false)
            dltStore.remove(item)
            dltStore.addFirst(item.copy(reprocessed = true))
            totalReprocessed.incrementAndGet()
            reprocesados++
            logContextual("INFO", item.correlationId, "DLT reprocesado y re-inyectado a ${item.originalTopic} (dltId=${item.dltId})")
        }

        return mapOf(
            "success" to true,
            "reprocessedCount" to reprocesados,
            "state" to estado()
        )
    }

    fun estado(): ErrorHandlingStateDto = ErrorHandlingStateDto(
        mainTopic = TOPIC_PRINCIPAL,
        dltTopic = TOPIC_DLT,
        consumerGroup = CONSUMER_GROUP,
        totalReceived = totalReceived.get(),
        totalProcessed = totalProcessed.get(),
        totalDuplicatesIgnored = totalDuplicatesIgnored.get(),
        totalRetries = totalRetries.get(),
        totalSentToDlt = totalSentToDlt.get(),
        totalReprocessed = totalReprocessed.get(),
        processedMessages = processedMessages.values.sortedByDescending { it.processedAt }.take(30),
        dltMessages = dltStore.toList().take(30),
        contextualLogs = logHistory.toList().take(40)
    )

    private fun enviarADlt(topic: String, key: String?, payload: String, correlationId: String, ex: Exception) {
        val stack = ex.stackTraceToString().take(1200)
        if (kafkaTemplate != null) {
            try {
                val dltTopic = "$topic.DLT"
                val record = ProducerRecord(dltTopic, key ?: correlationId, payload)
                record.headers().add("X-Correlation-Id", correlationId.toByteArray(StandardCharsets.UTF_8))
                record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, ex.javaClass.name.toByteArray(StandardCharsets.UTF_8))
                record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, (ex.message ?: "").toByteArray(StandardCharsets.UTF_8))
                record.headers().add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, stack.toByteArray(StandardCharsets.UTF_8))
                kafkaTemplate.send(record)
            } catch (_: Exception) {
            }
        }
        registrarEnDlt(
            originalTopic = topic,
            targetDltTopic = "$topic.DLT",
            key = key,
            payload = payload,
            correlationId = correlationId,
            exClass = ex.javaClass.simpleName,
            exMessage = ex.message ?: "Error de procesamiento",
            stacktrace = stack
        )
    }

    private fun registrarEnDlt(
        originalTopic: String,
        targetDltTopic: String,
        key: String?,
        payload: String,
        correlationId: String,
        exClass: String,
        exMessage: String,
        stacktrace: String
    ) {
        totalSentToDlt.incrementAndGet()
        dltStore.addFirst(
            DeadLetterMensajeDto(
                dltId = "dlt-${UUID.randomUUID().toString().take(8)}",
                originalTopic = originalTopic,
                targetDltTopic = targetDltTopic,
                key = key,
                payload = payload,
                correlationId = correlationId,
                exceptionClass = exClass,
                exceptionMessage = exMessage,
                stacktrace = stacktrace
            )
        )
        while (dltStore.size > 50) dltStore.removeLast()
    }

    private fun normalizarPayloadParaReproceso(raw: String, correlationId: String): String {
        return try {
            val dto = mapper.readValue(raw, EventoOrdenStreamingDto::class.java)
            mapper.writeValueAsString(
                dto.copy(
                    messageId = dto.messageId.ifBlank { "msg-${UUID.randomUUID().toString().take(8)}" },
                    orderId = dto.orderId.ifBlank { "ORD-REC-${(100..999).random()}" },
                    customerEmail = if (dto.customerEmail.contains("@")) dto.customerEmail else "recuperado@rutaexpress.cl",
                    packages = dto.packages.coerceAtLeast(1),
                    amount = if (dto.amount > 0.0) dto.amount else 15000.0,
                    simulateError = null,
                    correlationId = dto.correlationId ?: correlationId
                )
            )
        } catch (_: Exception) {
            mapper.writeValueAsString(
                EventoOrdenStreamingDto(
                    messageId = "msg-rec-${UUID.randomUUID().toString().take(8)}",
                    orderId = "ORD-REC-${(100..999).random()}",
                    customerEmail = "recuperado@rutaexpress.cl",
                    packages = 1,
                    amount = 15000.0,
                    correlationId = correlationId
                )
            )
        }
    }

    private fun logContextual(level: String, correlationId: String, message: String) {
        when (level) {
            "WARN" -> logger.warn("[correlationId={}] {}", correlationId, message)
            "ERROR" -> logger.error("[correlationId={}] {}", correlationId, message)
            else -> logger.info("[correlationId={}] {}", correlationId, message)
        }
        logHistory.addFirst(LogContextualDto(level = level, correlationId = correlationId, message = message))
        while (logHistory.size > 80) logHistory.removeLast()
    }
}
