package com.rutaexpress.demo

import com.rutaexpress.demo.config.RabbitMqConfig
import com.rutaexpress.demo.model.EmailNotification
import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.IdempotencyStore
import com.rutaexpress.demo.viewmodel.EmailNotificationViewModel
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration
import java.time.Instant

@Testcontainers
@SpringBootTest(
	properties = [
		"rutaexpress.security.enabled=false",
		"spring.rabbitmq.listener.simple.auto-startup=true",
		"rutaexpress.notify.retry.max-attempts=1",
		"rutaexpress.notify.retry.initial-interval=0",
		"management.health.rabbit.enabled=false",
		"management.health.redis.enabled=false",
		"management.health.mail.enabled=false",
	],
)
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class RabbitMqIntegrationTests {
	@Autowired
	private lateinit var template: RabbitTemplate

	@Autowired
	private lateinit var admin: RabbitAdmin

	@Autowired
	private lateinit var meters: MeterRegistry

	@MockitoBean
	private lateinit var emailViewModel: EmailNotificationViewModel

	@MockitoBean
	private lateinit var idempotencyStore: IdempotencyStore

	@Test
	fun `declara las seis colas y los bindings en el broker`() {
		listOf(
			RabbitMqConfig.EMAIL_QUEUE,
			RabbitMqConfig.WAREHOUSE_QUEUE,
			RabbitMqConfig.LABEL_QUEUE,
			RabbitMqConfig.EMAIL_DLQ,
			RabbitMqConfig.WAREHOUSE_DLQ,
			RabbitMqConfig.LABEL_DLQ,
		).forEach { queue -> assertThat(admin.getQueueInfo(queue)).isNotNull() }

		assertThat(sourceBindings(RabbitMqConfig.COMMAND_DIRECT_EXCHANGE))
			.contains(RabbitMqConfig.EMAIL_KEY, RabbitMqConfig.WAREHOUSE_KEY, RabbitMqConfig.LABEL_KEY)
		assertThat(sourceBindings(RabbitMqConfig.COMMAND_TOPIC_EXCHANGE))
			.contains(
				RabbitMqConfig.EMAIL_TOPIC_PATTERN,
				RabbitMqConfig.WAREHOUSE_TOPIC_PATTERN,
				RabbitMqConfig.LABEL_TOPIC_PATTERN,
			)
		assertThat(sourceBindings(RabbitMqConfig.DEAD_LETTER_EXCHANGE))
			.contains(RabbitMqConfig.EMAIL_KEY, RabbitMqConfig.WAREHOUSE_KEY, RabbitMqConfig.LABEL_KEY)
	}

	private fun sourceBindings(exchange: String): List<String> {
		val response = RestClient.create()
			.get()
			.uri(URI.create("${rabbit.httpUrl}/api/exchanges/%2F/$exchange/bindings/source"))
			.headers { it.setBasicAuth(rabbit.adminUsername, rabbit.adminPassword) }
			.retrieve()
			.body(object : ParameterizedTypeReference<List<Map<String, Any>>>() {})
		return response.orEmpty().mapNotNull { it["routing_key"] as? String }
	}

	@Test
	fun `el mensaje que falla siempre termina en la DLQ y suma la metrica`() {
		whenever(idempotencyStore.claim(any())).thenReturn(true)
		doThrow(IllegalStateException("SMTP caído")).`when`(emailViewModel).notifyRecipient(any())

		template.convertAndSend(
			RabbitMqConfig.COMMAND_DIRECT_EXCHANGE,
			RabbitMqConfig.EMAIL_KEY,
			EventEnvelope(
				type = "notification.requested",
				eventId = "evt-dlq",
				timestamp = Instant.now(),
				traceId = "trace-1",
				correlationId = "corr-1",
				payload = EmailNotification("SHP-9", "cliente@example.com", "Ana", "Envío en ruta", "Mensaje"),
			),
		)

		await().atMost(Duration.ofSeconds(20)).untilAsserted {
			assertThat(template.receiveAndConvert(RabbitMqConfig.EMAIL_DLQ, 2_000)).isNotNull()
		}
		assertThat(meters.find("rutaexpress.notify.dlq.messages").counter()!!.count()).isGreaterThan(0.0)
	}

	companion object {
		@Container
		@JvmStatic
		val rabbit = RabbitMQContainer("rabbitmq:4-management")

		@JvmStatic
		@DynamicPropertySource
		fun brokerProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.rabbitmq.host") { rabbit.host }
			registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
			registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
			registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
		}
	}
}
