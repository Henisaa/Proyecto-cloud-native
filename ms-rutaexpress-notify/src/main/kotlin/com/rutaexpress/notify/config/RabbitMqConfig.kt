package com.rutaexpress.notify.config

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declaración de la parte de la topología que consume notify: el exchange de eventos (topic),
 * el dead-letter exchange, la cola de notificaciones con su DLQ y los enlaces. Sin nombres literales:
 * todo sale de RabbitNombres (application.properties).
 */
@Configuration
@EnableConfigurationProperties(RabbitNombres::class)
class RabbitMqConfig(private val nombres: RabbitNombres) {

    @Bean
    fun exchangeEventos(): TopicExchange = TopicExchange(nombres.exchanges.events, true, false)

    @Bean
    fun exchangeDeadLetter(): DirectExchange = DirectExchange(nombres.exchanges.deadLetter, true, false)

    @Bean
    fun colaNotificaciones(): Queue = QueueBuilder.durable(nombres.queues.notification.name)
        .deadLetterExchange(nombres.exchanges.deadLetter)
        .deadLetterRoutingKey(nombres.queues.notification.deadLetterKey)
        .build()

    @Bean
    fun colaNotificacionesDlq(): Queue = QueueBuilder.durable(nombres.queues.notification.dlq).build()

    @Bean
    fun enlacesNotificaciones(colaNotificaciones: Queue, exchangeEventos: TopicExchange): Declarables =
        Declarables(nombres.queues.notification.bindings.map { patron ->
            BindingBuilder.bind(colaNotificaciones).to(exchangeEventos).with(patron)
        })

    @Bean
    fun enlaceNotificacionesDlq(colaNotificacionesDlq: Queue, exchangeDeadLetter: DirectExchange): Binding =
        BindingBuilder.bind(colaNotificacionesDlq).to(exchangeDeadLetter).with(nombres.queues.notification.deadLetterKey)

    /** ACK manual: el consumidor confirma o rechaza cada mensaje de forma explícita. */
    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setAcknowledgeMode(AcknowledgeMode.MANUAL)
            setPrefetchCount(nombres.consumer.prefetch)
            setObservationEnabled(true)
        }
}
