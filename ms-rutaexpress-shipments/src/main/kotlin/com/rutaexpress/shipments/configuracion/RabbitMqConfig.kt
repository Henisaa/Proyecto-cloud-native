package com.rutaexpress.shipments.configuracion

import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["app.rabbit.enabled"], havingValue = "true", matchIfMissing = true)
class RabbitMqConfig {

    /**
     * Publica JSON plano SIN header __TypeId__ para que notify (que solo confía en
     * sus propias clases) pueda deserializar usando el tipo inferido de su listener.
     */
    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        val converter = Jackson2JsonMessageConverter()
        val typeMapper = DefaultJackson2JavaTypeMapper()
        typeMapper.typePrecedence = Jackson2JavaTypeMapper.TypePrecedence.INFERRED
        converter.javaTypeMapper = typeMapper
        return RabbitTemplate(connectionFactory).apply { messageConverter = converter }
    }

    @Bean
    fun exchangeComandosDirectos(): DirectExchange = DirectExchange("cmd.direct", true, false)

    @Bean
    fun exchangeComandosTopic(): TopicExchange = TopicExchange("cmd.topic", true, false)

    @Bean
    fun exchangeDeadLetter(): DirectExchange = DirectExchange("cmd.dead.dlx", true, false)
}
