package com.rutaexpress.shipments.configuracion
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class RabbitMqConfig {
    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory, mapper: JsonMapper): RabbitTemplate = RabbitTemplate(connectionFactory).apply {
        messageConverter = JacksonJsonMessageConverter(mapper)
        setMandatory(true)
        // The receiver owns its DTO packages and infers the type from its listener signature.
        addBeforePublishPostProcessors({ message ->
            message.messageProperties.headers.remove("__TypeId__")
            message.messageProperties.headers.remove("__ContentTypeId__")
            message.messageProperties.headers.remove("__KeyTypeId__")
            message.messageProperties.deliveryMode = org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
            message
        })
    }
    @Bean fun exchangeComandosDirectos() = DirectExchange("cmd.direct", true, false)
    @Bean fun exchangeComandosTopic() = TopicExchange("cmd.topic", true, false)
    @Bean fun exchangeDeadLetter() = DirectExchange("cmd.dead.dlx", true, false)
}
