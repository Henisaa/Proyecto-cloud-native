package com.rutaexpress.kafkaadmin

import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Branched
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ReadOnlyWindowStore
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round

data class EventoEnvioStream(
    val eventId: String = UUID.randomUUID().toString(),
    val shipmentId: String,
    val trackingCode: String = "RX-${shipmentId.takeLast(6).uppercase()}",
    val serviceType: String = "ESTANDAR",
    val status: String = "CREADO",
    val zone: String = "CENTRO",
    val weightKg: Double = 2.5,
    val packagesCount: Int = 1,
    val price: Double = 4500.0,
    val timestamp: String = Instant.now().toString(),
    val priorityCategory: String = "ESTANDAR"
)

data class AcumuladoZonaState(
    val zone: String,
    val totalShipments: Long,
    val totalPackages: Int,
    val totalWeightKg: Double,
    val totalRevenue: Double,
    val lastStatus: String,
    val updatedAt: String
)

data class VentanaConteoDto(
    val key: String,
    val windowType: String,
    val windowStart: String,
    val windowEnd: String,
    val count: Long
)

data class ResumenStreamsEstadoDto(
    val applicationId: String,
    val inputTopic: String,
    val streamsState: String,
    val topologyDescription: String,
    val totalProcessed: Long,
    val branchCounts: Map<String, Long>,
    val countsByService: Map<String, Long>,
    val countsByStatus: Map<String, Long>,
    val reducedByZone: List<AcumuladoZonaState>,
    val tumblingWindowsByZone: List<VentanaConteoDto>,
    val hoppingWindowsByService: List<VentanaConteoDto>,
    val recentAlertsAndFiltered: List<EventoEnvioStream>
)

data class SimulacionEnvioRequest(
    val shipmentId: String? = null,
    val serviceType: String = "EXPRESS",
    val status: String = "EN_TRANSITO",
    val zone: String = "NORTE",
    val weightKg: Double = 14.5,
    val packagesCount: Int = 3,
    val price: Double = 18500.0
)

/**
 * Procesador en tiempo real con Apache Kafka Streams adaptado a entidades de envíos (shipments.events).
 *
 * Implementa los 4 pilares de la guía de procesamiento en tiempo real:
 * 1. Topología y Consumer Group de Streams: application.id = "rutaexpress-streams-processor"
 * 2. Operaciones Stateless (Sin Estado):
 *    - map(): Normaliza cada evento de envío y clasifica su prioridad operacional.
 *    - filter(): Filtra envíos prioritarios/críticos (EXPRESS, MISMO_DIA, peso >= 10kg, monto >= $15.000 o CANCELADO).
 *    - split() / branch(): Divide el stream en 2 caminos: rama-estandar (< 10kg estándar) y rama-prioritaria (>= 10kg o express).
 * 3. Operaciones Stateful (Con Estado):
 *    - groupByKey().count(): Cuenta eventos por serviceType y por status.
 *    - groupByKey().reduce(): Combina y acumula paquetes, peso (kg) y monto ($) por zona geográfica.
 * 4. Windowing (Procesamiento por Ventanas) y State Stores (Interactive Queries):
 *    - Tumbling Window (5 min, no superpuestas) por zona.
 *    - Hopping Window (10 min con avance de 5 min, superpuestas) por tipo de servicio.
 *    - State Stores consultables en tiempo real sin detener la aplicación.
 */
@Service
class KafkaStreamsService(
    @Autowired(required = false) private val kafkaTemplate: KafkaTemplate<String, String>? = null,
    @Autowired(required = false) private val adminClient: AdminClient? = null,
    @Autowired(required = false) objectMapper: ObjectMapper? = null,
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String = "localhost:9092",
    @Value("\${app.kafka.streams.application-id:rutaexpress-streams-processor}") private val applicationId: String = APPLICATION_ID,
    @Value("\${app.kafka.streams.auto-startup:true}") private val autoStartup: Boolean = false
) {
    private val logger = LoggerFactory.getLogger(KafkaStreamsService::class.java)
    private val mapper: ObjectMapper = objectMapper ?: JsonMapper.builder().findAndAddModules().build()

    @Volatile
    private var kafkaStreams: KafkaStreams? = null
    private val topologia: Topology by lazy { buildTopology() }

    private val totalProcesados = AtomicLong(0)
    private val contadorRamaEstandar = AtomicLong(0)
    private val contadorRamaPrioritaria = AtomicLong(0)
    private val alertasFiltradas = ConcurrentLinkedDeque<EventoEnvioStream>()

    // Respaldo en memoria sincronizado para consultas instantáneas durante rebalanceo o modo sin broker
    private val conteoServicioFallback = ConcurrentHashMap<String, Long>()
    private val conteoEstadoFallback = ConcurrentHashMap<String, Long>()
    private val acumuladoZonaFallback = ConcurrentHashMap<String, AcumuladoZonaState>()

    companion object {
        const val APPLICATION_ID = "rutaexpress-streams-processor"
        const val INPUT_TOPIC = "shipments.events"
        const val TOPIC_ALERTAS_FILTRADAS = "shipments.streams.alertas"
        const val TOPIC_RAMA_ESTANDAR = "shipments.streams.estandar"
        const val TOPIC_RAMA_PRIORITARIA = "shipments.streams.prioritarios"
        const val TOPIC_CONTEO_SERVICIO = "shipments.streams.por-servicio"
        const val TOPIC_CONTEO_ESTADO = "shipments.streams.por-estado"
        const val TOPIC_ACUMULADO_ZONA = "shipments.streams.total-por-zona"

        const val STORE_CONTEO_SERVICIO = "store-conteo-por-servicio"
        const val STORE_CONTEO_ESTADO = "store-conteo-por-estado"
        const val STORE_ACUMULADO_ZONA = "store-acumulado-por-zona"
        const val STORE_VENTANA_TUMBLING_ZONA = "store-ventana-tumbling-zona"
        const val STORE_VENTANA_HOPPING_SERVICIO = "store-ventana-hopping-servicio"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun iniciarEnArranque() {
        if (!autoStartup) return
        Thread({
            try {
                asegurarTopicosStreams()
                iniciarStreams()
            } catch (ex: Exception) {
                logger.warn("Kafka Streams no pudo iniciar en arranque automático: {}", ex.message)
            }
        }, "kafka-streams-starter").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun iniciarStreams() {
        if (kafkaStreams != null) return
        val streams = KafkaStreams(topologia, streamsProperties())
        streams.start()
        kafkaStreams = streams
        logger.info("Kafka Streams iniciado con application.id={}", applicationId)
    }

    @PreDestroy
    @Synchronized
    fun detenerStreams() {
        try {
            kafkaStreams?.close(Duration.ofSeconds(5))
        } catch (_: Exception) {
        } finally {
            kafkaStreams = null
        }
    }

    fun streamsProperties(): Properties {
        val stateDir = Files.createTempDirectory("rutaexpress-kstreams-").toFile().absolutePath
        return Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().javaClass.name)
            put(StreamsConfig.STATE_DIR_CONFIG, stateDir)
            put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 500)
        }
    }

    /**
     * Construye la Topología de Kafka Streams (Source -> Stateless -> Stateful -> Windowing -> Sink/StateStore).
     */
    fun buildTopology(): Topology {
        val builder = StreamsBuilder()
        val stringSerde = Serdes.String()
        val longSerde = Serdes.Long()

        val rawStream: KStream<String, String> = builder.stream(
            INPUT_TOPIC,
            Consumed.with(stringSerde, stringSerde)
        )

        // A1. Stateless: map() -> Transforma y enriquece cada evento de envío
        val mappedStream: KStream<String, String> = rawStream.map { key, rawValue ->
            val envio = parsearYEnriquecer(key, rawValue)
            registrarEnMemoria(envio)
            KeyValue(envio.shipmentId, mapper.writeValueAsString(envio))
        }

        // A2. Stateless: filter() -> Filtra solo envíos de alta prioridad, carga pesada (>= 10 kg), monto >= $15.000 o cancelados
        val filteredStream: KStream<String, String> = mappedStream.filter { _, json ->
            val ev = leerEvento(json)
            val relevante = ev != null && esPrioritarioOAlerta(ev)
            if (relevante && ev != null) {
                registrarAlertaFiltrada(ev)
            }
            relevante
        }
        filteredStream.to(TOPIC_ALERTAS_FILTRADAS, Produced.with(stringSerde, stringSerde))

        // A3. Stateless: split() / branch() -> Divide el stream en 2 caminos (Estándar vs Prioritario)
        val ramas: Map<String, KStream<String, String>> = mappedStream
            .split(Named.`as`("rama-"))
            .branch(
                { _, json ->
                    val ev = leerEvento(json)
                    ev != null && ev.priorityCategory == "ESTANDAR"
                },
                Branched.`as`("estandar")
            )
            .defaultBranch(Branched.`as`("prioritaria"))

        ramas["rama-estandar"]
            ?.peek { _, _ -> contadorRamaEstandar.incrementAndGet() }
            ?.to(TOPIC_RAMA_ESTANDAR, Produced.with(stringSerde, stringSerde))

        ramas["rama-prioritaria"]
            ?.peek { _, _ -> contadorRamaPrioritaria.incrementAndGet() }
            ?.to(TOPIC_RAMA_PRIORITARIA, Produced.with(stringSerde, stringSerde))

        // B1. Stateful: groupByKey().count() por Tipo de Servicio -> State Store "store-conteo-por-servicio"
        mappedStream
            .selectKey { _, json -> leerEvento(json)?.serviceType ?: "ESTANDAR" }
            .groupByKey(Grouped.with(stringSerde, stringSerde))
            .count(
                Materialized.`as`<String, Long>(Stores.inMemoryKeyValueStore(STORE_CONTEO_SERVICIO))
                    .withKeySerde(stringSerde)
                    .withValueSerde(longSerde)
            )
            .toStream()
            .to(TOPIC_CONTEO_SERVICIO, Produced.with(stringSerde, longSerde))

        // B2. Stateful: groupByKey().count() por Estado del Envío -> State Store "store-conteo-por-estado"
        mappedStream
            .selectKey { _, json -> leerEvento(json)?.status ?: "CREADO" }
            .groupByKey(Grouped.with(stringSerde, stringSerde))
            .count(
                Materialized.`as`<String, Long>(Stores.inMemoryKeyValueStore(STORE_CONTEO_ESTADO))
                    .withKeySerde(stringSerde)
                    .withValueSerde(longSerde)
            )
            .toStream()
            .to(TOPIC_CONTEO_ESTADO, Produced.with(stringSerde, longSerde))

        // B3. Stateful: groupByKey().reduce() por Zona -> Combina envíos acumulando paquetes, peso y monto
        mappedStream
            .map { _, json ->
                val ev = leerEvento(json)
                val zona = ev?.zone ?: "CENTRO"
                val estadoInicial = AcumuladoZonaState(
                    zone = zona,
                    totalShipments = 1L,
                    totalPackages = ev?.packagesCount ?: 1,
                    totalWeightKg = redondear(ev?.weightKg ?: 1.0),
                    totalRevenue = redondear(ev?.price ?: 0.0),
                    lastStatus = ev?.status ?: "CREADO",
                    updatedAt = ev?.timestamp ?: Instant.now().toString()
                )
                KeyValue(zona, mapper.writeValueAsString(estadoInicial))
            }
            .groupByKey(Grouped.with(stringSerde, stringSerde))
            .reduce(
                { acumuladoJson, nuevoJson ->
                    val actual = mapper.readValue(acumuladoJson, AcumuladoZonaState::class.java)
                    val nuevo = mapper.readValue(nuevoJson, AcumuladoZonaState::class.java)
                    val reducido = combinarZona(actual, nuevo)
                    mapper.writeValueAsString(reducido)
                },
                Materialized.`as`<String, String>(Stores.inMemoryKeyValueStore(STORE_ACUMULADO_ZONA))
                    .withKeySerde(stringSerde)
                    .withValueSerde(stringSerde)
            )
            .toStream()
            .to(TOPIC_ACUMULADO_ZONA, Produced.with(stringSerde, stringSerde))

        // C1. Windowing: Tumbling Window (Ventanas de 5 minutos no superpuestas) por Zona
        mappedStream
            .selectKey { _, json -> leerEvento(json)?.zone ?: "CENTRO" }
            .groupByKey(Grouped.with(stringSerde, stringSerde))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)))
            .count(
                Materialized.`as`<String, Long>(
                    Stores.inMemoryWindowStore(
                        STORE_VENTANA_TUMBLING_ZONA,
                        Duration.ofHours(1),
                        Duration.ofMinutes(5),
                        false
                    )
                )
                    .withKeySerde(stringSerde)
                    .withValueSerde(longSerde)
            )

        // C2. Windowing: Hopping Window (Ventana de 10 min con avance de 5 min, superpuestas) por Servicio
        mappedStream
            .selectKey { _, json -> leerEvento(json)?.serviceType ?: "ESTANDAR" }
            .groupByKey(Grouped.with(stringSerde, stringSerde))
            .windowedBy(
                TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(10))
                    .advanceBy(Duration.ofMinutes(5))
            )
            .count(
                Materialized.`as`<String, Long>(
                    Stores.inMemoryWindowStore(
                        STORE_VENTANA_HOPPING_SERVICIO,
                        Duration.ofHours(1),
                        Duration.ofMinutes(10),
                        false
                    )
                )
                    .withKeySerde(stringSerde)
                    .withValueSerde(longSerde)
            )

        return builder.build()
    }

    /**
     * Interactive Queries: Consulta el estado de los State Stores sin detener la aplicación.
     */
    fun consultarEstado(): ResumenStreamsEstadoDto {
        val streams = kafkaStreams
        if (streams != null && streams.state() == KafkaStreams.State.RUNNING) {
            try {
                val servicioStore = streams.store(
                    StoreQueryParameters.fromNameAndType(STORE_CONTEO_SERVICIO, QueryableStoreTypes.keyValueStore<String, Long>())
                )
                val estadoStore = streams.store(
                    StoreQueryParameters.fromNameAndType(STORE_CONTEO_ESTADO, QueryableStoreTypes.keyValueStore<String, Long>())
                )
                val zonaStore = streams.store(
                    StoreQueryParameters.fromNameAndType(STORE_ACUMULADO_ZONA, QueryableStoreTypes.keyValueStore<String, String>())
                )
                val tumblingStore = streams.store(
                    StoreQueryParameters.fromNameAndType(STORE_VENTANA_TUMBLING_ZONA, QueryableStoreTypes.windowStore<String, Long>())
                )
                val hoppingStore = streams.store(
                    StoreQueryParameters.fromNameAndType(STORE_VENTANA_HOPPING_SERVICIO, QueryableStoreTypes.windowStore<String, Long>())
                )
                return extraerEstadoDeStores(
                    servicioStore = servicioStore,
                    estadoStore = estadoStore,
                    zonaStore = zonaStore,
                    tumblingStore = tumblingStore,
                    hoppingStore = hoppingStore,
                    stateName = streams.state().name
                )
            } catch (ex: Exception) {
                logger.debug("StateStore en rebalanceo, usando snapshot local: {}", ex.message)
            }
        }

        val ahoraMs = System.currentTimeMillis()
        val inicioTumbling = (ahoraMs / 300_000L) * 300_000L
        val finTumbling = inicioTumbling + 300_000L
        val inicioHopping = (ahoraMs / 300_000L) * 300_000L
        val finHopping = inicioHopping + 600_000L

        return ResumenStreamsEstadoDto(
            applicationId = applicationId,
            inputTopic = INPUT_TOPIC,
            streamsState = streams?.state()?.name ?: "RUNNING_LOCAL_STORE",
            topologyDescription = topologia.describe().toString(),
            totalProcessed = totalProcesados.get(),
            branchCounts = mapOf(
                "rama-estandar" to contadorRamaEstandar.get(),
                "rama-prioritaria" to contadorRamaPrioritaria.get()
            ),
            countsByService = conteoServicioFallback.toSortedMap(),
            countsByStatus = conteoEstadoFallback.toSortedMap(),
            reducedByZone = acumuladoZonaFallback.values.sortedByDescending { it.totalRevenue },
            tumblingWindowsByZone = acumuladoZonaFallback.values.map {
                VentanaConteoDto(
                    key = it.zone,
                    windowType = "TUMBLING_5M",
                    windowStart = Instant.ofEpochMilli(inicioTumbling).toString(),
                    windowEnd = Instant.ofEpochMilli(finTumbling).toString(),
                    count = it.totalShipments
                )
            },
            hoppingWindowsByService = conteoServicioFallback.entries.map {
                VentanaConteoDto(
                    key = it.key,
                    windowType = "HOPPING_10M_5M",
                    windowStart = Instant.ofEpochMilli(inicioHopping).toString(),
                    windowEnd = Instant.ofEpochMilli(finHopping).toString(),
                    count = it.value
                )
            },
            recentAlertsAndFiltered = alertasFiltradas.toList()
        )
    }

    /**
     * Extrae los datos directamente desde las interfaces ReadOnlyKeyValueStore y ReadOnlyWindowStore de Kafka Streams.
     */
    fun extraerEstadoDeStores(
        servicioStore: ReadOnlyKeyValueStore<String, Long>,
        estadoStore: ReadOnlyKeyValueStore<String, Long>,
        zonaStore: ReadOnlyKeyValueStore<String, String>,
        tumblingStore: ReadOnlyWindowStore<String, Long>,
        hoppingStore: ReadOnlyWindowStore<String, Long>,
        stateName: String = "RUNNING"
    ): ResumenStreamsEstadoDto {
        val porServicio = mutableMapOf<String, Long>()
        servicioStore.all().use { iter ->
            while (iter.hasNext()) {
                val kv = iter.next()
                porServicio[kv.key] = kv.value
            }
        }

        val porEstado = mutableMapOf<String, Long>()
        estadoStore.all().use { iter ->
            while (iter.hasNext()) {
                val kv = iter.next()
                porEstado[kv.key] = kv.value
            }
        }

        val porZona = mutableListOf<AcumuladoZonaState>()
        zonaStore.all().use { iter ->
            while (iter.hasNext()) {
                val kv = iter.next()
                try {
                    porZona.add(mapper.readValue(kv.value, AcumuladoZonaState::class.java))
                } catch (_: Exception) {
                }
            }
        }

        val ventanasTumbling = mutableListOf<VentanaConteoDto>()
        tumblingStore.all().use { iter ->
            while (iter.hasNext()) {
                val kv = iter.next()
                ventanasTumbling.add(
                    VentanaConteoDto(
                        key = kv.key.key(),
                        windowType = "TUMBLING_5M",
                        windowStart = Instant.ofEpochMilli(kv.key.window().start()).toString(),
                        windowEnd = Instant.ofEpochMilli(kv.key.window().end()).toString(),
                        count = kv.value
                    )
                )
            }
        }

        val ventanasHopping = mutableListOf<VentanaConteoDto>()
        hoppingStore.all().use { iter ->
            while (iter.hasNext()) {
                val kv = iter.next()
                ventanasHopping.add(
                    VentanaConteoDto(
                        key = kv.key.key(),
                        windowType = "HOPPING_10M_5M",
                        windowStart = Instant.ofEpochMilli(kv.key.window().start()).toString(),
                        windowEnd = Instant.ofEpochMilli(kv.key.window().end()).toString(),
                        count = kv.value
                    )
                )
            }
        }

        return ResumenStreamsEstadoDto(
            applicationId = applicationId,
            inputTopic = INPUT_TOPIC,
            streamsState = stateName,
            topologyDescription = topologia.describe().toString(),
            totalProcessed = totalProcesados.get(),
            branchCounts = mapOf(
                "rama-estandar" to contadorRamaEstandar.get(),
                "rama-prioritaria" to contadorRamaPrioritaria.get()
            ),
            countsByService = porServicio.toSortedMap(),
            countsByStatus = porEstado.toSortedMap(),
            reducedByZone = porZona.sortedByDescending { it.totalRevenue },
            tumblingWindowsByZone = ventanasTumbling.sortedByDescending { it.windowStart }.take(15),
            hoppingWindowsByService = ventanasHopping.sortedByDescending { it.windowStart }.take(15),
            recentAlertsAndFiltered = alertasFiltradas.toList()
        )
    }

    /**
     * Publica un evento de envío en shipments.events para su procesamiento en tiempo real por Kafka Streams.
     */
    fun simularEnvio(req: SimulacionEnvioRequest): EventoEnvioStream {
        val id = req.shipmentId?.takeIf { it.isNotBlank() } ?: "envio-${UUID.randomUUID().toString().take(8)}"
        val crudo = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "shipmentId" to id,
            "trackingCode" to "RX-${id.takeLast(6).uppercase()}",
            "serviceType" to req.serviceType.uppercase(),
            "status" to req.status.uppercase(),
            "zone" to req.zone.uppercase(),
            "weightKg" to req.weightKg,
            "packagesCount" to req.packagesCount.coerceAtLeast(1),
            "price" to req.price.coerceAtLeast(0.0),
            "timestamp" to Instant.now().toString()
        )
        val json = mapper.writeValueAsString(crudo)
        val enriquecido = parsearYEnriquecer(id, json)

        kafkaTemplate?.send(INPUT_TOPIC, id, json)

        // Si KafkaStreams aún no está conectado a un broker externo, actualiza el espejo local para respuesta inmediata
        if (kafkaStreams?.state() != KafkaStreams.State.RUNNING) {
            registrarEnMemoria(enriquecido)
            if (esPrioritarioOAlerta(enriquecido)) registrarAlertaFiltrada(enriquecido)
            if (enriquecido.priorityCategory == "ESTANDAR") {
                contadorRamaEstandar.incrementAndGet()
            } else {
                contadorRamaPrioritaria.incrementAndGet()
            }
        }
        return enriquecido
    }

    /**
     * Ejecuta escenarios predefinidos de envíos para demostrar todas las operaciones de Kafka Streams.
     */
    fun simularEscenario(scenario: String): List<EventoEnvioStream> {
        val lotes = when (scenario.lowercase()) {
            "prioritario" -> listOf(
                SimulacionEnvioRequest(serviceType = "EXPRESS", status = "EN_TRANSITO", zone = "NORTE", weightKg = 18.5, packagesCount = 4, price = 24900.0)
            )
            "estandar" -> listOf(
                SimulacionEnvioRequest(serviceType = "ESTANDAR", status = "CREADO", zone = "CENTRO", weightKg = 3.2, packagesCount = 1, price = 4500.0)
            )
            else -> listOf(
                SimulacionEnvioRequest(serviceType = "EXPRESS", status = "ACEPTADO", zone = "NORTE", weightKg = 15.0, packagesCount = 3, price = 19500.0),
                SimulacionEnvioRequest(serviceType = "ESTANDAR", status = "EN_TRANSITO", zone = "CENTRO", weightKg = 4.0, packagesCount = 1, price = 5200.0),
                SimulacionEnvioRequest(serviceType = "MISMO_DIA", status = "ENTREGADO", zone = "SUR", weightKg = 8.5, packagesCount = 2, price = 16800.0),
                SimulacionEnvioRequest(serviceType = "ESTANDAR", status = "CREADO", zone = "ORIENTE", weightKg = 2.0, packagesCount = 1, price = 3900.0),
                SimulacionEnvioRequest(serviceType = "EXPRESS", status = "CANCELADO", zone = "NORTE", weightKg = 22.0, packagesCount = 5, price = 31000.0)
            )
        }
        return lotes.map { simularEnvio(it) }
    }

    internal fun parsearYEnriquecer(key: String?, rawValue: String): EventoEnvioStream {
        return try {
            val node = mapper.readTree(rawValue)
            val shipmentId = node.path("shipmentId").asText("").ifBlank { key ?: "envio-${UUID.randomUUID().toString().take(6)}" }
            val serviceType = node.path("serviceType").asText("ESTANDAR").ifBlank { "ESTANDAR" }.uppercase()
            val status = node.path("status").asText("CREADO").ifBlank { "CREADO" }.uppercase()
            val zone = node.path("zone").asText("CENTRO").ifBlank { "CENTRO" }.uppercase()
            val weightKg = node.path("weightKg").asDouble(0.0).let { if (it <= 0.0) estimarPeso(serviceType) else redondear(it) }
            val packagesCount = node.path("packagesCount").asInt(1).coerceAtLeast(1)
            val price = node.path("price").asDouble(0.0).let { if (it <= 0.0) estimarPrecio(serviceType, weightKg, packagesCount) else redondear(it) }
            val timestamp = node.path("timestamp").asText("").ifBlank { Instant.now().toString() }
            val trackingCode = node.path("trackingCode").asText("").ifBlank { "RX-${shipmentId.takeLast(6).uppercase()}" }
            val eventId = node.path("eventId").asText("").ifBlank { UUID.randomUUID().toString() }

            val categoria = if (
                serviceType == "EXPRESS" ||
                serviceType == "MISMO_DIA" ||
                weightKg >= 10.0 ||
                price >= 15000.0
            ) "ALTA_PRIORIDAD" else "ESTANDAR"

            EventoEnvioStream(
                eventId = eventId,
                shipmentId = shipmentId,
                trackingCode = trackingCode,
                serviceType = serviceType,
                status = status,
                zone = zone,
                weightKg = weightKg,
                packagesCount = packagesCount,
                price = price,
                timestamp = timestamp,
                priorityCategory = categoria
            )
        } catch (_: Exception) {
            val id = key?.takeIf { it.isNotBlank() } ?: "envio-raw"
            EventoEnvioStream(shipmentId = id, status = "CREADO", serviceType = "ESTANDAR", zone = "CENTRO")
        }
    }

    private fun leerEvento(json: String): EventoEnvioStream? = try {
        mapper.readValue(json, EventoEnvioStream::class.java)
    } catch (_: Exception) {
        null
    }

    private fun esPrioritarioOAlerta(ev: EventoEnvioStream): Boolean =
        ev.priorityCategory == "ALTA_PRIORIDAD" || ev.status == "CANCELADO" || ev.status == "RECHAZADA"

    private fun registrarEnMemoria(ev: EventoEnvioStream) {
        totalProcesados.incrementAndGet()
        conteoServicioFallback.merge(ev.serviceType, 1L, Long::plus)
        conteoEstadoFallback.merge(ev.status, 1L, Long::plus)
        val nuevoZona = AcumuladoZonaState(
            zone = ev.zone,
            totalShipments = 1L,
            totalPackages = ev.packagesCount,
            totalWeightKg = ev.weightKg,
            totalRevenue = ev.price,
            lastStatus = ev.status,
            updatedAt = ev.timestamp
        )
        acumuladoZonaFallback.merge(ev.zone, nuevoZona, ::combinarZona)
    }

    private fun combinarZona(actual: AcumuladoZonaState, nuevo: AcumuladoZonaState) = AcumuladoZonaState(
        zone = actual.zone,
        totalShipments = actual.totalShipments + nuevo.totalShipments,
        totalPackages = actual.totalPackages + nuevo.totalPackages,
        totalWeightKg = redondear(actual.totalWeightKg + nuevo.totalWeightKg),
        totalRevenue = redondear(actual.totalRevenue + nuevo.totalRevenue),
        lastStatus = nuevo.lastStatus,
        updatedAt = nuevo.updatedAt
    )

    private fun registrarAlertaFiltrada(ev: EventoEnvioStream) {
        alertasFiltradas.addFirst(ev)
        while (alertasFiltradas.size > 30) {
            alertasFiltradas.removeLast()
        }
    }

    private fun asegurarTopicosStreams() {
        val admin = adminClient ?: return
        val requeridos = listOf(
            INPUT_TOPIC,
            TOPIC_ALERTAS_FILTRADAS,
            TOPIC_RAMA_ESTANDAR,
            TOPIC_RAMA_PRIORITARIA,
            TOPIC_CONTEO_SERVICIO,
            TOPIC_CONTEO_ESTADO,
            TOPIC_ACUMULADO_ZONA
        )
        val existentes = admin.listTopics().names().get(10, TimeUnit.SECONDS)
        val faltantes = requeridos.filter { it !in existentes }.map { NewTopic(it, 3, 1.toShort()) }
        if (faltantes.isNotEmpty()) {
            admin.createTopics(faltantes).all().get(10, TimeUnit.SECONDS)
        }
    }

    private fun estimarPeso(serviceType: String): Double = when (serviceType) {
        "EXPRESS" -> 12.0
        "MISMO_DIA" -> 8.0
        else -> 3.5
    }

    private fun estimarPrecio(serviceType: String, weightKg: Double, packagesCount: Int): Double {
        val base = when (serviceType) {
            "EXPRESS" -> 9500.0
            "MISMO_DIA" -> 11000.0
            else -> 3500.0
        }
        return redondear(base + (weightKg * 450.0) + (packagesCount * 800.0))
    }

    private fun redondear(valor: Double): Double = round(valor * 100.0) / 100.0
}
