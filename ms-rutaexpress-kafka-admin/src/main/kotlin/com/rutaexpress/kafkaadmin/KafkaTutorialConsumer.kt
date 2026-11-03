package com.rutaexpress.kafkaadmin

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

data class MensajeRecibido(
    val partition: Int,
    val offset: Long,
    val key: String?,
    val message: String,
    val timestamp: Instant = Instant.now()
)

/**
 * Consumidor de Kafka que escucha el topic 'tutorial-particiones' con el group-id 'mi-grupo-educativo'.
 * Muestra en tiempo real:
 * - Partición: En cuál de las 3 particiones llegó el mensaje
 * - Offset: Posición secuencial dentro de esa partición
 * - Key: La clave del mensaje (null si no se envió)
 * - Mensaje: El contenido
 */
@Service
class KafkaTutorialConsumer {
    private val historial = ConcurrentLinkedDeque<MensajeRecibido>()

    fun ultimosMensajes(): List<MensajeRecibido> = historial.toList()

    @KafkaListener(
        topics = ["tutorial-particiones"],
        groupId = "mi-grupo-educativo"
    )
    fun listen(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        // Imprimir información de manera clara y visual idéntica a la guía
        println("\n" + "=".repeat(100))
        System.out.printf(" MENSAJE RECIBIDO%n")
        System.out.printf(" 📍Partición: %d%n", partition)
        System.out.printf(" Offset: %d%n", offset)
        System.out.printf(" Key: %s%n", if (key != null) key else "(sin key)")
        System.out.printf(" Mensaje: %s%n", message)
        println("=".repeat(100) + "\n")

        historial.addFirst(MensajeRecibido(partition, offset, key, message))
        while (historial.size > 100) {
            historial.removeLast()
        }
    }
}
