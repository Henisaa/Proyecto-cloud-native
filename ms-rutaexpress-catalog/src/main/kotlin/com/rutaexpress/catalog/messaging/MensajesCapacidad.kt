package com.rutaexpress.catalog.messaging

/** Tipo de comando que principal envía a catalog por la cola de comandos. */
enum class TipoComando { RESERVAR, LIBERAR }

/** Resultado de un comando de capacidad; se publica como evento catalog.capacity.* en el exchange de eventos. */
enum class EstadoCapacidad { RESERVADA, RECHAZADA, LIBERADA }

/** Comando ya interpretado y validado. commandId es el eventId del sobre: clave de idempotencia. */
data class ComandoCapacidad(
    val commandId: String,
    val tipo: TipoComando,
    val shipmentId: String,
    val serviceId: Long,
    val zona: String,
    val vehicleType: String?,
    val packagesCount: Int,
    val weightKg: Double,
    val volumeM3: Double,
)

/** Payload tal como llega en el JSON. Los valores por defecto permiten validar campos ausentes con un mensaje claro. */
data class PayloadComandoCapacidad(
    val shipmentId: String = "",
    val serviceId: Long = 0,
    val zone: String = "",
    val vehicleType: String? = null,
    val packagesCount: Int = 0,
    val weightKg: Double = 0.0,
    val volumeM3: Double = 0.0,
)

data class ResultadoCapacidad(
    val commandId: String,
    val shipmentId: String,
    val serviceId: Long,
    val estado: EstadoCapacidad,
    val vehicleType: String?,
    val capacidadRestante: Int,
    val detalle: String,
)

/** Mensaje que no puede procesarse aunque se reintente (formato, datos o routing key inválidos). Va a la DLQ sin reintentos. */
class MensajeNoRecuperableException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)

/** El broker no confirmó la publicación de un evento. Es recuperable: se reintenta de forma acotada. */
class MensajeriaNoDisponibleException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)
