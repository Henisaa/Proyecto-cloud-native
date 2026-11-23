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
import tools.jackson.core.JacksonException
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

data class TopicRequest(
    @field:Size(max = 249)
    @field:Pattern(regexp = "(shipments[.]events|audit[.]timeline|catalog[.]events|tutorial-particiones|sensor-topic|ordenes-creadas|rutaexpress[.][a-zA-Z0-9._-]+|[a-zA-Z0-9._-]+[.]DLT)")
    val name: String,
    @field:Min(1) @field:Max(100) val partitions: Int = 3,
    @field:Min(1) @field:Max(3) val replicas: Short = 3,
    @field:Min(60000) @field:Max(2592000000) val retentionMs: Long = 604800000,
    @field:Pattern(regexp = "delete|compact|compact,delete") val cleanupPolicy: String = "delete",
    @field:Min(1048576) val retentionBytes: Long? = null
)
data class PartitionRequest(@field:Min(1) @field:Max(100) val partitions: Int)
data class TopicRetention(
    @field:Min(60000) @field:Max(2592000000) val retentionMs: Long,
    @field:Pattern(regexp = "delete|compact|compact,delete") val cleanupPolicy: String = "delete",
    @field:Min(1048576) val retentionBytes: Long? = null
)
data class TopicConfigUpdateRequest(@field:NotEmpty val configs: Map<String, String>)
data class ResetOffsetsRequest(
    @field:NotBlank val topic: String,
    @field:Pattern(regexp = "earliest|latest") val strategy: String = "earliest"
)

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
            // 4. Buffer Memory: 64MB (Default 32MB) para acumular ráfagas masivas
            ProducerConfig.BUFFER_MEMORY_CONFIG to 64 * 1024 * 1024,
            // 5. Observabilidad: Propagar X-Correlation-Id en Headers
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
                JacksonException::class.java,
                DateTimeParseException::class.java,
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
        val topic = admin.describeTopics(listOf(name)).allTopicNames().get(timeout, TimeUnit.SECONDS)[name]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tópico '$name' no encontrado")
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        val config = admin.describeConfigs(listOf(resource)).all().get(timeout, TimeUnit.SECONDS)[resource]
        val entries = config?.entries().orEmpty()
            .filter { it.name() in ALLOWED_TOPIC_CONFIGS }
            .associate { it.name() to (it.value() ?: "") }
        return mapOf(
            "name" to name,
            "protected" to (name in PROTECTED_TOPICS),
            "partitions" to topic.partitions().map { p ->
                mapOf(
                    "partition" to p.partition(),
                    "leader" to (p.leader()?.id() ?: -1),
                    "replicas" to p.replicas().map { it.id() },
                    "inSyncReplicas" to p.isr().map { it.id() }
                )
            },
            "retentionMs" to (config?.get("retention.ms")?.value() ?: entries["retention.ms"] ?: ""),
            "retentionBytes" to (config?.get("retention.bytes")?.value() ?: entries["retention.bytes"] ?: "-1"),
            "cleanupPolicy" to (config?.get("cleanup.policy")?.value() ?: entries["cleanup.policy"] ?: "delete"),
            "minInsyncReplicas" to (config?.get("min.insync.replicas")?.value() ?: entries["min.insync.replicas"] ?: ""),
            "compressionType" to (config?.get("compression.type")?.value() ?: entries["compression.type"] ?: "producer"),
            "configs" to entries
        )
    }

    fun create(request: TopicRequest): Map<String, Any> {
        requireManaged(request.name)
        if (request.name in topics()) throw ResponseStatusException(HttpStatus.CONFLICT, "El tópico ya existe")
        val topicConfigs = mutableMapOf(
            "retention.ms" to request.retentionMs.toString(),
            "cleanup.policy" to request.cleanupPolicy,
            "min.insync.replicas" to (if (request.replicas >= 3) "2" else "1")
        )
        request.retentionBytes?.let { topicConfigs["retention.bytes"] = it.toString() }
        admin.createTopics(listOf(NewTopic(request.name, request.partitions, request.replicas).configs(topicConfigs)))
            .all().get(timeout, TimeUnit.SECONDS)
        return describe(request.name)
    }

    fun deleteTopic(name: String, force: Boolean = false): Map<String, Any> {
        requireManaged(name)
        if (name !in topics()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "El tópico '$name' no existe")
        }
        if (name in PROTECTED_TOPICS && !force) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "El tópico '$name' forma parte de la topología protegida del clúster; repita la operación con force=true si realmente desea eliminarlo"
            )
        }
        admin.deleteTopics(listOf(name)).all().get(timeout, TimeUnit.SECONDS)
        return mapOf("name" to name, "deleted" to true, "forced" to force)
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
        val ops = mutableListOf(
            AlterConfigOp(ConfigEntry("retention.ms", request.retentionMs.toString()), AlterConfigOp.OpType.SET),
            AlterConfigOp(ConfigEntry("cleanup.policy", request.cleanupPolicy), AlterConfigOp.OpType.SET)
        )
        request.retentionBytes?.let {
            ops += AlterConfigOp(ConfigEntry("retention.bytes", it.toString()), AlterConfigOp.OpType.SET)
        }
        admin.incrementalAlterConfigs(mapOf(resource to ops)).all().get(timeout, TimeUnit.SECONDS)
        return describe(name)
    }

    fun updateConfigs(name: String, request: TopicConfigUpdateRequest): Map<String, Any> {
        requireManaged(name)
        require(request.configs.isNotEmpty()) { "Debe indicar al menos una configuración para actualizar" }
        val ops = request.configs.map { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            validateTopicConfigEntry(key, value)
            AlterConfigOp(ConfigEntry(key, value), AlterConfigOp.OpType.SET)
        }
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        admin.incrementalAlterConfigs(mapOf(resource to ops)).all().get(timeout, TimeUnit.SECONDS)
        return describe(name)
    }

    fun deleteConfig(name: String, configKey: String): Map<String, Any> {
        requireManaged(name)
        val key = configKey.trim()
        require(key in ALLOWED_TOPIC_CONFIGS) {
            "Configuración no administrable: '$key'. Permitidas: ${ALLOWED_TOPIC_CONFIGS.sorted().joinToString(", ")}"
        }
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        admin.incrementalAlterConfigs(
            mapOf(resource to listOf(AlterConfigOp(ConfigEntry(key, ""), AlterConfigOp.OpType.DELETE)))
        ).all().get(timeout, TimeUnit.SECONDS)
        return describe(name)
    }

    fun reconcile(): List<String> {
        val existing = topics().toSet()
        val defaults = listOf(
            TopicRequest("tutorial-particiones", partitions = 3, replicas = replicas.coerceAtMost(3.toShort())),
            TopicRequest("shipments.events", replicas = replicas),
            TopicRequest("audit.timeline", replicas = replicas, retentionMs = 2592000000, cleanupPolicy = "compact,delete"),
            TopicRequest("catalog.events", replicas = replicas),
            TopicRequest("shipments.events.report.DLT", replicas = replicas, retentionMs = 1209600000),
            TopicRequest("shipments.events.audit.DLT", replicas = replicas, retentionMs = 1209600000),
            TopicRequest("audit.timeline.audit.DLT", replicas = replicas, retentionMs = 1209600000)
        )
        defaults.filter { it.name !in existing }.forEach(::create)
        return topics()
    }

    fun reconcileTuningAndErrorTopics(): List<String> {
        val existing = topics().toSet()
        val extras = listOf(
            TopicRequest("sensor-topic", partitions = 3, replicas = replicas.coerceAtMost(3.toShort()), retentionMs = 300000L),
            TopicRequest("ordenes-creadas", partitions = 3, replicas = replicas.coerceAtMost(3.toShort())),
            TopicRequest("ordenes-creadas.DLT", partitions = 3, replicas = replicas.coerceAtMost(3.toShort()), retentionMs = 1209600000L),
            TopicRequest("catalog.events.audit.DLT", partitions = 3, replicas = replicas.coerceAtMost(3.toShort()), retentionMs = 1209600000L)
        )
        extras.filter { it.name !in existing }.forEach { req ->
            try {
                create(req)
            } catch (_: ResponseStatusException) {
            }
        }
        return topics()
    }

    @Suppress("DEPRECATION")
    fun listGroups(): List<Map<String, Any>> = admin.listConsumerGroups().all().get(timeout, TimeUnit.SECONDS)
        .filter { managedGroup(it.groupId()) }
        .sortedBy { it.groupId() }
        .map { listing ->
            mapOf(
                "groupId" to listing.groupId(),
                "isSimpleConsumerGroup" to listing.isSimpleConsumerGroup(),
                "state" to (listing.groupState()?.map { it.name }?.orElse("UNKNOWN") ?: "UNKNOWN")
            )
        }

    fun describeGroup(group: String): Map<String, Any> {
        requireManagedGroup(group)
        val desc = admin.describeConsumerGroups(listOf(group)).all().get(timeout, TimeUnit.SECONDS)[group]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo '$group' no encontrado")
        val offsets = lag(group)
        val members = desc.members().orEmpty().map { m ->
            mapOf(
                "memberId" to (m.consumerId() ?: ""),
                "clientId" to (m.clientId() ?: ""),
                "host" to (m.host() ?: ""),
                "assignedPartitions" to m.assignment()?.topicPartitions().orEmpty()
                    .map { "${it.topic()}[${it.partition()}]" }
                    .sorted()
            )
        }
        return mapOf(
            "groupId" to desc.groupId(),
            "state" to (desc.groupState()?.name ?: "UNKNOWN"),
            "protected" to (group in PROTECTED_GROUPS),
            "isSimpleConsumerGroup" to desc.isSimpleConsumerGroup(),
            "partitionAssignor" to (desc.partitionAssignor() ?: ""),
            "coordinator" to (desc.coordinator()?.let { mapOf("id" to it.id(), "host" to it.host(), "port" to it.port()) } ?: emptyMap<String, Any>()),
            "members" to members,
            "partitions" to offsets,
            "totalLag" to offsets.sumOf { (it["lag"] as Number).toLong() }
        )
    }

    fun resetGroupOffsets(group: String, request: ResetOffsetsRequest): Map<String, Any> {
        requireManagedGroup(group)
        val topic = request.topic.trim()
        requireManaged(topic)
        val strategy = request.strategy.trim().lowercase()
        require(strategy in setOf("earliest", "latest")) { "La estrategia de reinicio debe ser earliest o latest" }

        val desc = admin.describeConsumerGroups(listOf(group)).all().get(timeout, TimeUnit.SECONDS)[group]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo '$group' no encontrado")
        val state = desc.groupState()?.name ?: "UNKNOWN"
        if (state !in setOf("EMPTY", "DEAD") && desc.members().orEmpty().isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "El grupo '$group' tiene consumidores activos (estado=$state); detenga el consumidor antes de reiniciar sus offsets"
            )
        }

        val topicDesc = admin.describeTopics(listOf(topic)).allTopicNames().get(timeout, TimeUnit.SECONDS)[topic]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tópico '$topic' no encontrado")
        val spec = if (strategy == "earliest") OffsetSpec.earliest() else OffsetSpec.latest()
        val partitions = topicDesc.partitions().map { TopicPartition(topic, it.partition()) }
        val targetOffsets = admin.listOffsets(partitions.associateWith { spec }).all().get(timeout, TimeUnit.SECONDS)
            .mapValues { (_, info) -> OffsetAndMetadata(info.offset()) }

        admin.alterConsumerGroupOffsets(group, targetOffsets).all().get(timeout, TimeUnit.SECONDS)
        return mapOf(
            "groupId" to group,
            "topic" to topic,
            "strategy" to strategy,
            "partitions" to targetOffsets.map { (tp, om) -> mapOf("partition" to tp.partition(), "newOffset" to om.offset()) }
        )
    }

    fun deleteGroup(group: String, force: Boolean = false): Map<String, Any> {
        requireManagedGroup(group)
        if (group in PROTECTED_GROUPS && !force) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "El grupo '$group' pertenece a un consumidor crítico del dominio; repita con force=true si realmente desea eliminarlo"
            )
        }
        val desc = admin.describeConsumerGroups(listOf(group)).all().get(timeout, TimeUnit.SECONDS)[group]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo '$group' no encontrado")
        val state = desc.groupState()?.name ?: "UNKNOWN"
        if (state !in setOf("EMPTY", "DEAD") && desc.members().orEmpty().isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "El grupo '$group' tiene consumidores activos (estado=$state) y no puede eliminarse mientras esté activo"
            )
        }
        admin.deleteConsumerGroups(listOf(group)).all().get(timeout, TimeUnit.SECONDS)
        return mapOf("groupId" to group, "deleted" to true, "forced" to force)
    }

    fun lag(group: String): List<Map<String, Any>> {
        requireManagedGroup(group)
        val offsets: Map<TopicPartition, OffsetAndMetadata> = admin.listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata().get(timeout, TimeUnit.SECONDS).filterKeys { managed(it.topic()) }
        if (offsets.isEmpty()) return emptyList()
        val ends = admin.listOffsets(offsets.keys.associateWith { OffsetSpec.latest() }).all().get(timeout, TimeUnit.SECONDS)
        return offsets.map { (partition, offset) ->
            mapOf(
                "topic" to partition.topic(),
                "partition" to partition.partition(),
                "committedOffset" to offset.offset(),
                "endOffset" to ends[partition]!!.offset(),
                "lag" to maxOf(0L, ends[partition]!!.offset() - offset.offset())
            )
        }
    }

    private fun validateTopicConfigEntry(key: String, value: String) {
        require(key in ALLOWED_TOPIC_CONFIGS) {
            "Configuración no administrable: '$key'. Permitidas: ${ALLOWED_TOPIC_CONFIGS.sorted().joinToString(", ")}"
        }
        require(value.isNotEmpty()) { "El valor de '$key' no puede estar vacío" }
        when (key) {
            "retention.ms", "segment.ms" -> {
                val ms = value.toLongOrNull() ?: throw IllegalArgumentException("'$key' debe ser un número entero en milisegundos")
                require(ms in 60_000L..2_592_000_000L) { "'$key' debe estar entre 60000 y 2592000000 ms" }
            }
            "retention.bytes" -> {
                val bytes = value.toLongOrNull() ?: throw IllegalArgumentException("'retention.bytes' debe ser entero (-1 o >= 1048576)")
                require(bytes == -1L || bytes >= 1_048_576L) { "'retention.bytes' debe ser -1 o al menos 1048576 bytes" }
            }
            "max.message.bytes" -> {
                val bytes = value.toLongOrNull() ?: throw IllegalArgumentException("'max.message.bytes' debe ser entero")
                require(bytes in 1024L..10_485_760L) { "'max.message.bytes' debe estar entre 1024 y 10485760 bytes" }
            }
            "min.insync.replicas" -> {
                val isr = value.toIntOrNull() ?: throw IllegalArgumentException("'min.insync.replicas' debe ser entero")
                require(isr in 1..3) { "'min.insync.replicas' debe estar entre 1 y 3" }
            }
            "cleanup.policy" -> require(value in setOf("delete", "compact", "compact,delete")) {
                "'cleanup.policy' debe ser delete, compact o compact,delete"
            }
            "compression.type" -> require(value in setOf("producer", "uncompressed", "snappy", "lz4", "zstd", "gzip")) {
                "'compression.type' inválido: '$value'"
            }
        }
    }

    private fun managed(name: String) = name in CORE_TOPICS ||
        name in STREAMS_TOPICS ||
        (name.length <= 249 && name.matches(Regex("rutaexpress[.][a-zA-Z0-9._-]+"))) ||
        (name.endsWith(".DLT") && name.length <= 249 && name.matches(Regex("(shipments[.]events|audit[.]timeline|catalog[.]events|tutorial-particiones|sensor-topic|ordenes-creadas)([.][a-zA-Z0-9._-]+)?[.]DLT")))

    private fun requireManaged(name: String) = require(managed(name)) { "Tópico fuera del dominio RutaExpress" }

    private fun managedGroup(group: String) = group.matches(
        Regex("ms-rutaexpress-(audit|report)-group|mi-grupo-educativo|rutaexpress-streams-processor|analytics-group|error-handling-group|dlt-audit-group|rutaexpress[.][a-zA-Z0-9._-]+-group")
    )

    private fun requireManagedGroup(group: String) = require(managedGroup(group)) { "Grupo fuera del dominio RutaExpress" }

    companion object {
        private val CORE_TOPICS = setOf(
            "shipments.events",
            "audit.timeline",
            "catalog.events",
            "tutorial-particiones",
            "sensor-topic",
            "ordenes-creadas"
        )
        private val STREAMS_TOPICS = setOf(
            "shipments.streams.alertas",
            "shipments.streams.estandar",
            "shipments.streams.prioritarios",
            "shipments.streams.por-servicio",
            "shipments.streams.por-estado",
            "shipments.streams.total-por-zona"
        )
        private val PROTECTED_TOPICS = CORE_TOPICS + STREAMS_TOPICS + setOf(
            "shipments.events.report.DLT",
            "shipments.events.audit.DLT",
            "audit.timeline.audit.DLT",
            "catalog.events.audit.DLT",
            "ordenes-creadas.DLT"
        )
        private val PROTECTED_GROUPS = setOf(
            "ms-rutaexpress-audit-group",
            "ms-rutaexpress-report-group",
            "rutaexpress-streams-processor"
        )
        private val ALLOWED_TOPIC_CONFIGS = setOf(
            "retention.ms",
            "retention.bytes",
            "cleanup.policy",
            "min.insync.replicas",
            "segment.ms",
            "max.message.bytes",
            "compression.type"
        )
    }
}
