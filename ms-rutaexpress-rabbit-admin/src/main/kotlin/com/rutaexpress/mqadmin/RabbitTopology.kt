package com.rutaexpress.mqadmin

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Topología fija del clúster: tres exchanges, tres colas funcionales y una DLQ por cola.
 *  - exchange de comandos (direct): comandos punto a punto; la cola de capacidad la consume catalog.
 *  - exchange de eventos (topic): eventos y solicitudes de notificación enrutadas por patrones.
 *  - dead-letter exchange (direct): recibe los rechazos de cada cola y los entrega a su DLQ.
 */
@Configuration
@EnableConfigurationProperties(RabbitNombres::class)
class RabbitTopology(private val nombres: RabbitNombres) {

    private val comandos = DirectExchange(nombres.exchanges.command, true, false)
    private val eventos = TopicExchange(nombres.exchanges.events, true, false)
    private val deadLetter = DirectExchange(nombres.exchanges.deadLetter, true, false)

    @Bean
    fun exchangeComandos(): DirectExchange = comandos

    @Bean
    fun exchangeEventos(): TopicExchange = eventos

    @Bean
    fun exchangeDeadLetter(): DirectExchange = deadLetter

    @Bean
    fun declaracionesNucleo(): Declarables = Declarables(declaraciones())

    /** Declaraciones idempotentes de la topología fija; RabbitAdmin las aplica al conectar y en POST /topology. */
    fun declaraciones(): List<Declarable> = buildList<Declarable> {
        add(comandos)
        add(eventos)
        add(deadLetter)
        for (cola in nombres.queues.todas()) {
            val origen: Exchange = if (cola.name == nombres.queues.command.name) comandos else eventos
            val principal = QueueBuilder.durable(cola.name)
                .deadLetterExchange(deadLetter.name)
                .deadLetterRoutingKey(cola.deadLetterKey)
                .build()
            val dlq = QueueBuilder.durable(cola.dlq).build()
            add(principal)
            add(dlq)
            cola.bindings.forEach { clave -> add(BindingBuilder.bind(principal).to(origen).with(clave).noargs()) }
            add(BindingBuilder.bind(dlq).to(deadLetter).with(cola.deadLetterKey))
        }
    }

    fun nombresExchangesProtegidos(): Set<String> =
        setOf(nombres.exchanges.command, nombres.exchanges.events, nombres.exchanges.deadLetter)

    fun nombresColasProtegidas(): Set<String> =
        nombres.queues.todas().flatMap { listOf(it.name, it.dlq) }.toSet()

    fun colasDeMonitoreo(): List<String> = nombresColasProtegidas().sorted()

    fun colasDeTrabajo(): List<String> = nombres.queues.todas().map { it.name }

    fun dlqs(): List<String> = nombres.queues.todas().map { it.dlq }

    /** Un enlace es parte del núcleo si une un exchange fijo con una cola fija. */
    fun esEnlaceNucleo(origen: String, destino: String): Boolean =
        origen in nombresExchangesProtegidos() && destino in nombresColasProtegidas()
}
