package com.rutaexpress.demo

import com.rutaexpress.demo.config.RabbitMqConfig
import com.rutaexpress.demo.config.RabbitNombres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Binding

class RabbitMqConfigTests {
    private val config = RabbitMqConfig(RabbitNombres())

    @Test
    fun `la cola de notificaciones envia sus rechazos al dead-letter exchange con su clave`() {
        val cola = config.colaNotificaciones()

        assertThat(cola.name).isEqualTo("q.rutaexpress.notification")
        assertThat(cola.arguments)
            .containsEntry("x-dead-letter-exchange", "ex.rutaexpress.dlx")
            .containsEntry("x-dead-letter-routing-key", "notification")
    }

    @Test
    fun `la DLQ queda enlazada al dead-letter exchange con la misma clave`() {
        val dlq = config.colaNotificacionesDlq()
        val enlace = config.enlaceNotificacionesDlq(dlq, config.exchangeDeadLetter())

        assertThat(dlq.name).isEqualTo("q.rutaexpress.notification.dlq")
        assertThat(enlace.destination).isEqualTo("q.rutaexpress.notification.dlq")
        assertThat(enlace.exchange).isEqualTo("ex.rutaexpress.dlx")
        assertThat(enlace.routingKey).isEqualTo("notification")
    }

    @Test
    fun `enlaza la cola al exchange de eventos con el patron notification`() {
        val enlaces = config.enlacesNotificaciones(config.colaNotificaciones(), config.exchangeEventos())
            .declarables.filterIsInstance<Binding>()

        assertThat(enlaces.map { it.exchange }).containsOnly("ex.rutaexpress.events")
        assertThat(enlaces.map { it.routingKey }).containsExactly("notification.#")
    }
}
