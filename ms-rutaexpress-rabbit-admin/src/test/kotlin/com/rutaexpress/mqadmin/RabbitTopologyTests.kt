package com.rutaexpress.mqadmin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange

class RabbitTopologyTests {
    private val topologia = RabbitTopology(RabbitNombres())

    @Test
    fun `declara tres colas funcionales cada una con su DLQ`() {
        val colas = topologia.declaraciones().filterIsInstance<Queue>().map { it.name }
        assertThat(colas).containsExactlyInAnyOrder(
            "q.rutaexpress.command", "q.rutaexpress.command.dlq",
            "q.rutaexpress.notification", "q.rutaexpress.notification.dlq",
            "q.rutaexpress.audit", "q.rutaexpress.audit.dlq",
        )
    }

    @Test
    fun `usa direct para comandos y topic para eventos`() {
        val declaraciones = topologia.declaraciones()
        assertThat(declaraciones.filterIsInstance<DirectExchange>().map { it.name })
            .containsExactlyInAnyOrder("ex.rutaexpress.cmd", "ex.rutaexpress.dlx")
        assertThat(declaraciones.filterIsInstance<TopicExchange>().map { it.name })
            .containsExactly("ex.rutaexpress.events")

        val enlaces = declaraciones.filterIsInstance<Binding>()
        assertThat(enlaces.filter { it.destination == "q.rutaexpress.command" && it.exchange == "ex.rutaexpress.cmd" }.map { it.routingKey })
            .containsExactlyInAnyOrder("capacity.reserve", "capacity.release")
        assertThat(enlaces).anyMatch { it.destination == "q.rutaexpress.audit" && it.exchange == "ex.rutaexpress.events" && it.routingKey == "catalog.#" }
        assertThat(enlaces).anyMatch { it.destination == "q.rutaexpress.notification" && it.exchange == "ex.rutaexpress.events" && it.routingKey == "notification.#" }
    }

    @Test
    fun `cada cola dead-letter hacia el DLX y su DLQ queda enlazada con la misma clave`() {
        val declaraciones = topologia.declaraciones()
        val principal = declaraciones.filterIsInstance<Queue>().first { it.name == "q.rutaexpress.notification" }
        assertThat(principal.arguments)
            .containsEntry("x-dead-letter-exchange", "ex.rutaexpress.dlx")
            .containsEntry("x-dead-letter-routing-key", "notification")
        assertThat(declaraciones.filterIsInstance<Binding>()).anyMatch {
            it.destination == "q.rutaexpress.notification.dlq" && it.exchange == "ex.rutaexpress.dlx" && it.routingKey == "notification"
        }
    }

    @Test
    fun `los nombres protegidos incluyen colas, DLQ y exchanges del nucleo`() {
        assertThat(topologia.nombresColasProtegidas()).hasSize(6)
        assertThat(topologia.nombresExchangesProtegidos()).containsExactlyInAnyOrder("ex.rutaexpress.cmd", "ex.rutaexpress.events", "ex.rutaexpress.dlx")
        assertThat(topologia.esEnlaceNucleo("ex.rutaexpress.cmd", "q.rutaexpress.command")).isTrue()
        assertThat(topologia.esEnlaceNucleo("ex.propio", "q.propia")).isFalse()
    }
}
