package com.rutaexpress.shipments.mensajeria

import java.util.UUID

/**
 * Identificadores deterministas de los comandos de liberación. Un UUID basado en nombre (versión 3) siempre mide
 * 36 caracteres y es igual en cada reintento: reenviar la liberación nunca descuenta dos veces en catalog.
 */
object IdsComando {
    private const val SUFIJO_RESULTADO = ":resultado"

    /** Liberación pedida al cancelar un envío con el cupo reservado. */
    fun liberacionPorCancelacion(envioId: String): String = uuid("liberar:$envioId")

    /** Compensación de una reserva que llegó después de cancelar; deriva del commandId de la reserva. */
    fun compensacion(reservaCommandId: String): String = uuid("compensacion:$reservaCommandId")

    /** Aviso al cliente de que su reserva fue rechazada; deriva del evento de resultado. */
    fun avisoRechazo(eventIdResultado: String): String = uuid("rechazo:$eventIdResultado")

    /** El eventId de un resultado de catalog es "<commandId>:resultado". */
    fun commandIdDeResultado(eventId: String): String = eventId.removeSuffix(SUFIJO_RESULTADO)

    private fun uuid(texto: String): String = UUID.nameUUIDFromBytes(texto.toByteArray(Charsets.UTF_8)).toString()
}
