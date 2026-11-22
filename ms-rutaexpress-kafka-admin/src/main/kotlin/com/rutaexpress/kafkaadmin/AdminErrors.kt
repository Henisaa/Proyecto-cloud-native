package com.rutaexpress.kafkaadmin

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

@RestControllerAdvice
class AdminErrors {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(ex: IllegalArgumentException) =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Solicitud inválida")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ProblemDetail {
        val detail = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Solicitud inválida. $detail")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(ex: HttpMessageNotReadableException) =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun badParam(ex: Exception) =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parámetro inválido o faltante")

    @ExceptionHandler(ResponseStatusException::class)
    fun status(ex: ResponseStatusException) =
        ProblemDetail.forStatusAndDetail(ex.statusCode, ex.reason ?: "Error al procesar la solicitud")

    @ExceptionHandler(RestClientException::class, ExecutionException::class, TimeoutException::class)
    fun broker(ex: Exception) =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Broker no disponible; revise la conexión y credenciales del administrador")
}
