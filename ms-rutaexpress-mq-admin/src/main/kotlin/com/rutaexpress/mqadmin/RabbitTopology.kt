package com.rutaexpress.mqadmin

import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitTopology {
    @Bean
    fun topology(): Declarables {
        val direct = DirectExchange("cmd.direct", true, false)
        val topic = TopicExchange("cmd.topic", true, false)
        val dead = DirectExchange("cmd.dead.dlx", true, false)
        val declarations = mutableListOf<Declarable>(direct, topic, dead)
        for ((name, key, pattern) in flows) {
            val queue = QueueBuilder.durable(name).deadLetterExchange(dead.name).deadLetterRoutingKey(key).build()
            val dlq = QueueBuilder.durable("$name.dlq").build()
            declarations.addAll(listOf(queue, dlq,
                BindingBuilder.bind(queue).to(direct).with(key),
                BindingBuilder.bind(queue).to(topic).with(pattern),
                BindingBuilder.bind(dlq).to(dead).with(key)))
        }
        return Declarables(declarations)
    }
    companion object {
        val flows = listOf(Triple("q.cmd.email", "email.send", "email.*"),
            Triple("q.cmd.warehouse", "warehouse.ticket", "warehouse.#"),
            Triple("q.cmd.label", "label.gen", "label.*"))
        val queues = flows.flatMap { listOf(it.first, it.first + ".dlq") }.toSet()
    }
}
