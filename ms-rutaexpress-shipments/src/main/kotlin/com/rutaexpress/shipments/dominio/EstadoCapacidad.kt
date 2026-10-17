package com.rutaexpress.shipments.dominio

/**
 * Estado de la reserva de capacidad de un envío, según los resultados que catalog comunica por RabbitMQ.
 *  NO_APLICA  -> el catálogo no está integrado o el envío aún no pidió reserva.
 *  PENDIENTE  -> se envió el comando capacity.reserve; falta el resultado.
 *  RESERVADA  -> catalog confirmó el cupo (catalog.capacity.reserved).
 *  RECHAZADA  -> catalog no tenía cupo (catalog.capacity.rejected).
 *  LIBERADA   -> catalog devolvió el cupo tras una cancelación (catalog.capacity.released).
 */
enum class EstadoCapacidad { NO_APLICA, PENDIENTE, RESERVADA, RECHAZADA, LIBERADA }
