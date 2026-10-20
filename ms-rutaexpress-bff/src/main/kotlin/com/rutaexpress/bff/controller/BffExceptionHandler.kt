package com.rutaexpress.bff.controller

import com.rutaexpress.bff.service.DownstreamException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI
import java.util.UUID

/**
 * Formato de error único del BFF (ProblemDetail). Cuando un microservicio responde con error, su código y su
 * cuerpo se reenvían tal cual: la interfaz muestra el mensaje exacto del backend.
 */
@RestControllerAdvice
class BffExceptionHandler {
    private val log = LoggerFactory.getLogger(BffExceptionHandler::class.java)

    @ExceptionHandler(DownstreamException::class)
    fun servicioInterno(ex: DownstreamException, request: HttpServletRequest): ResponseEntity<Any> {
        val cuerpo = ex.body?.trim()
        if (!cuerpo.isNullOrEmpty() && (cuerpo.startsWith("{") || cuerpo.startsWith("["))) {
            return ResponseEntity.status(ex.status).contentType(MediaType.APPLICATION_JSON).body(cuerpo)
        }
        val detalle = when (ex.status.value()) {
            503 -> "El servicio ${ex.servicio} no está disponible; reintente en unos segundos"
            504 -> "El servicio ${ex.servicio} tardó demasiado en responder"
            else -> "El servicio ${ex.servicio} respondió con un error"
        }
        return problema(HttpStatus.valueOf(ex.status.value()), detalle, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun cuerpoIlegible(ex: HttpMessageNotReadableException, request: HttpServletRequest) =
        problema(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun parametroInvalido(ex: Exception, request: HttpServletRequest) =
        problema(HttpStatus.BAD_REQUEST, "Parámetro de la solicitud ausente o con un tipo inválido", request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metodoNoPermitido(ex: HttpRequestMethodNotSupportedException, request: HttpServletRequest) =
        problema(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para esta ruta", request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun rutaInexistente(ex: NoResourceFoundException, request: HttpServletRequest) =
        problema(HttpStatus.NOT_FOUND, "Ruta inexistente", request)

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException::class)
    fun conEstado(ex: org.springframework.web.server.ResponseStatusException, request: HttpServletRequest) =
        problema(HttpStatus.valueOf(ex.statusCode.value()), ex.reason ?: "Solicitud no válida", request)

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun prohibido(ex: org.springframework.security.access.AccessDeniedException, request: HttpServletRequest) =
        problema(HttpStatus.FORBIDDEN, "No tiene los roles requeridos para esta operación", request)

    @ExceptionHandler(Exception::class)
    fun inesperado(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        val correlacion = UUID.randomUUID().toString()
        log.error("Error inesperado [{}] en {}", correlacion, request.requestURI, ex)
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Indique el código $correlacion a soporte.", request, correlacion)
    }

    private fun problema(
        status: HttpStatus,
        detalle: String,
        request: HttpServletRequest,
        correlacion: String? = null,
    ): ResponseEntity<Any> {
        val problema = ProblemDetail.forStatusAndDetail(status, detalle).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            // Los mismos datos con los nombres del formato anterior, para los clientes que aún los leen.
            setProperty("error", status.reasonPhrase)
            setProperty("message", detalle)
            correlacion?.let { setProperty("correlationId", it) }
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema)
    }
}
