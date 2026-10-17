package com.rutaexpress.mqadmin

/** El recurso solicitado (cola, exchange, enlace) no existe en el clúster. Se responde 404. */
class RecursoNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

/** La operación choca con el estado actual (ya existe, pertenece a la topología protegida). Se responde 409. */
class ConflictoException(mensaje: String) : RuntimeException(mensaje)

/** Ningún nodo RabbitMQ responde o el broker rechazó la operación técnica. Se responde 503. */
class BrokerNoDisponibleException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)
