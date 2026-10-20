package com.rutaexpress.kafkaadmin

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.Node
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ConfigEntry
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class KafkaAdminServiceTests {
    private val admin = mock(AdminClient::class.java, RETURNS_DEEP_STUBS)
    private val service = KafkaAdminService(admin, 3, 2)

    @Test
    fun `solo lista topicos propios y DLT validos en orden`() {
        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(setOf(
            "catalog.events", "shipments.events", "audit.timeline", "shipments.events.report.DLT",
            "other.DLT", "system", "shipments.events." + "x".repeat(250) + ".DLT", "shipments.events.bad name.DLT",
        )))
        assertThat(service.topics()).containsExactly("audit.timeline", "catalog.events", "shipments.events", "shipments.events.report.DLT")
    }

    @Test
    fun `rechaza cambios fuera del dominio antes de consultar Kafka`() {
        for (operation in listOf<() -> Any>(
            { service.describe("system") }, { service.create(TopicRequest("system")) },
            { service.partitions("system", 3) }, { service.retention("other.DLT", TopicRetention(86400000, "delete")) },
            { service.lag("another-group") },
        )) assertThatThrownBy { operation() }.isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(admin)
    }

    @Test
    fun `un topico existente responde conflicto sin crearlo de nuevo`() {
        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(setOf("shipments.events")))
        assertThatThrownBy { service.create(TopicRequest("shipments.events")) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("409")
    }

    @Test
    fun `reconciliar es idempotente cuando la topologia ya existe`() {
        val topics = setOf("shipments.events", "audit.timeline", "catalog.events", "shipments.events.report.DLT", "shipments.events.audit.DLT", "audit.timeline.audit.DLT")
        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(topics))
        assertThat(service.reconcile()).containsExactlyElementsOf(topics.sorted())
    }

    @Test
    fun `grupo propio sin offsets devuelve lista vacia`() {
        `when`(admin.listConsumerGroupOffsets("ms-rutaexpress-audit-group").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))
        assertThat(service.lag("ms-rutaexpress-audit-group")).isEmpty()
    }

    @Test
    fun `cluster devuelve identificador y nodos sin filtrar datos inventados`() {
        `when`(admin.describeCluster().clusterId()).thenReturn(KafkaFuture.completedFuture("ci-cluster"))
        `when`(admin.describeCluster().nodes()).thenReturn(KafkaFuture.completedFuture(listOf(Node(1, "kafka-1", 29092))))
        val cluster = service.cluster()
        assertThat(cluster["clusterId"]).isEqualTo("ci-cluster")
        assertThat(cluster["nodes"].toString()).contains("kafka-1", "29092")
    }

    @Test
    fun `no se permite reducir particiones`() {
        val topic = TopicDescription("shipments.events", false, listOf(
            org.apache.kafka.common.TopicPartitionInfo(0, Node(1, "kafka", 29092), emptyList(), emptyList()),
        ))
        `when`(admin.describeTopics(listOf("shipments.events")).allTopicNames())
            .thenReturn(KafkaFuture.completedFuture(mapOf("shipments.events" to topic)))
        assertThatThrownBy { service.partitions("shipments.events", 1) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("solo pueden aumentar")
    }
}
