package com.rutaexpress.shipments.configuracion

import com.rutaexpress.shipments.mensajeria.DeadLetterRegistro
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

/**
 * Topología que usa shipments: exchange de comandos (direct), exchange de eventos (topic), dead-letter exchange,
 * la cola de auditoría con su DLQ y la plantilla de publicación con confirmaciones y mensajes persistentes.
 * Todos los nombres provienen de RabbitNombres (application.yml).
 */
@Configuration
@EnableConfigurationProperties(RabbitNombres::class)
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class RabbitMqConfig(private val nombres: RabbitNombres) {

    @Bean
    fun exchangeComandos(): DirectExchange = DirectExchange(nombres.exchanges.command, true, false)

    @Bean
    fun exchangeEventos(): TopicExchange = TopicExchange(nombres.exchanges.events, true, false)

    @Bean
    fun exchangeDeadLetter(): DirectExchange = DirectExchange(nombres.exchanges.deadLetter, true, false)

    @Bean
    fun colaAuditoria(): Queue = QueueBuilder.durable(nombres.queues.audit.name)
        .deadLetterExchange(nombres.exchanges.deadLetter)
        .deadLetterRoutingKey(nombres.queues.audit.deadLetterKey)
        .build()

    @Bean
    fun colaAuditoriaDlq(): Queue = QueueBuilder.durable(nombres.queues.audit.dlq).build()

    @Bean
    fun enlacesAuditoria(colaAuditoria: Queue, exchangeEventos: TopicExchange): Declarables =
        Declarables(nombres.queues.audit.bindings.map { patron ->
            BindingBuilder.bind(colaAuditoria).to(exchangeEventos).with(patron)
        })

    @Bean
    fun enlaceAuditoriaDlq(colaAuditoriaDlq: Queue, exchangeDeadLetter: DirectExchange): Binding =
        BindingBuilder.bind(colaAuditoriaDlq).to(exchangeDeadLetter).with(nombres.queues.audit.deadLetterKey)

    /**
     * Publicación JSON persistente y obligatoria (mandatory). El receptor infiere el tipo de su propio DTO,
     * por eso se eliminan las cabeceras __TypeId__ que añade el conversor.
     */
    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory, mapper: JsonMapper): RabbitTemplate =
        RabbitTemplate(connectionFactory).apply {
            messageConverter = JacksonJsonMessageConverter(mapper)
            setMandatory(true)
            addBeforePublishPostProcessors({ mensaje ->
                mensaje.messageProperties.headers.remove("__TypeId__")
                mensaje.messageProperties.headers.remove("__ContentTypeId__")
                mensaje.messageProperties.headers.remove("__KeyTypeId__")
                mensaje.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
                mensaje
            })
        }

    /** ACK manual: el consumidor confirma cada mensaje tras procesarlo, o lo rechaza sin reencolar. */
    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setAcknowledgeMode(AcknowledgeMode.MANUAL)
            setPrefetchCount(nombres.consumer.prefetch)
            setObservationEnabled(true)
        }
}
