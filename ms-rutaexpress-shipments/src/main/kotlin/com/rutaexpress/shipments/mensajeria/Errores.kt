package com.rutaexpress.shipments.mensajeria

/** Mensaje que no puede procesarse aunque se reintente (formato, datos o routing key inválidos). Va a la DLQ sin reintentos. */
class MensajeNoRecuperableException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)

/**
 * El broker no confirmó una publicación hecha dentro de una operación. Se responde 503 y la transacción
 * se revierte: el envío no cambia de estado si el mensaje asociado no pudo confirmarse.
 */
class MensajeriaNoDisponibleException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)
