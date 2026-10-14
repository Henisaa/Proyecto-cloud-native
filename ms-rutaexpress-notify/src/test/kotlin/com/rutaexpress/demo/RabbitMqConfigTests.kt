package com.rutaexpress.demo.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.support.converter.MessageConverter
import org.mockito.kotlin.mock

class RabbitMqConfigTests {
	private val config = RabbitMqConfig()

	@Test
	fun `declara los tres exchanges del caso`() {
		assertThat(config.commandDirectExchange().name).isEqualTo("cmd.direct")
		assertThat(config.commandTopicExchange().name).isEqualTo("cmd.topic")
		assertThat(config.deadLetterExchange().name).isEqualTo("cmd.dead.dlx")
	}

	@Test
	fun `declara las seis colas con su dead letter routing key`() {
		assertThat(config.emailQueue().name).isEqualTo("q.cmd.email")
		assertThat(config.warehouseQueue().name).isEqualTo("q.cmd.warehouse")
		assertThat(config.labelQueue().name).isEqualTo("q.cmd.label")
		assertThat(config.emailDlq().name).isEqualTo("q.cmd.email.dlq")
		assertThat(config.warehouseDlq().name).isEqualTo("q.cmd.warehouse.dlq")
		assertThat(config.labelDlq().name).isEqualTo("q.cmd.label.dlq")

		assertThat(config.emailQueue().arguments)
			.containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
			.containsEntry("x-dead-letter-routing-key", "email.send")
		assertThat(config.warehouseQueue().arguments)
			.containsEntry("x-dead-letter-routing-key", "warehouse.ticket")
		assertThat(config.labelQueue().arguments)
			.containsEntry("x-dead-letter-routing-key", "label.gen")
	}

	@Test
	fun `enlaza las colas a direct topic y a la DLX`() {
		assertThat(config.emailDirectBinding(config.emailQueue(), config.commandDirectExchange()).routingKey)
			.isEqualTo("email.send")
		assertThat(config.warehouseDirectBinding(config.warehouseQueue(), config.commandDirectExchange()).routingKey)
			.isEqualTo("warehouse.ticket")
		assertThat(config.labelDirectBinding(config.labelQueue(), config.commandDirectExchange()).routingKey)
			.isEqualTo("label.gen")

		assertThat(config.emailTopicBinding(config.emailQueue(), config.commandTopicExchange()).routingKey)
			.isEqualTo("email.*")
		assertThat(config.warehouseTopicBinding(config.warehouseQueue(), config.commandTopicExchange()).routingKey)
			.isEqualTo("warehouse.#")
		assertThat(config.labelTopicBinding(config.labelQueue(), config.commandTopicExchange()).routingKey)
			.isEqualTo("label.*")

		assertThat(config.emailDlqBinding(config.emailDlq(), config.deadLetterExchange()).routingKey)
			.isEqualTo("email.send")
		assertThat(config.warehouseDlqBinding(config.warehouseDlq(), config.deadLetterExchange()).routingKey)
			.isEqualTo("warehouse.ticket")
		assertThat(config.labelDlqBinding(config.labelDlq(), config.deadLetterExchange()).routingKey)
			.isEqualTo("label.gen")
	}

	@Test
	fun `usa confirmacion manual para poder hacer ACK y NACK explicitos`() {
		val factory = config.rabbitListenerContainerFactory(
			mock<ConnectionFactory>(),
			mock<MessageConverter>(),
		)

		assertThat(factory).extracting("acknowledgeMode").isEqualTo(AcknowledgeMode.MANUAL)
	}
}
