package com.rutaexpress.shipments.mensajeria

import com.rutaexpress.shipments.cliente.CatalogoCliente
import com.rutaexpress.shipments.dominio.Envio
import com.rutaexpress.shipments.dominio.EnvioRepositorio
import com.rutaexpress.shipments.dominio.EstadoCapacidad
import com.rutaexpress.shipments.dominio.EstadoEnvio
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Reconciliación de la capacidad (P1-05). Los comandos hacia catalog se publican fuera de la transacción de los
 * consumidores; si el broker falla justo ahí, el cupo quedaría tomado. Cada minuto se revisa y se reenvía, de forma
 * idempotente (mismos commandId), lo que quedó sin resolver:
 *  - envíos CANCELADO con el cupo RESERVADA o LIBERACION_PENDIENTE: se reenvía la liberación;
 *  - envíos ACEPTADO con la reserva PENDIENTE sin respuesta: se reenvía el comando de reserva.
 */
@Component
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class ReconciliadorCapacidad(
    private val envios: EnvioRepositorio,
    private val mensajeria: MensajeriaPublicador,
    private val catalogo: CatalogoCliente,
    @Value("\${app.reconciliation.min-age-seconds:120}")
    private val antiguedadMinimaSegundos: Long,
) {
    private val log = LoggerFactory.getLogger(ReconciliadorCapacidad::class.java)

    @Scheduled(
        fixedDelayString = "\${app.reconciliation.interval-ms:60000}",
        initialDelayString = "\${app.reconciliation.initial-delay-ms:60000}",
    )
    fun reconciliar() {
        if (!catalogo.habilitado) return
        val limite = Instant.now().minus(Duration.ofSeconds(antiguedadMinimaSegundos))

        envios.canceladosConCupoTomado(
            EstadoEnvio.CANCELADO,
            listOf(EstadoCapacidad.RESERVADA, EstadoCapacidad.LIBERACION_PENDIENTE),
            limite,
        ).forEach { envio ->
            val commandId = if (envio.capacityStatus == EstadoCapacidad.LIBERACION_PENDIENTE) {
                IdsComando.compensacion(envio.lastReserveCommandId ?: envio.id)
            } else {
                IdsComando.liberacionPorCancelacion(envio.id)
            }
            reenviar(envio, "liberación") { mensajeria.solicitarLiberacion(envio, commandId) }
        }

        envios.reservasSinRespuesta(EstadoEnvio.ACEPTADO, EstadoCapacidad.PENDIENTE, limite).forEach { envio ->
            val commandId = envio.lastReserveCommandId ?: return@forEach
            reenviar(envio, "reserva") { mensajeria.solicitarReserva(envio, commandId) }
        }
    }

    private fun reenviar(envio: Envio, tipo: String, accion: () -> Unit) {
        try {
            accion()
            // Se marca la hora para no reenviar el mismo comando en cada pasada mientras catalog se recupera.
            envio.updatedAt = Instant.now()
            envios.save(envio)
            log.info("Reconciliación: {} reenviada para el envío {}", tipo, envio.id)
        } catch (ex: OptimisticLockingFailureException) {
            log.debug("Reconciliación: el envío {} cambió durante la revisión; se revisa en la próxima pasada", envio.id)
        } catch (ex: RuntimeException) {
            log.warn("Reconciliación: no se pudo reenviar la {} del envío {}: {}", tipo, envio.id, ex.message)
        }
    }
}
