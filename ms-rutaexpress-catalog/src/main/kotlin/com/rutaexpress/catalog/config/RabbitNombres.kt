package com.rutaexpress.catalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Nombres centralizados de la topología RabbitMQ de catalog (prefijo app.rabbit en application.yml).
 * Los mismos valores se declaran en los demás microservicios; ninguna clase los escribe a mano.
 */
@ConfigurationProperties("app.rabbit")
data class RabbitNombres(
    val exchanges: Exchanges = Exchanges(),
    val queues: Colas = Colas(),
    val routing: Enrutamiento = Enrutamiento(),
    val consumer: Consumidor = Consumidor(),
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
    )

    /** name: cola; dlq: su DLQ; deadLetterKey: clave con la que la cola envía sus rechazos al DLX; bindings: claves del direct. */
    data class Cola(
        val name: String = "",
        val dlq: String = "",
        val deadLetterKey: String = "",
        val bindings: List<String> = emptyList(),
    )

    data class Enrutamiento(
        val capacityReserve: String = "capacity.reserve",
        val capacityRelease: String = "capacity.release",
        val capacityReserved: String = "catalog.capacity.reserved",
        val capacityRejected: String = "catalog.capacity.rejected",
        val capacityReleased: String = "catalog.capacity.released",
    )

    data class Consumidor(
        /** Mensajes no confirmados que el broker entrega a la vez (QoS). */
        val prefetch: Int = 10,
        /** Intentos para errores recuperables antes de enviar el mensaje a la DLQ. */
        val maxAttempts: Int = 3,
        /** Espera inicial del backoff exponencial entre reintentos. */
        val initialIntervalMs: Long = 500,
    )
}
