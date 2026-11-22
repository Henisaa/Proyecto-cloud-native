package com.rutaexpress.kafkaadmin

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.ConsumerGroupDescription
import org.apache.kafka.clients.admin.ConsumerGroupListing
import org.apache.kafka.clients.admin.MemberAssignment
import org.apache.kafka.clients.admin.MemberDescription
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.GroupState
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.Node
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.TopicPartitionInfo
import org.apache.kafka.common.config.ConfigResource
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

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
        val topics = setOf("tutorial-particiones", "shipments.events", "audit.timeline", "catalog.events", "shipments.events.report.DLT", "shipments.events.audit.DLT", "audit.timeline.audit.DLT")
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
    fun `permite consultar lag para mi-grupo-educativo y rutaexpress-streams-processor`() {
        `when`(admin.listConsumerGroupOffsets("mi-grupo-educativo").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))
        `when`(admin.listConsumerGroupOffsets("rutaexpress-streams-processor").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))
        assertThat(service.lag("mi-grupo-educativo")).isEmpty()
        assertThat(service.lag("rutaexpress-streams-processor")).isEmpty()
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

    @Test
    fun `crea, actualiza configuracion, restablece y elimina topico personalizado con proteccion de nucleo`() {
        val customTopic = "rutaexpress.demo.ep5"
        val node = Node(1, "kafka-1", 29092)
        val topicDesc = TopicDescription(customTopic, false, listOf(TopicPartitionInfo(0, node, listOf(node), listOf(node))))
        val resource = ConfigResource(ConfigResource.Type.TOPIC, customTopic)
        val cfg = Config(listOf(
            ConfigEntry("retention.ms", "300000"),
            ConfigEntry("retention.bytes", "52428800"),
            ConfigEntry("cleanup.policy", "compact"),
            ConfigEntry("min.insync.replicas", "2")
        ))

        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(setOf("shipments.events")))
        `when`(admin.createTopics(anyCollection()).all()).thenReturn(KafkaFuture.completedFuture(null))
        `when`(admin.describeTopics(listOf(customTopic)).allTopicNames()).thenReturn(KafkaFuture.completedFuture(mapOf(customTopic to topicDesc)))
        `when`(admin.describeConfigs(listOf(resource)).all()).thenReturn(KafkaFuture.completedFuture(mapOf(resource to cfg)))
        `when`(admin.incrementalAlterConfigs(anyMap()).all()).thenReturn(KafkaFuture.completedFuture(null))
        `when`(admin.deleteTopics(anyCollection()).all()).thenReturn(KafkaFuture.completedFuture(null))

        val created = service.create(TopicRequest(name = customTopic, retentionBytes = 52428800L, cleanupPolicy = "compact"))
        assertThat(created["name"]).isEqualTo(customTopic)
        assertThat(created["protected"]).isEqualTo(false)
        assertThat(created["retentionBytes"]).isEqualTo("52428800")

        val updated = service.updateConfigs(customTopic, TopicConfigUpdateRequest(mapOf("retention.ms" to "600000", "cleanup.policy" to "compact,delete")))
        assertThat(updated["name"]).isEqualTo(customTopic)

        val reset = service.deleteConfig(customTopic, "retention.ms")
        assertThat(reset["name"]).isEqualTo(customTopic)

        // Proteccion de topico nucleo sin force=true -> 409; inexistente -> 404
        `when`(admin.listTopics().names()).thenReturn(KafkaFuture.completedFuture(setOf("shipments.events", customTopic)))
        assertThatThrownBy { service.deleteTopic("shipments.events", false) }
            .isInstanceOf(ResponseStatusException::class.java).hasMessageContaining("409")
        assertThatThrownBy { service.deleteTopic("rutaexpress.noexiste", false) }
            .isInstanceOf(ResponseStatusException::class.java).hasMessageContaining("404")

        val deleted = service.deleteTopic(customTopic, false)
        assertThat(deleted["deleted"]).isEqualTo(true)

        // Validaciones negativas de configuracion
        assertThatThrownBy { service.updateConfigs(customTopic, TopicConfigUpdateRequest(mapOf("unknown.key" to "1"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.updateConfigs(customTopic, TopicConfigUpdateRequest(mapOf("retention.ms" to "100"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.updateConfigs(customTopic, TopicConfigUpdateRequest(mapOf("cleanup.policy" to "invalid"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.deleteConfig(customTopic, "unknown.key") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `lista e inspecciona consumer groups del dominio incluyendo miembros y lag total`() {
        val listingAudit = mock(ConsumerGroupListing::class.java).apply {
            `when`(groupId()).thenReturn("ms-rutaexpress-audit-group")
            `when`(isSimpleConsumerGroup()).thenReturn(false)
            `when`(groupState()).thenReturn(Optional.of(GroupState.STABLE))
        }
        val listingForeign = mock(ConsumerGroupListing::class.java).apply {
            `when`(groupId()).thenReturn("otro-grupo-externo")
            `when`(isSimpleConsumerGroup()).thenReturn(false)
            `when`(groupState()).thenReturn(Optional.of(GroupState.EMPTY))
        }
        `when`(admin.listConsumerGroups().all()).thenReturn(KafkaFuture.completedFuture(listOf(listingAudit, listingForeign)))

        val groups = service.listGroups()
        assertThat(groups).hasSize(1)
        assertThat(groups[0]["groupId"]).isEqualTo("ms-rutaexpress-audit-group")
        assertThat(groups[0]["state"]).isEqualTo("STABLE")

        val member = mock(MemberDescription::class.java).apply {
            `when`(consumerId()).thenReturn("consumer-1")
            `when`(clientId()).thenReturn("audit-app")
            `when`(host()).thenReturn("/10.0.0.5")
            `when`(assignment()).thenReturn(MemberAssignment(setOf(TopicPartition("shipments.events", 0))))
        }
        val desc = mock(ConsumerGroupDescription::class.java).apply {
            `when`(groupId()).thenReturn("ms-rutaexpress-audit-group")
            `when`(groupState()).thenReturn(GroupState.STABLE)
            `when`(isSimpleConsumerGroup()).thenReturn(false)
            `when`(partitionAssignor()).thenReturn("CooperativeStickyAssignor")
            `when`(coordinator()).thenReturn(Node(1, "kafka-1", 29092))
            `when`(members()).thenReturn(listOf(member))
        }
        `when`(admin.describeConsumerGroups(listOf("ms-rutaexpress-audit-group")).all())
            .thenReturn(KafkaFuture.completedFuture(mapOf("ms-rutaexpress-audit-group" to desc)))
        `when`(admin.listConsumerGroupOffsets("ms-rutaexpress-audit-group").partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(emptyMap()))

        val detail = service.describeGroup("ms-rutaexpress-audit-group")
        assertThat(detail["state"]).isEqualTo("STABLE")
        assertThat(detail["partitionAssignor"]).isEqualTo("CooperativeStickyAssignor")
        assertThat(detail["totalLag"]).isEqualTo(0L)
        @Suppress("UNCHECKED_CAST")
        val members = detail["members"] as List<Map<String, Any>>
        assertThat(members).hasSize(1)
        assertThat(members[0]["assignedPartitions"]).isEqualTo(listOf("shipments.events[0]"))
    }
}
