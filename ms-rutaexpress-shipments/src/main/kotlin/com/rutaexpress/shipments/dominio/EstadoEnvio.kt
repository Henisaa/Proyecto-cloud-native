package com.rutaexpress.shipments.dominio

/**
 * Máquina de estados del envío.
 * Regla del caso: no se puede pasar a EN_RUTA sin haber pasado por ACEPTADO.
 */
enum class EstadoEnvio {
    CREADO,
    ACEPTADO,
    EN_BODEGA,
    EN_RUTA,
    ENTREGADO,
    CANCELADO;

    fun puedeTransicionarA(nuevo: EstadoEnvio): Boolean = when (this) {
        CREADO -> nuevo == ACEPTADO || nuevo == CANCELADO
        ACEPTADO -> nuevo == EN_BODEGA || nuevo == CANCELADO
        EN_BODEGA -> nuevo == EN_RUTA || nuevo == CANCELADO
        EN_RUTA -> nuevo == ENTREGADO
        ENTREGADO, CANCELADO -> false
    }

    fun transicionesPermitidas(): List<EstadoEnvio> = entries.filter { puedeTransicionarA(it) }

    val esFinal: Boolean
        get() = this == ENTREGADO || this == CANCELADO

    companion object {
        fun desde(valor: String): EstadoEnvio =
            entries.firstOrNull { it.name.equals(valor.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Estado de envío inválido: '$valor'. Valores permitidos: ${entries.joinToString(", ") { it.name }}"
                )
    }
}
