package com.rutaexpress.demo.view

import com.rutaexpress.demo.model.ShippingLabel
import com.rutaexpress.demo.model.WarehouseTicket
import org.openpdf.text.Document
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.PdfWriter
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class PdfNotificationView {
	fun renderTicket(ticket: WarehouseTicket): ByteArray = render(
		"Ticket de picking",
		listOf(
			"Envío: ${ticket.shipmentId}",
			"Servicio: ${ticket.serviceName}",
			"Destinatario: ${ticket.recipientName}",
			"Dirección: ${ticket.recipientAddress}",
			"Paquete: ${ticket.packageDescription}",
		),
	)

	fun renderLabel(label: ShippingLabel): ByteArray = render(
		"Etiqueta de despacho",
		listOf(
			"Código de seguimiento: ${label.trackingCode}",
			"Envío: ${label.shipmentId}",
			"Destinatario: ${label.recipientName}",
			"Dirección: ${label.recipientAddress}",
		),
	)

	private fun render(title: String, lines: List<String>): ByteArray {
		val output = ByteArrayOutputStream()
		val document = Document()
		PdfWriter.getInstance(document, output)
		document.open()
		document.add(Paragraph(title))
		lines.forEach { document.add(Paragraph(it)) }
		document.close()
		return output.toByteArray()
	}
}
