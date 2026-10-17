package com.rutaexpress.demo.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Nombres centralizados de la topología RabbitMQ que usa notify (prefijo app.rabbit en application.properties).
 * Los mismos nombres se declaran en los demás microservicios; ninguna clase los escribe a mano.
 */
@ConfigurationProperties("app.rabbit")
data class RabbitNombres(
    val exchanges: Exchanges = Exchanges(),
    val queues: Colas = Colas(),
    val routing: Enrutamiento = Enrutamiento(),
    val consumer: Consumidor = Consumidor(),
) {
    data class Exchanges(
        val events: String = "ex.rutaexpress.events",
        val deadLetter: String = "ex.rutaexpress.dlx",
    )

    data class Colas(
        val notification: Cola = Cola(
            name = "q.rutaexpress.notification",
            dlq = "q.rutaexpress.notification.dlq",
            deadLetterKey = "notification",
            bindings = listOf("notification.#"),
        ),
    )

    /** name: cola; dlq: su DLQ; deadLetterKey: clave con la que la cola envía sus rechazos al DLX; bindings: patrones del topic. */
    data class Cola(
        val name: String = "",
        val dlq: String = "",
        val deadLetterKey: String = "",
        val bindings: List<String> = emptyList(),
    )

    data class Enrutamiento(
        val notificationEmail: String = "notification.email.send",
        val notificationTicket: String = "notification.warehouse.ticket",
        val notificationLabel: String = "notification.label.generate",
    )

    data class Consumidor(
        /** Mensajes no confirmados que el broker entrega a la vez a un consumidor (QoS). */
        val prefetch: Int = 10,
    )
}
