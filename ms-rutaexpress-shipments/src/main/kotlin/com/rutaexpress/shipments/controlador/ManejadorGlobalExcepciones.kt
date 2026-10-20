package com.rutaexpress.shipments.controlador

import com.rutaexpress.shipments.cliente.CapacidadNoDisponibleException
import com.rutaexpress.shipments.cliente.CatalogoNoDisponibleException
import com.rutaexpress.shipments.cliente.ServicioCatalogoNoEncontradoException
import com.rutaexpress.shipments.dominio.ErrorResponse
import com.rutaexpress.shipments.mensajeria.MensajeriaNoDisponibleException
import com.rutaexpress.shipments.servicio.EnvioNoEncontradoException
import com.rutaexpress.shipments.servicio.TransicionInvalidaException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.UUID

/**
 * Traduce cada excepción a un código HTTP estable. Los errores inesperados responden 500 con un mensaje genérico
 * y un identificador de correlación; el detalle solo va al log.
 */
@RestControllerAdvice
class ManejadorGlobalExcepciones {
    private val log = LoggerFactory.getLogger(ManejadorGlobalExcepciones::class.java)

    @ExceptionHandler(EnvioNoEncontradoException::class, ServicioCatalogoNoEncontradoException::class)
    fun noEncontrado(ex: RuntimeException, request: HttpServletRequest) =
        respuesta(HttpStatus.NOT_FOUND, "Not Found", ex.message, request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun rutaInexistente(ex: NoResourceFoundException, request: HttpServletRequest) =
        respuesta(HttpStatus.NOT_FOUND, "Not Found", "Ruta inexistente", request)

    @ExceptionHandler(CapacidadNoDisponibleException::class, TransicionInvalidaException::class)
    fun conflicto(ex: RuntimeException, request: HttpServletRequest) =
        respuesta(HttpStatus.CONFLICT, "Conflict", ex.message, request)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun conflictoDeVersion(ex: OptimisticLockingFailureException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Conflicto de versión en {}: {}", request.requestURI, ex.message)
        return respuesta(HttpStatus.CONFLICT, "Conflict", "El envío cambió mientras se procesaba; recargue e intente de nuevo", request)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integridad(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Violación de integridad en {}: {}", request.requestURI, ex.mostSpecificCause.message)
        return respuesta(HttpStatus.CONFLICT, "Conflict", "La operación viola una restricción de datos", request)
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun solicitudInvalida(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val mensaje = when (ex) {
            is IllegalArgumentException -> ex.message
            is HttpMessageNotReadableException -> "El cuerpo de la solicitud no es un JSON válido"
            is MethodArgumentTypeMismatchException -> "El parámetro '${ex.name}' tiene un tipo o formato inválido"
            is MissingServletRequestParameterException -> "Falta el parámetro obligatorio '${ex.parameterName}'"
            else -> null
        }
        return respuesta(HttpStatus.BAD_REQUEST, "Bad Request", mensaje, request)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metodoNoPermitido(ex: HttpRequestMethodNotSupportedException, request: HttpServletRequest) =
        respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "Método HTTP no permitido para esta ruta", request)

    @ExceptionHandler(CatalogoNoDisponibleException::class)
    fun catalogoCaido(ex: CatalogoNoDisponibleException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Catálogo no disponible: {}", ex.message)
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "El catálogo no está disponible; reintente en unos segundos", request)
    }

    @ExceptionHandler(MensajeriaNoDisponibleException::class)
    fun mensajeriaCaida(ex: MensajeriaNoDisponibleException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Mensajería no disponible: {}", ex.message)
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "La mensajería no está disponible; la operación no se realizó, reintente", request)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun prohibido(ex: AccessDeniedException, request: HttpServletRequest) =
        respuesta(HttpStatus.FORBIDDEN, "Forbidden", "No tiene los roles requeridos para esta operación", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacion(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errores = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "inválido") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(
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
    fun generico(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val correlacion = UUID.randomUUID().toString()
        log.error("Error inesperado [{}] en {}: {}", correlacion, request.requestURI, ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(
            ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "Error interno. Indique el código $correlacion a soporte.",
                path = request.requestURI,
                correlationId = correlacion,
            )
        )
    }

    private fun respuesta(
        status: HttpStatus,
        error: String,
        mensaje: String?,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(
        ErrorResponse(
            status = status.value(),
            error = error,
            message = mensaje ?: error,
            path = request.requestURI
        )
    )
}
