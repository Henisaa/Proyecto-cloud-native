package com.rutaexpress.demo.view

import com.rutaexpress.demo.config.NotificationProperties
import com.rutaexpress.demo.model.EmailNotification
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class EmailNotificationView(
	private val mailSender: JavaMailSender,
	private val properties: NotificationProperties,
) {
	fun send(notification: EmailNotification) {
		val email = SimpleMailMessage().apply {
			from = properties.from
			setTo(notification.recipientEmail)
			subject = notification.subject
			text = "Hola ${notification.recipientName},\n\n${notification.message}\n\nEnvío: ${notification.shipmentId}"
		}
		mailSender.send(email)
	}

	fun sendAttachment(recipient: String, subject: String, body: String, fileName: String, bytes: ByteArray) {
		val email = mailSender.createMimeMessage()
		MimeMessageHelper(email, true, Charsets.UTF_8.name()).apply {
			setFrom(properties.from)
			setTo(recipient)
			setSubject(subject)
			setText(body)
			addAttachment(fileName, ByteArrayResource(bytes))
		}
		mailSender.send(email)
	}
}
