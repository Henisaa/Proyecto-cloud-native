package com.rutaexpress.kafkaadmin
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
@RestControllerAdvice
class AdminErrors {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(ex: IllegalArgumentException) = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Solicitud inválida")
    @ExceptionHandler(org.springframework.web.client.RestClientException::class, java.util.concurrent.ExecutionException::class, java.util.concurrent.TimeoutException::class)
    fun broker(ex: Exception) = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Broker no disponible; revise la conexión y credenciales del administrador")
}
