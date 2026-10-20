package com.rutaexpress.mqadmin

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** Cuerpo de POST /api/mq-admin/queues. Los campos opcionales de retención se traducen a argumentos de la cola. */
data class CrearColaRequest(
    @field:NotBlank(message = "El nombre de la cola es obligatorio")
    val name: String? = null,
    val durable: Boolean = true,
    /** Tiempo de vida de cada mensaje (x-message-ttl), entre 1 s y 30 días. */
    val ttlMs: Long? = null,
    /** Límite de mensajes (x-max-length), entre 1 y 1.000.000. */
    val maxLength: Int? = null,
    /** Tiempo sin consumidores tras el cual RabbitMQ elimina la cola (x-expires). */
    val expiresMs: Long? = null,
    /** reject-publish (rechaza nuevos) o drop-head (descarta los más antiguos) al alcanzar maxLength. */
    val overflow: String? = null,
    val deadLetterExchange: String? = null,
    val deadLetterRoutingKey: String? = null,
)

data class CrearExchangeRequest(
    @field:NotBlank(message = "El nombre del exchange es obligatorio")
    val name: String? = null,
    /** direct, topic, fanout o headers. */
    val type: String? = null,
    val durable: Boolean = true,
)

data class CrearBindingRequest(
    @field:NotBlank(message = "El exchange de origen es obligatorio")
    val source: String? = null,
    @field:NotBlank(message = "El destino es obligatorio")
    val destination: String? = null,
    /** queue o exchange. */
    val destinationType: String = "queue",
    val routingKey: String = "",
)

data class PublicarMensajeRequest(
    val routingKey: String = "",
    @field:NotNull(message = "El cuerpo del mensaje es obligatorio")
    val body: String? = null,
)

/** Cuerpo de PUT /api/mq-admin/retention: políticas de TTL y límite para trabajo y DLQ. */
data class RetentionRequest(
    @field:Min(1000) @field:Max(2592000000)
    val commandTtlMs: Long = 86400000,
    @field:Min(1000) @field:Max(2592000000)
    val dlqTtlMs: Long = 604800000,
    @field:Min(1) @field:Max(1000000)
    val maxLength: Int = 10000,
)
