package com.rutaexpress.demo

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
	properties = [
		"rutaexpress.security.enabled=true",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"management.health.rabbit.enabled=false",
		"management.health.redis.enabled=false",
		"management.health.mail.enabled=false",
	],
)
@AutoConfigureMockMvc
class SecurityTests {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Test
	fun `health queda publico para las sondas de EC2`() {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
	}

	@Test
	fun `prometheus exige token del IDaaS`() {
		mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized)
	}

	@Test
	fun `prometheus rechaza un rol sin permiso`() {
		mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, "Bearer operator"))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `prometheus permite al rol Admin`() {
		mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, "Bearer admin"))
			.andExpect(status().isOk)
	}
}
