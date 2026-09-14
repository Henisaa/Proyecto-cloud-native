package com.rutaexpress.demo

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
	properties = [
		"rutaexpress.security.enabled=false",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"management.health.rabbit.enabled=false",
		"management.health.redis.enabled=false",
		"management.health.mail.enabled=false",
	],
)
class DemoApplicationTests {
	@Test
	fun `levanta el contexto del microservicio de notificaciones`() {
	}
}
