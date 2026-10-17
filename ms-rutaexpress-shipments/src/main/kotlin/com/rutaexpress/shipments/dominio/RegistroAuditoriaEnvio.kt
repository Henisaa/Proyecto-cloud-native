package com.rutaexpress.shipments.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Traza de auditoría local de los eventos que shipments consume de la cola de auditoría.
 * eventId es la clave: un mismo evento entregado dos veces se registra una sola vez.
 */
@Entity
@Table(name = "tb_auditoria_envio")
class RegistroAuditoriaEnvio(
    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    var eventId: String = "",

    @Column(name = "shipment_id", length = 36, nullable = false)
    var shipmentId: String = "",

    @Column(name = "event_type", length = 80, nullable = false)
    var tipoEvento: String = "",

    @Column(name = "routing_key", length = 120, nullable = false)
    var routingKey: String = "",

    @Column(name = "detail", length = 250)
    var detalle: String? = null,

    @Column(name = "occurred_at", nullable = false)
    var ocurridoEn: Instant = Instant.now(),

    @Column(name = "received_at", nullable = false)
    var recibidoEn: Instant = Instant.now(),
)
