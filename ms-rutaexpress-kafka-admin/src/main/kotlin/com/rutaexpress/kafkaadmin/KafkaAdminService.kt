package com.rutaexpress.kafkaadmin

import jakarta.validation.ConstraintViolationException
import jakarta.validation.constraints.*
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListenerConfigurer
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.stereotype.Service
import org.springframework.util.backoff.ExponentialBackOff
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class TopicRequest(
    @field:Pattern(regexp = "(shipments[.]events|audit[.]timeline|catalog[.]events|tutorial-particiones|sensor-topic|ordenes-creadas|[a-zA-Z0-9._-]+[.]DLT)") val name: String,
    @field:Min(1) @field:Max(100) val partitions: Int = 3,
    @field:Min(1) @field:Max(3) val replicas: Short = 3,
    @field:Min(60000) @field:Max(2592000000) val retentionMs: Long = 604800000,
    @field:Pattern(regexp = "delete|compact,delete") val cleanupPolicy: String = "delete"
)
data class PartitionRequest(@field:Min(1) @field:Max(100) val partitions: Int)
data class TopicRetention(@field:Min(60000) @field:Max(2592000000) val retentionMs: Long,
    @field:Pattern(regexp = "delete|compact,delete") val cleanupPolicy: String = "delete")

@Configuration
@EnableKafka
class KafkaClientConfig(
    @Autowired(required = false)
    private val validator: LocalValidatorFactoryBean? = null
) : KafkaListenerConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun configureKafkaListeners(registrar: KafkaListenerEndpointRegistrar) {
        if (validator != null) {
            registrar.setValidator(validator)
        }
    }

    @Bean(destroyMethod = "close")
    fun adminClient(@Value("\${spring.kafka.bootstrap-servers}") bootstrap: String): AdminClient =
        AdminClient.create(mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG to 10000,
            AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG to 15000
        ))

    @Bean
    fun producerFactory(@Value("\${spring.kafka.bootstrap-servers}") bootstrap: String): ProducerFactory<String, String> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.PARTITIONER_CLASS_CONFIG to TutorialPartitioner::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            // --- TUNING DE ALTO RENDIMIENTO ---
            // 1. Batch Size: Aumentar a 32KB (Default 16KB)
            ProducerConfig.BATCH_SIZE_CONFIG to 32 * 1024,
            // 2. Linger MS: Esperar 20ms antes de enviar
            ProducerConfig.LINGER_MS_CONFIG to 20,
            // 3. Compression: Snappy (Balance entre CPU y Red)
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy",
            // 4. Observabilidad: Propagar X-Correlation-Id en Headers
            ProducerConfig.INTERCEPTOR_CLASSES_CONFIG to listOf(CorrelationIdProducerInterceptor::class.java.name)
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)

    @Bean
    fun kafkaMdcRecordInterceptor(): RecordInterceptor<Any, Any> = object : RecordInterceptor<Any, Any> {
        override fun intercept(record: ConsumerRecord<Any, Any>, consumer: Consumer<Any, Any>): ConsumerRecord<Any, Any> {
            val header = record.headers().lastHeader("X-Correlation-Id") ?: record.headers().lastHeader("X-Trace-Id")
            val correlationId = header?.value()?.let { String(it, StandardCharsets.UTF_8) }
                ?: record.key()?.toString()
                ?: "${record.topic()}-${record.partition()}-${record.offset()}"
            MDC.put("correlationId", correlationId)
            return record
        }

        override fun afterRecord(record: ConsumerRecord<Any, Any>, consumer: Consumer<Any, Any>) {
            MDC.remove("correlationId")
        }
    }

    @Bean
    fun kafkaErrorHandler(template: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(template) { record, ex ->
            val correlationId = MDC.get("correlationId") ?: record.key()?.toString() ?: "N/A"
            logger.error("Fallo definitivo ID: {}, enviando a DLT ({}.DLT): {}", correlationId, record.topic(), ex.message)
            TopicPartition(record.topic() + ".DLT", record.partition())
        }
        recoverer.setFailIfSendResultIsError(true)

        val backOff = ExponentialBackOff(1000L, 2.0).apply {
            maxInterval = 8000L
            maxElapsedTime = 7000L
        }

        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(
                DeserializationException::class.java,
                MethodArgumentNotValidException::class.java,
                ConstraintViolationException::class.java,
                IllegalArgumentException::class.java
            )
            setRetryListeners(RetryListener { record, ex, deliveryAttempt ->
                val correlationId = MDC.get("correlationId") ?: record.key()?.toString() ?: "N/A"
                logger.warn("Fallo al procesar ID: {}, reintentando (intento {})... causa: {}", correlationId, deliveryAttempt, ex?.message)
            })
        }
    }
}

@Service
class KafkaAdminService(private val admin: AdminClient,
    @Value("\${app.kafka.replication-factor:3}") private val replicas: Short,
    @Value("\${app.kafka.timeout-seconds:10}") private val timeout: Long) {
    fun cluster(): Map<String, Any> {
        val cluster = admin.describeCluster()
        return mapOf("clusterId" to cluster.clusterId().get(timeout, TimeUnit.SECONDS),
            "nodes" to cluster.nodes().get(timeout, TimeUnit.SECONDS).map { mapOf("id" to it.id(), "host" to it.host(), "port" to it.port()) })
    }
    fun topics(): List<String> = admin.listTopics().names().get(timeout, TimeUnit.SECONDS).filter(::managed).sorted()
    fun describe(name: String): Map<String, Any> {
        requireManaged(name)
        val topic = admin.describeTopics(listOf(name)).allTopicNames().get(timeout, TimeUnit.SECONDS)[name]!!
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        val config = admin.describeConfigs(listOf(resource)).all().get(timeout, TimeUnit.SECONDS)[resource]!!
        return mapOf("name" to name, "partitions" to topic.partitions().map { p -> mapOf(
            "partition" to p.partition(), "leader" to (p.leader()?.id() ?: -1),
            "replicas" to p.replicas().map { it.id() }, "inSyncReplicas" to p.isr().map { it.id() }) },
            "retentionMs" to config.get("retention.ms").value(), "cleanupPolicy" to config.get("cleanup.policy").value())
    }
    fun create(request: TopicRequest): Map<String, Any> {
        requireManaged(request.name)
        if (request.name in topics()) throw ResponseStatusException(HttpStatus.CONFLICT, "El tópico ya existe")
        admin.createTopics(listOf(NewTopic(request.name, request.partitions, request.replicas).configs(mapOf(
            "retention.ms" to request.retentionMs.toString(), "cleanup.policy" to request.cleanupPolicy,
            "min.insync.replicas" to (if(request.replicas >= 3) "2" else "1")))
        )).all().get(timeout, TimeUnit.SECONDS)
        return describe(request.name)
    }
    fun partitions(name: String, count: Int): Map<String, Any> {
        requireManaged(name)
        val current = admin.describeTopics(listOf(name)).allTopicNames().get(timeout, TimeUnit.SECONDS)[name]!!.partitions().size
        require(count > current) { "Las particiones solo pueden aumentar: actual=$current" }
        admin.createPartitions(mapOf(name to NewPartitions.increaseTo(count))).all().get(timeout, TimeUnit.SECONDS)
        return describe(name)
    }
    fun retention(name: String, request: TopicRetention): Map<String, Any> {
        requireManaged(name)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        admin.incrementalAlterConfigs(mapOf(resource to listOf(
            AlterConfigOp(ConfigEntry("retention.ms", request.retentionMs.toString()), AlterConfigOp.OpType.SET),
            AlterConfigOp(ConfigEntry("cleanup.policy", request.cleanupPolicy), AlterConfigOp.OpType.SET)
        ))).all().get(timeout, TimeUnit.SECONDS)
        return describe(name)
    }
    fun reconcile(): List<String> {
        val existing = topics().toSet()
        val defaults = listOf(
            TopicRequest("tutorial-particiones", partitions = 3, replicas = replicas.coerceAtMost(3.toShort())),
            TopicRequest("shipments.events", replicas=replicas),
            TopicRequest("audit.timeline", replicas=replicas, retentionMs=2592000000, cleanupPolicy="compact,delete"),
            TopicRequest("catalog.events", replicas=replicas),
            TopicRequest("shipments.events.report.DLT", replicas=replicas, retentionMs=1209600000),
            TopicRequest("shipments.events.audit.DLT", replicas=replicas, retentionMs=1209600000),
            TopicRequest("audit.timeline.audit.DLT", replicas=replicas, retentionMs=1209600000)
        )
        defaults.filter { it.name !in existing }.forEach(::create)
        return topics()
    }
    fun reconcileTuningAndErrorTopics(): List<String> {
        val existing = topics().toSet()
        val extras = listOf(
            TopicRequest("sensor-topic", partitions = 3, replicas = replicas.coerceAtMost(3.toShort()), retentionMs = 300000L),
            TopicRequest("ordenes-creadas", partitions = 3, replicas = replicas.coerceAtMost(3.toShort())),
            TopicRequest("ordenes-creadas.DLT", partitions = 3, replicas = replicas.coerceAtMost(3.toShort()), retentionMs = 1209600000L)
        )
        extras.filter { it.name !in existing }.forEach { req ->
            try {
                create(req)
            } catch (_: ResponseStatusException) {
            }
        }
        return topics()
    }
    fun lag(group: String): List<Map<String, Any>> {
        require(group.matches(Regex("ms-rutaexpress-(audit|report)-group|mi-grupo-educativo|rutaexpress-streams-processor|analytics-group|error-handling-group"))) { "Grupo fuera del dominio RutaExpress" }
        val offsets: Map<TopicPartition, OffsetAndMetadata> = admin.listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata().get(timeout, TimeUnit.SECONDS).filterKeys { managed(it.topic()) }
        if(offsets.isEmpty()) return emptyList()
        val ends = admin.listOffsets(offsets.keys.associateWith { OffsetSpec.latest() }).all().get(timeout, TimeUnit.SECONDS)
        return offsets.map { (partition, offset) -> mapOf("topic" to partition.topic(), "partition" to partition.partition(),
            "committedOffset" to offset.offset(), "endOffset" to ends[partition]!!.offset(),
            "lag" to maxOf(0L, ends[partition]!!.offset()-offset.offset())) }
    }
    private fun managed(name: String) = name in setOf("shipments.events", "audit.timeline", "catalog.events", "tutorial-particiones", "sensor-topic", "ordenes-creadas") ||
        (name.endsWith(".DLT") && name.length <= 249 && name.matches(Regex("(shipments[.]events|audit[.]timeline|catalog[.]events|tutorial-particiones|sensor-topic|ordenes-creadas)([.][a-zA-Z0-9._-]+)?[.]DLT")))
    private fun requireManaged(name: String) = require(managed(name)) { "Tópico fuera del dominio RutaExpress" }
}
