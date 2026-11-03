package com.rutaexpress.notify.viewmodel

import com.rutaexpress.notify.model.EmailNotification
import com.rutaexpress.notify.model.EventEnvelope
import com.rutaexpress.notify.model.IdempotencyStore
import com.rutaexpress.notify.view.EmailNotificationView
import com.rutaexpress.notify.view.PushNotificationView
import jakarta.validation.Validator
import org.springframework.stereotype.Service

@Service
class EmailNotificationViewModel(
	private val emailView: EmailNotificationView,
	private val pushView: PushNotificationView,
	idempotencyStore: IdempotencyStore,
	validator: Validator,
) : ValidatingNotificationProcessor(validator, idempotencyStore) {

	fun notifyRecipient(event: EventEnvelope<EmailNotification>) = processOnce(event) { notification ->
		require(notification.hasEmail || notification.hasDeviceToken) {
			"La notificación debe incluir recipientEmail o deviceToken (eventId=${event.eventId})"
		}
		if (notification.hasEmail) {
			emailView.send(notification)
		}
		if (notification.hasDeviceToken) {
			pushView.send(
				notification.shipmentId,
				notification.deviceToken.orEmpty(),
				notification.subject,
				notification.message,
			)
		}
	}
}
