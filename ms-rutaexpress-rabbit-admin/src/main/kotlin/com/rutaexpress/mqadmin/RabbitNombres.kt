package com.rutaexpress.mqadmin

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Nombres centralizados de la topología RabbitMQ de RutaExpress (prefijo app.rabbit).
 * Cada microservicio lee los mismos valores de su propio application.yml/.properties;
 * ningún productor ni consumidor declara nombres de colas o exchanges en el código.
 */
@ConfigurationProperties("app.rabbit")
data class RabbitNombres(
    val exchanges: Exchanges = Exchanges(),
    val queues: Colas = Colas(),
) {
    data class Exchanges(
        val command: String = "ex.rutaexpress.cmd",
        val events: String = "ex.rutaexpress.events",
        val deadLetter: String = "ex.rutaexpress.dlx",
    )

    data class Colas(
        val command: Cola = Cola(
            name = "q.rutaexpress.command",
            dlq = "q.rutaexpress.command.dlq",
            deadLetterKey = "command",
            bindings = listOf("capacity.reserve", "capacity.release"),
        ),
        val notification: Cola = Cola(
            name = "q.rutaexpress.notification",
            dlq = "q.rutaexpress.notification.dlq",
            deadLetterKey = "notification",
            bindings = listOf("notification.#"),
        ),
        val audit: Cola = Cola(
            name = "q.rutaexpress.audit",
            dlq = "q.rutaexpress.audit.dlq",
            deadLetterKey = "audit",
            bindings = listOf("shipment.#", "catalog.#"),
        ),
    ) {
        fun todas(): List<Cola> = listOf(command, notification, audit)
    }

    /** name: cola funcional; dlq: su cola de mensajes no entregados; bindings: patrones de enlace al exchange. */
    data class Cola(
        val name: String = "",
        val dlq: String = "",
        val deadLetterKey: String = "",
        val bindings: List<String> = emptyList(),
    )
}
