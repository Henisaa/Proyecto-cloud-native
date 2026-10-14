package com.rutaexpress.demo.viewmodel

import com.rabbitmq.client.Channel
import com.rutaexpress.demo.config.NotificationProperties
import com.rutaexpress.demo.config.RabbitMqConfig
import com.rutaexpress.demo.model.EmailNotification
import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.ShippingLabel
import com.rutaexpress.demo.model.WarehouseTicket
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class NotificationConsumerViewModel(
	private val emailViewModel: EmailNotificationViewModel,
	private val warehouseViewModel: WarehouseNotificationViewModel,
	private val properties: NotificationProperties,
	registry: MeterRegistry,
) {
	private val dlqCounter: Counter = Counter.builder("rutaexpress.notify.dlq.messages")
		.description("Mensajes enviados a DLQ tras agotar los reintentos")
		.register(registry)

	@RabbitListener(queues = [RabbitMqConfig.EMAIL_QUEUE])
	fun consumeEmail(event: EventEnvelope<EmailNotification>, message: Message, channel: Channel) =
		handle(message, channel, event.eventId) { emailViewModel.notifyRecipient(event) }

	@RabbitListener(queues = [RabbitMqConfig.WAREHOUSE_QUEUE])
	fun consumeWarehouseTicket(event: EventEnvelope<WarehouseTicket>, message: Message, channel: Channel) =
		handle(message, channel, event.eventId) { warehouseViewModel.sendTicket(event) }

	@RabbitListener(queues = [RabbitMqConfig.LABEL_QUEUE])
	fun consumeLabel(event: EventEnvelope<ShippingLabel>, message: Message, channel: Channel) =
		handle(message, channel, event.eventId) { warehouseViewModel.sendLabel(event) }

	private fun handle(message: Message, channel: Channel, eventId: String, action: () -> Unit) {
		val deliveryTag = message.messageProperties.deliveryTag
		try {
			deliverWithRetries(action)
			channel.basicAck(deliveryTag, false)
		} catch (exception: RuntimeException) {
			dlqCounter.increment()
			channel.basicNack(deliveryTag, false, false)
			LOGGER.error("Evento {} derivado a DLQ tras {} intentos", eventId, properties.retry.maxAttempts, exception)
		}
	}

	private fun deliverWithRetries(action: () -> Unit) {
		val retry = properties.retry
		var lastFailure: RuntimeException? = null
		for (attempt in 0 until retry.maxAttempts) {
			try {
				action()
				return
			} catch (exception: RuntimeException) {
				lastFailure = exception
				if (attempt < retry.maxAttempts - 1) {
					Thread.sleep(retry.delayBefore(attempt))
				}
			}
		}
		throw lastFailure ?: IllegalStateException("Sin intentos configurados")
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(NotificationConsumerViewModel::class.java)
	}
}
