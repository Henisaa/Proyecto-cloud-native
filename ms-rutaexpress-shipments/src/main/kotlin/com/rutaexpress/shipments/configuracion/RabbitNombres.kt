package com.rutaexpress.shipments.configuracion

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Nombres centralizados de la topología RabbitMQ de shipments (prefijo app.rabbit en application.yml).
 * shipments publica comandos y eventos, y consume la cola de auditoría donde llegan los resultados de catalog.
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
        val audit: Cola = Cola(
            name = "q.rutaexpress.audit",
            dlq = "q.rutaexpress.audit.dlq",
            deadLetterKey = "audit",
            bindings = listOf("shipment.#", "catalog.#"),
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
        val capacityReserve: String = "capacity.reserve",
        val capacityRelease: String = "capacity.release",
        val capacityReserved: String = "catalog.capacity.reserved",
        val capacityRejected: String = "catalog.capacity.rejected",
        val capacityReleased: String = "catalog.capacity.released",
        val capacityResultPrefix: String = "catalog.capacity.",
        val shipmentStatusPrefix: String = "shipment.status.",
        val notificationEmail: String = "notification.email.send",
        val notificationTicket: String = "notification.warehouse.ticket",
        val notificationLabel: String = "notification.label.generate",
    )

    data class Consumidor(
        val prefetch: Int = 10,
        val maxAttempts: Int = 3,
        val initialIntervalMs: Long = 500,
    )
}
