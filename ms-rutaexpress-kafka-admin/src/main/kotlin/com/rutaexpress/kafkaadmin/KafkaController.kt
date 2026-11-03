package com.rutaexpress.kafkaadmin

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*

/**
 * Controlador REST que envía mensajes a Kafka según la guía tutorial de tópicos, particiones y offsets.
 *
 * Demuestra dos conceptos:
 * - Sin Key: Round Robin (distribuye entre particiones 0, 1, 2...)
 * - Con Key: Sticky Partitioning (todos a la misma partición según hash de la clave)
 */
@RestController
@RequestMapping("/api/kafka")
class KafkaController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumer: KafkaTutorialConsumer
) {
    private val topicName = "tutorial-particiones"

    /**
     * Endpoint para enviar mensajes a Kafka vía GET
     *
     * Ejemplos de uso:
     * - SIN KEY (Round Robin - distribuye entre particiones):
     *   GET http://localhost:8888/api/kafka/send?message=Mensaje1
     * - CON KEY (Sticky Partitioning - misma partición):
     *   GET http://localhost:8888/api/kafka/send?message=Paso1-Creada&key=orden-555
     */
    @GetMapping("/send")
    fun sendMessageGet(
        @RequestParam message: String,
        @RequestParam(required = false) key: String?
    ): String {
        return if (!key.isNullOrEmpty()) {
            // Con Key: Kafka calcula hash(key) para determinar partición
            // Todos los mensajes con la misma key van a la MISMA partición
            kafkaTemplate.send(topicName, key, message)
            "Mensaje enviado al topic '$topicName' con KEY: $key | MENSAJE: $message"
        } else {
            // Sin Key: Kafka usa Round Robin
            // Distribuye automáticamente entre particiones 0, 1, 2
            kafkaTemplate.send(topicName, message)
            "Mensaje enviado al topic '$topicName' (sin key - distribución automática) | MENSAJE: $message"
        }
    }

    /**
     * Endpoint POST para enviar mensajes a Kafka (soporta parámetros URL o formulario)
     */
    @PostMapping("/send")
    fun sendMessagePost(
        @RequestParam message: String,
        @RequestParam(required = false) key: String?
    ): String {
        val clave = key?.takeIf { it.isNotEmpty() }
        if (clave != null) {
            kafkaTemplate.send(topicName, clave, message)
        } else {
            kafkaTemplate.send(topicName, message)
        }
        return "Mensaje enviado: $message Key: $key"
    }

    /**
     * Endpoint de verificación solicitado en el tutorial
     */
    @GetMapping("/health")
    fun health(): String {
        return "Aplicación de Kafka Tutorial está corriendo en puerto 8888"
    }

    /**
     * Consulta los últimos mensajes leídos por el consumidor en tiempo real
     */
    @GetMapping("/messages")
    fun messages(): List<MensajeRecibido> = consumer.ultimosMensajes()
}
