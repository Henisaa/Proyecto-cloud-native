package com.rutaexpress.shipments.mensajeria

import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Consumidor de la DLQ de auditoría: registra en logs cada mensaje no entregado con su motivo (x-death),
 * la cola de origen y el cuerpo. RABBIT_DLQ_CONSUMER_ENABLED=false deja los mensajes en la DLQ para inspección.
 */
@Component
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class DeadLetterRegistro(registry: MeterRegistry) {
    private val log = LoggerFactory.getLogger(DeadLetterRegistro::class.java)
    private val contador: Counter = Counter.builder("rutaexpress.shipments.dlq.messages")
        .description("Eventos de auditoría que llegaron a la DLQ")
        .register(registry)

    @RabbitListener(
        queues = ["\${app.rabbit.queues.audit.dlq}"],
        autoStartup = "\${app.rabbit.dlq.auto-startup:true}",
    )
    fun registrar(message: Message, channel: Channel) {
        val propiedades = message.messageProperties
        val muerte = (propiedades.headers["x-death"] as? List<*>)?.firstOrNull() as? Map<*, *>
        contador.increment()
        log.error(
            "Mensaje no entregado: motivo={} colaOrigen={} routingKeyOriginal={} messageId={} extracto={}",
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
