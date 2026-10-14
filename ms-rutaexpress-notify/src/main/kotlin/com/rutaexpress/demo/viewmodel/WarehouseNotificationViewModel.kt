package com.rutaexpress.demo.viewmodel

import com.rutaexpress.demo.model.EventEnvelope
import com.rutaexpress.demo.model.IdempotencyStore
import com.rutaexpress.demo.model.ShippingLabel
import com.rutaexpress.demo.model.WarehouseTicket
import com.rutaexpress.demo.view.EmailNotificationView
import com.rutaexpress.demo.view.PdfNotificationView
import jakarta.validation.Validator
import org.springframework.stereotype.Service

@Service
class WarehouseNotificationViewModel(
	private val pdfView: PdfNotificationView,
	private val emailView: EmailNotificationView,
	idempotencyStore: IdempotencyStore,
	validator: Validator,
) : ValidatingNotificationProcessor(validator, idempotencyStore) {

	fun sendTicket(event: EventEnvelope<WarehouseTicket>) = processOnce(event) { ticket ->
		emailView.sendAttachment(
			ticket.warehouseEmail,
			"Ticket de picking - ${ticket.shipmentId}",
			"Se adjunta el ticket de picking del envío ${ticket.shipmentId}.",
			"ticket-${ticket.shipmentId}.pdf",
			pdfView.renderTicket(ticket),
		)
	}

	fun sendLabel(event: EventEnvelope<ShippingLabel>) = processOnce(event) { label ->
		emailView.sendAttachment(
			label.warehouseEmail,
			"Etiqueta de despacho - ${label.shipmentId}",
			"Se adjunta la etiqueta del envío ${label.shipmentId}.",
			"etiqueta-${label.shipmentId}.pdf",
			pdfView.renderLabel(label),
		)
	}
}
