package com.rutaexpress.demo.viewmodel

import com.rabbitmq.client.Channel
import com.rutaexpress.demo.config.NotificationProperties
import com.rutaexpress.demo.config.RabbitNombres
import com.rutaexpress.demo.model.EmailNotification
import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.ShippingLabel
import com.rutaexpress.demo.model.WarehouseTicket
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets

/**
 * Consumidor del dominio de notificaciones. Una cola (q.rutaexpress.notification) recibe tres tipos de mensaje,
 * que se despachan por routing key. Reglas de confiabilidad:
 *  - ACK manual solo después de procesar con éxito.
 *  - Error no recuperable (JSON inválido, datos que no cumplen la validación, routing key desconocida):
 *    NACK sin requeue de inmediato, sin reintentos.
 *  - Error recuperable (SMTP, Redis, red): reintentos acotados con backoff; si se agotan, NACK sin requeue.
 *  - Todo NACK sin requeue deja el mensaje en la DLQ de la cola, donde DeadLetterRegistro lo registra en logs.
 */
@Component
class NotificationConsumerViewModel(
    private val emailViewModel: EmailNotificationViewModel,
    private val warehouseViewModel: WarehouseNotificationViewModel,
    private val properties: NotificationProperties,
    private val nombres: RabbitNombres,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(NotificationConsumerViewModel::class.java)

    @RabbitListener(queues = ["\${app.rabbit.queues.notification.name}"])
    fun onNotification(message: Message, channel: Channel) {
        val propiedades = message.messageProperties
        val etiqueta = propiedades.deliveryTag
        val clave = propiedades.receivedRoutingKey.orEmpty()
        try {
            despachar(clave, message.body)
            channel.basicAck(etiqueta, false)
        } catch (ex: MensajeNoRecuperableException) {
            rechazarSinReencolar(channel, etiqueta, clave, propiedades.messageId, "no recuperable", ex)
        } catch (ex: RuntimeException) {
            rechazarSinReencolar(channel, etiqueta, clave, propiedades.messageId, "reintentos agotados", ex)
        }
    }

    private fun despachar(clave: String, cuerpo: ByteArray) {
        when (clave) {
            nombres.routing.notificationEmail -> {
                val sobre = leerSobre<EmailNotification>(cuerpo)
                reintentar { emailViewModel.notifyRecipient(sobre) }
            }
            nombres.routing.notificationTicket -> {
                val sobre = leerSobre<WarehouseTicket>(cuerpo)
                reintentar { warehouseViewModel.sendTicket(sobre) }
            }
            nombres.routing.notificationLabel -> {
                val sobre = leerSobre<ShippingLabel>(cuerpo)
                reintentar { warehouseViewModel.sendLabel(sobre) }
            }
            else -> throw MensajeNoRecuperableException("No hay manejador para la routing key '$clave'")
        }
    }

    private inline fun <reified T : Any> leerSobre(cuerpo: ByteArray): EventEnvelope<T> = try {
        jsonMapper.readValue(cuerpo, object : TypeReference<EventEnvelope<T>>() {})
    } catch (ex: JacksonException) {
        throw MensajeNoRecuperableException("Mensaje JSON no válido para ${T::class.simpleName}: ${ex.message}", ex)
    }

    /** Solo se reintentan fallos recuperables. Los datos inválidos se descartan al primer intento. */
    private fun reintentar(accion: () -> Unit) {
        val politica = properties.retry
        var ultimo: RuntimeException? = null
        for (intento in 0 until politica.maxAttempts) {
            try {
                accion()
                return
            } catch (ex: MensajeNoRecuperableException) {
                throw ex
            } catch (ex: IllegalArgumentException) {
                throw MensajeNoRecuperableException("Datos de notificación no válidos: ${ex.message}", ex)
            } catch (ex: ConstraintViolationException) {
                throw MensajeNoRecuperableException("Datos de notificación no válidos: ${ex.message}", ex)
            } catch (ex: RuntimeException) {
                ultimo = ex
                log.warn("Intento {} de {} fallido: {}", intento + 1, politica.maxAttempts, ex.message)
                if (intento < politica.maxAttempts - 1) {
                    Thread.sleep(politica.delayBefore(intento))
                }
            }
        }
        throw ultimo ?: IllegalStateException("Sin intentos configurados")
    }

    private fun rechazarSinReencolar(
        channel: Channel,
        etiqueta: Long,
        clave: String,
        mensajeId: String?,
        motivo: String,
        causa: RuntimeException,
    ) {
        channel.basicNack(etiqueta, false, false)
        log.error(
            "Notificación enviada a la DLQ ({}): routingKey={} messageId={} causa={}",
            motivo, clave, mensajeId ?: "-", causa.message, causa,
        )
    }

    companion object {
        /** Tamaño máximo del cuerpo que se copia en los logs de la DLQ. */
        const val LIMITE_LOG = 2000

        fun textoParaLog(cuerpo: ByteArray): String =
            String(cuerpo, StandardCharsets.UTF_8).take(LIMITE_LOG)
    }
}

/** Mensaje que no puede procesarse aunque se reintente (formato, datos o routing key inválidos). */
class MensajeNoRecuperableException(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)
