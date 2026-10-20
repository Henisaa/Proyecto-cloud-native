package com.rutaexpress.mqadmin

import org.springframework.amqp.AmqpException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException

/** Respuestas de error en formato ProblemDetail con un detalle legible para el administrador. */
@RestControllerAdvice
class AdminErrors {

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalida(ex: IllegalArgumentException) = problema(HttpStatus.BAD_REQUEST, ex.message ?: "Solicitud inválida")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacion(ex: MethodArgumentNotValidException): ProblemDetail {
        val detalle = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida. $detalle")
    }

    @ExceptionHandler(RecursoNoEncontradoException::class)
    fun noEncontrado(ex: RecursoNoEncontradoException) = problema(HttpStatus.NOT_FOUND, ex.message)

    @ExceptionHandler(ConflictoException::class)
    fun conflicto(ex: ConflictoException) = problema(HttpStatus.CONFLICT, ex.message)

    @ExceptionHandler(BrokerNoDisponibleException::class)
    fun brokerNoDisponible(ex: BrokerNoDisponibleException) = problema(HttpStatus.SERVICE_UNAVAILABLE, ex.message)

    @ExceptionHandler(AmqpException::class, RestClientException::class)
    fun brokerCaido(ex: Exception) = problema(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Broker no disponible; revise la conexión y las credenciales del administrador",
    )

    private fun problema(estado: HttpStatus, detalle: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(estado, detalle ?: estado.reasonPhrase)
}
