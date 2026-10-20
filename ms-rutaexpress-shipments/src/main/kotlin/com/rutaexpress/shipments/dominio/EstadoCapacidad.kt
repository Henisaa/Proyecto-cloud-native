package com.rutaexpress.shipments.dominio

/**
 * Estado de la reserva de capacidad de un envío, según los resultados que catalog comunica por RabbitMQ.
 *  NO_APLICA           -> el catálogo no está integrado o el envío aún no pidió reserva.
 *  PENDIENTE           -> se envió el comando capacity.reserve; falta el resultado.
 *  RESERVADA           -> catalog confirmó el cupo (catalog.capacity.reserved).
 *  RECHAZADA           -> catalog no tenía cupo (catalog.capacity.rejected).
 *  LIBERACION_PENDIENTE-> el envío fue cancelado con cupo tomado y falta confirmar la devolución (compensación).
 *  LIBERADA            -> catalog devolvió el cupo tras una cancelación (catalog.capacity.released).
 */
enum class EstadoCapacidad {
    NO_APLICA, PENDIENTE, RESERVADA, RECHAZADA, LIBERACION_PENDIENTE, LIBERADA;

    /** Un envío solo sale a bodega con el cupo confirmado, o sin integración con catálogo. */
    val permiteDespacho: Boolean
        get() = this == RESERVADA || this == NO_APLICA

    /** Mensaje para el usuario cuando la capacidad impide despachar. */
    fun motivoBloqueo(): String = when (this) {
        PENDIENTE -> "Capacidad pendiente; intente en unos segundos"
        RECHAZADA -> "Capacidad rechazada: cancele el envío o reintente la reserva"
        LIBERACION_PENDIENTE, LIBERADA -> "La capacidad del envío fue liberada; no se puede despachar"
        else -> "La capacidad no permite despachar el envío"
    }
}
