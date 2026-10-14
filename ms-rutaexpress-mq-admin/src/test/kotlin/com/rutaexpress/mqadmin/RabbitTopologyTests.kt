package com.rutaexpress.mqadmin
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.amqp.core.*
class RabbitTopologyTests {
    @Test fun `todos los comandos tienen destino directo topic y DLQ independiente`() {
        val declarations = RabbitTopology().topology().declarables
        val queues = declarations.filterIsInstance<Queue>().associateBy { it.name }
        val bindings = declarations.filterIsInstance<Binding>()
        assertThat(queues.keys).containsExactlyInAnyOrderElementsOf(RabbitTopology.queues)
        for ((name,key,pattern) in RabbitTopology.flows) {
            assertThat(queues[name]!!.arguments).containsEntry("x-dead-letter-exchange","cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key",key)
            assertThat(bindings.any { it.destination==name && it.exchange=="cmd.direct" && it.routingKey==key }).isTrue()
            assertThat(bindings.any { it.destination==name && it.exchange=="cmd.topic" && it.routingKey==pattern }).isTrue()
            assertThat(bindings.any { it.destination=="$name.dlq" && it.exchange=="cmd.dead.dlx" && it.routingKey==key }).isTrue()
        }
    }
}
