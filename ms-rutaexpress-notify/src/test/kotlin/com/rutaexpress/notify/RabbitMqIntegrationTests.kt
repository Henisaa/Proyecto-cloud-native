package com.rutaexpress.notify

import com.rutaexpress.notify.config.RabbitNombres
import com.rutaexpress.notify.model.ClaimResult
import com.rutaexpress.notify.model.IdempotencyStore
import com.rutaexpress.notify.viewmodel.EmailNotificationViewModel
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration

/**
 * Pruebas contra un RabbitMQ real (Testcontainers). Se activan con RUN_DOCKER_TESTS=true.
 * La DLQ listener se desactiva aquí para que el mensaje fallido permanezca en la DLQ y pueda inspeccionarse.
 */
@Testcontainers
@SpringBootTest(
    properties = [
        "rutaexpress.security.enabled=false",
        "rutaexpress.notify.retry.max-attempts=2",
        "rutaexpress.notify.retry.initial-interval=0",
        "app.rabbit.dlq.auto-startup=false",
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
    private lateinit var nombres: RabbitNombres

    @MockitoBean
    private lateinit var emailViewModel: EmailNotificationViewModel

    @MockitoBean
    private lateinit var idempotencyStore: IdempotencyStore

    @Test
    fun `declara la cola, su DLQ y los enlaces en el broker`() {
        assertThat(admin.getQueueInfo(nombres.queues.notification.name)).isNotNull()
        assertThat(admin.getQueueInfo(nombres.queues.notification.dlq)).isNotNull()
        assertThat(sourceBindings(nombres.exchanges.events)).contains("notification.#")
        assertThat(sourceBindings(nombres.exchanges.deadLetter)).contains(nombres.queues.notification.deadLetterKey)
    }

    @Test
    fun `un correo que falla siempre termina en la DLQ tras los reintentos`() {
        whenever(idempotencyStore.claim(any())).thenReturn(ClaimResult.ACQUIRED)
        doThrow(IllegalStateException("SMTP caído")).`when`(emailViewModel).notifyRecipient(any())

        publicar("notification.email.send", """
            {"type":"notification.requested","eventId":"evt-dlq","timestamp":"2026-09-10T12:00:00Z",
             "traceId":"t","correlationId":"c",
             "payload":{"shipmentId":"RX-9","recipientEmail":"cliente@example.com","recipientName":"Ana",
                        "subject":"Envío en ruta","message":"Mensaje"}}
        """.trimIndent())

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(template.receive(nombres.queues.notification.dlq, 1_000)).isNotNull()
        }
    }

    @Test
    fun `un JSON invalido llega a la DLQ sin reintentar`() {
        publicar("notification.email.send", "{esto no es json")

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(template.receive(nombres.queues.notification.dlq, 1_000)).isNotNull()
        }
        verify(emailViewModel, never()).notifyRecipient(any())
    }

    private fun publicar(clave: String, cuerpo: String) {
        val mensaje: Message = MessageBuilder.withBody(cuerpo.toByteArray())
            .setContentType("application/json")
            .setMessageId("msg-" + System.nanoTime())
            .build()
        template.send(nombres.exchanges.events, clave, mensaje)
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
