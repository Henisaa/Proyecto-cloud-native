package com.rutaexpress.catalog.config

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
 * Declaración de la parte de la topología que usa catalog: el exchange de comandos (direct),
 * el de eventos (topic), el dead-letter exchange, la cola de comandos con su DLQ, y la plantilla
 * de publicación con confirmaciones. Los nombres provienen de RabbitNombres (application.yml).
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
    fun colaComandos(): Queue = QueueBuilder.durable(nombres.queues.command.name)
        .deadLetterExchange(nombres.exchanges.deadLetter)
        .deadLetterRoutingKey(nombres.queues.command.deadLetterKey)
        .build()

    @Bean
    fun colaComandosDlq(): Queue = QueueBuilder.durable(nombres.queues.command.dlq).build()

    @Bean
    fun enlacesComandos(colaComandos: Queue, exchangeComandos: DirectExchange): Declarables =
        Declarables(nombres.queues.command.bindings.map { clave ->
            BindingBuilder.bind(colaComandos).to(exchangeComandos).with(clave)
        })

    @Bean
    fun enlaceComandosDlq(colaComandosDlq: Queue, exchangeDeadLetter: DirectExchange): Binding =
        BindingBuilder.bind(colaComandosDlq).to(exchangeDeadLetter).with(nombres.queues.command.deadLetterKey)

    /** Publicación JSON persistente, obligatoria (mandatory) y con confirmación del broker. */
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

    /** ACK manual: el consumidor confirma o rechaza cada comando de forma explícita. */
    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setAcknowledgeMode(AcknowledgeMode.MANUAL)
            setPrefetchCount(nombres.consumer.prefetch)
            setObservationEnabled(true)
        }
}
