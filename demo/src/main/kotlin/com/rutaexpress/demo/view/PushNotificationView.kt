package com.rutaexpress.demo.view

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.rutaexpress.demo.config.NotificationProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class PushNotificationView(properties: NotificationProperties) {
	private val messaging: FirebaseMessaging? = properties.firebase.credentialsPath
		.takeIf(String::isNotBlank)
		?.let { initializeFirebase(Path.of(it)) }

	fun send(shipmentId: String, deviceToken: String, title: String, body: String) {
		val client = messaging
			?: throw IllegalStateException("Firebase no está configurado: defina FIREBASE_CREDENTIALS_PATH")
		try {
			client.send(
				Message.builder()
					.setToken(deviceToken)
					.setNotification(Notification.builder().setTitle(title).setBody(body).build())
					.putData("shipmentId", shipmentId)
					.build(),
			)
		} catch (exception: FirebaseMessagingException) {
			throw IllegalStateException("No se pudo enviar la notificación push", exception)
		}
	}

	private fun initializeFirebase(credentialsPath: Path): FirebaseMessaging {
		val app = FirebaseApp.getApps().firstOrNull() ?: Files.newInputStream(credentialsPath).use { stream ->
			FirebaseApp.initializeApp(
				FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(stream)).build(),
			)
		}
		return FirebaseMessaging.getInstance(app)
	}
}
