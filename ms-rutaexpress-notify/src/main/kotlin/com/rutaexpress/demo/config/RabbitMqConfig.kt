package com.rutaexpress.demo.config

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.ExchangeBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class RabbitMqConfig {
	companion object {
		const val COMMAND_DIRECT_EXCHANGE = "cmd.direct"
		const val COMMAND_TOPIC_EXCHANGE = "cmd.topic"
		const val DEAD_LETTER_EXCHANGE = "cmd.dead.dlx"

		const val EMAIL_QUEUE = "q.cmd.email"
		const val WAREHOUSE_QUEUE = "q.cmd.warehouse"
		const val LABEL_QUEUE = "q.cmd.label"

		const val EMAIL_DLQ = "q.cmd.email.dlq"
		const val WAREHOUSE_DLQ = "q.cmd.warehouse.dlq"
		const val LABEL_DLQ = "q.cmd.label.dlq"

		const val EMAIL_KEY = "email.send"
		const val WAREHOUSE_KEY = "warehouse.ticket"
		const val LABEL_KEY = "label.gen"

		const val EMAIL_TOPIC_PATTERN = "email.*"
		const val WAREHOUSE_TOPIC_PATTERN = "warehouse.#"
		const val LABEL_TOPIC_PATTERN = "label.*"

		/** Paquete aceptado por el mapeador de tipos al deserializar el envelope. */
		const val MODEL_PACKAGE = "com.rutaexpress.demo.model"
	}

	@Bean
	fun messageConverter(jsonMapper: JsonMapper): MessageConverter =
		JacksonJsonMessageConverter(jsonMapper).apply {
			javaTypeMapper.addTrustedPackages(MODEL_PACKAGE)
		}

	@Bean
	fun commandDirectExchange(): DirectExchange =
		ExchangeBuilder.directExchange(COMMAND_DIRECT_EXCHANGE).durable(true).build()

	@Bean
	fun commandTopicExchange(): TopicExchange =
		ExchangeBuilder.topicExchange(COMMAND_TOPIC_EXCHANGE).durable(true).build()

	@Bean
	fun deadLetterExchange(): DirectExchange =
		ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build()

	@Bean
	fun emailQueue(): Queue = mainQueue(EMAIL_QUEUE, EMAIL_KEY)

	@Bean
	fun warehouseQueue(): Queue = mainQueue(WAREHOUSE_QUEUE, WAREHOUSE_KEY)

	@Bean
	fun labelQueue(): Queue = mainQueue(LABEL_QUEUE, LABEL_KEY)

	@Bean
	fun emailDlq(): Queue = QueueBuilder.durable(EMAIL_DLQ).build()

	@Bean
	fun warehouseDlq(): Queue = QueueBuilder.durable(WAREHOUSE_DLQ).build()

	@Bean
	fun labelDlq(): Queue = QueueBuilder.durable(LABEL_DLQ).build()

	@Bean
	fun emailDirectBinding(emailQueue: Queue, commandDirectExchange: DirectExchange): Binding =
		BindingBuilder.bind(emailQueue).to(commandDirectExchange).with(EMAIL_KEY)

	@Bean
	fun warehouseDirectBinding(warehouseQueue: Queue, commandDirectExchange: DirectExchange): Binding =
		BindingBuilder.bind(warehouseQueue).to(commandDirectExchange).with(WAREHOUSE_KEY)

	@Bean
	fun labelDirectBinding(labelQueue: Queue, commandDirectExchange: DirectExchange): Binding =
		BindingBuilder.bind(labelQueue).to(commandDirectExchange).with(LABEL_KEY)

	@Bean
	fun emailTopicBinding(emailQueue: Queue, commandTopicExchange: TopicExchange): Binding =
		BindingBuilder.bind(emailQueue).to(commandTopicExchange).with(EMAIL_TOPIC_PATTERN)

	@Bean
	fun warehouseTopicBinding(warehouseQueue: Queue, commandTopicExchange: TopicExchange): Binding =
		BindingBuilder.bind(warehouseQueue).to(commandTopicExchange).with(WAREHOUSE_TOPIC_PATTERN)

	@Bean
	fun labelTopicBinding(labelQueue: Queue, commandTopicExchange: TopicExchange): Binding =
		BindingBuilder.bind(labelQueue).to(commandTopicExchange).with(LABEL_TOPIC_PATTERN)

	@Bean
	fun emailDlqBinding(emailDlq: Queue, deadLetterExchange: DirectExchange): Binding =
		BindingBuilder.bind(emailDlq).to(deadLetterExchange).with(EMAIL_KEY)

	@Bean
	fun warehouseDlqBinding(warehouseDlq: Queue, deadLetterExchange: DirectExchange): Binding =
		BindingBuilder.bind(warehouseDlq).to(deadLetterExchange).with(WAREHOUSE_KEY)

	@Bean
	fun labelDlqBinding(labelDlq: Queue, deadLetterExchange: DirectExchange): Binding =
		BindingBuilder.bind(labelDlq).to(deadLetterExchange).with(LABEL_KEY)

	@Bean
	fun rabbitListenerContainerFactory(
		connectionFactory: ConnectionFactory,
		messageConverter: MessageConverter,
	): SimpleRabbitListenerContainerFactory = SimpleRabbitListenerContainerFactory().apply {
		setConnectionFactory(connectionFactory)
		setMessageConverter(messageConverter)
		setAcknowledgeMode(AcknowledgeMode.MANUAL)
		setObservationEnabled(true)
	}

	private fun mainQueue(name: String, deadLetterRoutingKey: String): Queue =
		QueueBuilder.durable(name)
			.deadLetterExchange(DEAD_LETTER_EXCHANGE)
			.deadLetterRoutingKey(deadLetterRoutingKey)
			.build()
}
