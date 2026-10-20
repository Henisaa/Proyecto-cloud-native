package com.rutaexpress.catalog.view.controller

import com.rutaexpress.catalog.viewmodel.response.ErrorResponseViewModel
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Resource not found: {}", ex.message)
        val error = ErrorResponseViewModel(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: "Resource not found",
            path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Bad request: {}", ex.message)
        val isConflict = ex.message?.contains("already exists", ignoreCase = true) == true
        val status = if (isConflict) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST

        val error = ErrorResponseViewModel(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message ?: "Invalid request argument",
            path = request.requestURI
        )
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("State conflict: {}", ex.message)
        val error = ErrorResponseViewModel(
            status = HttpStatus.CONFLICT.value(),
            error = HttpStatus.CONFLICT.reasonPhrase,
            message = ex.message ?: "State conflict occurred",
            path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: org.springframework.http.converter.HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Malformed JSON request: {}", ex.message)
        val error = ErrorResponseViewModel(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Malformed or unreadable JSON request body",
            path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        val fieldErrors = mutableMapOf<String, String>()
        ex.bindingResult.allErrors.forEach { err ->
            val fieldName = (err as? FieldError)?.field ?: err.objectName
            val message = err.defaultMessage ?: "Validation failure"
            fieldErrors[fieldName] = message
        }

        logger.warn("Validation failed for {}: {}", request.requestURI, fieldErrors)
        val error = ErrorResponseViewModel(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed for request body",
            path = request.requestURI,
            validationErrors = fieldErrors
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Access denied for {}: {}", request.requestURI, ex.message)
        val error = ErrorResponseViewModel(
            status = HttpStatus.FORBIDDEN.value(),
            error = HttpStatus.FORBIDDEN.reasonPhrase,
            message = "Access denied: insufficient permissions or required role missing",
            path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Authentication failure for {}: {}", request.requestURI, ex.message)
        val error = ErrorResponseViewModel(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            message = "Authentication required or invalid JWT token",
            path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(error)
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: org.springframework.dao.OptimisticLockingFailureException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Optimistic lock conflict at {}: {}", request.requestURI, ex.message)
        return respond(HttpStatus.CONFLICT, "La capacidad cambió mientras se editaba; recargue e intente de nuevo", request)
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun handleIntegrity(ex: org.springframework.dao.DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Integrity violation at {}: {}", request.requestURI, ex.message)
        return respond(HttpStatus.CONFLICT, "La operación viola una restricción de datos", request)
    }

    @ExceptionHandler(
        org.springframework.web.bind.MissingServletRequestParameterException::class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class,
    )
    fun handleBadParameter(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        logger.warn("Invalid parameter at {}: {}", request.requestURI, ex.message)
        return respond(HttpStatus.BAD_REQUEST, "Parámetro de la solicitud ausente o con un tipo inválido", request)
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: org.springframework.web.HttpRequestMethodNotSupportedException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> =
        respond(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para esta ruta", request)

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException::class)
    fun handleNoResource(ex: org.springframework.web.servlet.resource.NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> =
        respond(HttpStatus.NOT_FOUND, "Ruta inexistente", request)

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> {
        val correlationId = java.util.UUID.randomUUID().toString()
        logger.error("Internal error [{}] at {}: {}", correlationId, request.requestURI, ex.message, ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Referencia para soporte: $correlationId", request)
    }

    private fun respond(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ErrorResponseViewModel> =
        ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(
            ErrorResponseViewModel(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI
            )
        )
}
