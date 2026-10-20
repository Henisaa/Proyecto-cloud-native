package com.rutaexpress.notify.viewmodel

import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Consumidor de la DLQ de notificaciones. Registra en logs cada mensaje no entregado con su motivo,
 * la cola y la routing key de origen, y cuenta la llegada en la métrica rutaexpress.notify.dlq.messages.
 * Se puede detener con RABBIT_DLQ_CONSUMER_ENABLED=false para inspeccionar la DLQ desde el Management UI.
 */
@Component
class DeadLetterRegistro(registry: MeterRegistry) {
    private val log = LoggerFactory.getLogger(DeadLetterRegistro::class.java)
    private val contador: Counter = Counter.builder("rutaexpress.notify.dlq.messages")
        .description("Mensajes que llegaron a la DLQ de notificaciones")
        .register(registry)

    @RabbitListener(
        queues = ["\${app.rabbit.queues.notification.dlq}"],
        autoStartup = "\${app.rabbit.dlq.auto-startup:true}",
    )
    fun registrar(message: Message, channel: Channel) {
        val propiedades = message.messageProperties
        val muerte = (propiedades.headers["x-death"] as? List<*>)?.firstOrNull() as? Map<*, *>
        contador.increment()
        log.error(
            "Mensaje no entregado en {}: motivo={} colaOrigen={} routingKeyOriginal={} messageId={} extracto={}",
            propiedades.consumerQueue,
            muerte?.get("reason") ?: "desconocido",
            muerte?.get("queue") ?: "desconocida",
            muerte?.get("routing-keys") ?: propiedades.receivedRoutingKey,
            propiedades.messageId ?: "-",
            extractoEnmascarado(message.body),
        )
        channel.basicAck(propiedades.deliveryTag, false)
    }

    /** Extracto corto sin correos: el log guarda lo necesario para diagnosticar, no datos personales completos. */
    private fun extractoEnmascarado(cuerpo: ByteArray): String =
        String(cuerpo, Charsets.UTF_8).take(300).replace(Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""), "***@***")
}
