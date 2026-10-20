package com.rutaexpress.mqadmin

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** API de administración del clúster RabbitMQ. Solo el rol Admin (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/mq-admin")
class MqAdminController(
    private val monitor: MqAdminService,
    private val admin: RabbitAdminService,
) {
    @GetMapping("/cluster")
    fun cluster() = monitor.cluster()

    @GetMapping("/queues")
    fun queues() = monitor.colas()

    @GetMapping("/alerts")
    fun alerts() = monitor.alertas()

    @GetMapping("/metrics")
    fun metrics() = monitor.metricas()

    @GetMapping("/exchanges")
    fun exchanges() = admin.exchanges()

    @GetMapping("/bindings")
    fun bindings() = admin.enlaces()

    @PostMapping("/topology")
    fun reconcile() = admin.reconciliar()

    @PutMapping("/retention")
    fun retention(@Valid @RequestBody request: RetentionRequest) = monitor.aplicarRetencion(request)

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearCola(@Valid @RequestBody request: CrearColaRequest) = admin.crearCola(request)

    @DeleteMapping("/queues/{name}")
    fun eliminarCola(
        @PathVariable("name") name: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
    ) = admin.eliminarCola(name, force)

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearExchange(@Valid @RequestBody request: CrearExchangeRequest) = admin.crearExchange(request)

    @DeleteMapping("/exchanges/{name}")
    fun eliminarExchange(
        @PathVariable("name") name: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
    ) = admin.eliminarExchange(name, force)

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearEnlace(@Valid @RequestBody request: CrearBindingRequest) = admin.crearEnlace(request)

    @DeleteMapping("/bindings")
    fun eliminarEnlace(
        @RequestParam("source") source: String,
        @RequestParam("destination") destination: String,
        @RequestParam("destinationType", defaultValue = "queue") destinationType: String,
        @RequestParam("routingKey", defaultValue = "") routingKey: String,
        @RequestParam("force", defaultValue = "false") force: Boolean,
    ) = admin.eliminarEnlace(source, destination, destinationType, routingKey, force)

    @PostMapping("/exchanges/{name}/messages")
    fun publicarMensaje(
        @PathVariable("name") name: String,
        @Valid @RequestBody request: PublicarMensajeRequest,
    ) = admin.publicarMensaje(name, request)
}
