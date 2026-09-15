package com.rutaexpress.shipments.controlador

import com.rutaexpress.shipments.cliente.CapacidadNoDisponibleException
import com.rutaexpress.shipments.cliente.CatalogoNoDisponibleException
import com.rutaexpress.shipments.cliente.ServicioCatalogoNoEncontradoException
import com.rutaexpress.shipments.dominio.ErrorResponse
import com.rutaexpress.shipments.servicio.EnvioNoEncontradoException
import com.rutaexpress.shipments.servicio.TransicionInvalidaException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ManejadorGlobalExcepciones {

    @ExceptionHandler(EnvioNoEncontradoException::class, ServicioCatalogoNoEncontradoException::class)
    fun noEncontrado(ex: RuntimeException, request: HttpServletRequest) =
        respuesta(HttpStatus.NOT_FOUND, "Not Found", ex.message, request)

    @ExceptionHandler(CapacidadNoDisponibleException::class, TransicionInvalidaException::class)
    fun conflicto(ex: RuntimeException, request: HttpServletRequest) =
        respuesta(HttpStatus.CONFLICT, "Conflict", ex.message, request)

    @ExceptionHandler(IllegalArgumentException::class)
    fun solicitudInvalida(ex: IllegalArgumentException, request: HttpServletRequest) =
        respuesta(HttpStatus.BAD_REQUEST, "Bad Request", ex.message, request)

    @ExceptionHandler(CatalogoNoDisponibleException::class)
    fun catalogoCaido(ex: CatalogoNoDisponibleException, request: HttpServletRequest) =
        respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.message, request)

    @ExceptionHandler(AccessDeniedException::class)
    fun prohibido(ex: AccessDeniedException, request: HttpServletRequest) =
        respuesta(HttpStatus.FORBIDDEN, "Forbidden", "No tiene los roles requeridos para esta operación", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacion(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errores = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "inválido") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = "Error de validación en la petición",
                path = request.requestURI,
                validationErrors = errores
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun generico(ex: Exception, request: HttpServletRequest) =
        respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.message, request)

    private fun respuesta(
        status: HttpStatus,
        error: String,
        mensaje: String?,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(
        ErrorResponse(
            status = status.value(),
            error = error,
            message = mensaje ?: error,
            path = request.requestURI
        )
    )
}
