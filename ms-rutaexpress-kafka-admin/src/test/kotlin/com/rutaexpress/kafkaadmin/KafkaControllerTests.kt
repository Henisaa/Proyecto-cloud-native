package com.rutaexpress.kafkaadmin

import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.assertj.core.api.Assertions.assertThat

@WebMvcTest(KafkaController::class)
@Import(SeguridadConfig::class, KafkaStreamsService::class)
class KafkaControllerTests {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @MockitoBean lateinit var consumer: KafkaTutorialConsumer
    @MockitoBean lateinit var decoder: JwtDecoder

    @Test
    fun `health responde sin autenticacion con mensaje tutorial`() {
        mvc.perform(get("/api/kafka/health"))
            .andExpect(status().isOk)
            .andExpect(content().string("Aplicación de Kafka Tutorial está corriendo en puerto 8888"))
    }

    @Test
    fun `send sin key realiza distribucion automatica sin requerir autenticacion`() {
        mvc.perform(get("/api/kafka/send").param("message", "Mensaje1"))
            .andExpect(status().isOk)
            .andExpect(content().string("Mensaje enviado al topic 'tutorial-particiones' (sin key - distribución automática) | MENSAJE: Mensaje1"))

        verify(kafkaTemplate).send("tutorial-particiones", "Mensaje1")
    }

    @Test
    fun `send con key garantiza sticky partitioning sin requerir autenticacion`() {
        mvc.perform(get("/api/kafka/send").param("message", "Paso1-Creada").param("key", "orden-555"))
            .andExpect(status().isOk)
            .andExpect(content().string("Mensaje enviado al topic 'tutorial-particiones' con KEY: orden-555 | MENSAJE: Paso1-Creada"))

        verify(kafkaTemplate).send("tutorial-particiones", "orden-555", "Paso1-Creada")
    }

    @Test
    fun `send via POST soporta mensaje y key`() {
        mvc.perform(post("/api/kafka/send").param("message", "Paso2-Pagada").param("key", "orden-555"))
            .andExpect(status().isOk)
            .andExpect(content().string("Mensaje enviado: Paso2-Pagada Key: orden-555"))

        verify(kafkaTemplate).send("tutorial-particiones", "orden-555", "Paso2-Pagada")
    }

    @Test
    fun `messages devuelve historial del consumidor`() {
        `when`(consumer.ultimosMensajes()).thenReturn(listOf(
            MensajeRecibido(partition = 1, offset = 0, key = "orden-555", message = "Paso1-Creada")
        ))

        mvc.perform(get("/api/kafka/messages"))
            .andExpect(status().isOk)
    }

    @Test
    fun `streams state y simulate procesan envios y consultan State Store`() {
        mvc.perform(get("/api/kafka/streams/simulate").param("scenario", "rafaga"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.processedCount").value(5))

        mvc.perform(
            post("/api/kafka/streams/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"serviceType":"EXPRESS","status":"ENTREGADO","zone":"NORTE","weightKg":14.0,"packagesCount":2,"price":19000.0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        mvc.perform(get("/api/kafka/streams/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applicationId").value("rutaexpress-streams-processor"))
            .andExpect(jsonPath("$.inputTopic").value("shipments.events"))
    }

    @Test
    fun `TutorialPartitioner alterna particiones sin key y fija particion con key`() {
        val partitioner = TutorialPartitioner()
        val nodes = arrayOf(Node(1, "k1", 9092))
        val topic = "tutorial-particiones"
        val partitions = listOf(
            PartitionInfo(topic, 0, nodes[0], nodes, nodes),
            PartitionInfo(topic, 1, nodes[0], nodes, nodes),
            PartitionInfo(topic, 2, nodes[0], nodes, nodes)
        )
        val cluster = Cluster("test", nodes.toList(), partitions, emptySet(), emptySet())

        // Sin key: Round Robin en orden 0, 1, 2, 0...
        val p0 = partitioner.partition(topic, null, null, "m1", null, cluster)
        val p1 = partitioner.partition(topic, null, null, "m2", null, cluster)
        val p2 = partitioner.partition(topic, null, null, "m3", null, cluster)
        val p3 = partitioner.partition(topic, null, null, "m4", null, cluster)

        assertThat(p0).isEqualTo(0)
        assertThat(p1).isEqualTo(1)
        assertThat(p2).isEqualTo(2)
        assertThat(p3).isEqualTo(0)

        // Con key: Siempre la misma partición para la misma clave
        val keyBytes = "orden-555".toByteArray()
        val kp1 = partitioner.partition(topic, "orden-555", keyBytes, "Paso1", null, cluster)
        val kp2 = partitioner.partition(topic, "orden-555", keyBytes, "Paso2", null, cluster)
        val kp3 = partitioner.partition(topic, "orden-555", keyBytes, "Paso3", null, cluster)

        assertThat(kp1).isEqualTo(kp2)
        assertThat(kp2).isEqualTo(kp3)
    }
}
