package com.rutaexpress.mqadmin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Al terminar de arrancar aplica la topología fija y las políticas de retención, de forma idempotente (P0-07).
 * Si RabbitMQ aún no responde se reintenta en un hilo aparte, sin retrasar el arranque ni el healthcheck.
 * En un clúster nuevo, Policies muestra rutaexpress.trabajo y rutaexpress.dlq sin llamar a ningún endpoint.
 */
@Component
class TopologiaInicial(
    private val admin: RabbitAdminService,
    @Value("\${app.mq.apply-on-startup:true}") private val habilitado: Boolean,
    @Value("\${app.mq.startup-retries:20}") private val intentos: Int,
    @Value("\${app.mq.startup-retry-delay-ms:15000}") private val esperaMs: Long,
) {
    private val log = LoggerFactory.getLogger(TopologiaInicial::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun alArrancar() {
        if (!habilitado) return
        Thread({ aplicarConReintentos() }, "topologia-inicial").apply { isDaemon = true }.start()
    }

    fun aplicarConReintentos() {
        for (intento in 1..intentos) {
            try {
                admin.reconciliar()
                log.info("Topología y políticas de retención aplicadas (intento {})", intento)
                return
            } catch (ex: ConflictoException) {
                // Una cola base existe con otros argumentos: reintentar no lo arregla; se deja para el operador.
                log.error("No se pudo aplicar la topología inicial: {}", ex.message)
                return
            } catch (ex: RuntimeException) {
                log.warn("RabbitMQ aún no está listo para la topología inicial (intento {} de {}): {}", intento, intentos, ex.message)
                try {
                    Thread.sleep(esperaMs)
                } catch (interrumpido: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
        log.error("La topología inicial no se pudo aplicar tras {} intentos; use POST /api/mq-admin/topology", intentos)
    }
}
