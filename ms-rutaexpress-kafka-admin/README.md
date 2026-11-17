ms-rutaexpress-kafka-admin
Administración de Kafka

Funcionamiento
Sin base de datos. Utiliza Kafka AdminClient para consultar brokers, crear tópicos, ampliar particiones, cambiar retención y medir lag de los grupos de auditoría, reportes, tutorial educativo, procesamiento en streaming y análisis lento. La administración de tópicos está reservada para el rol Admin, mientras que las rutas de demostración y simulación (/api/kafka/* y /api/test/*) operan sin autenticación en entornos locales o protegidas a través del BFF. No borra tópicos ni reduce particiones y las consultas tienen timeout limitado.

POST /api/kafka-admin/topology crea los tópicos base faltantes sin reemplazar los existentes: tutorial-particiones, shipments.events, catalog.events, audit.timeline, shipments.events.audit.DLT, audit.timeline.audit.DLT y shipments.events.report.DLT (tres particiones y tres réplicas por defecto, min.insync.replicas=2). Adicionalmente, POST /api/kafka/tuning/init inicializa los tópicos de tuning y manejo de errores: sensor-topic (3 particiones, retención de 5 minutos), ordenes-creadas (3 particiones) y ordenes-creadas.DLT (3 particiones). Los cambios de retención sobre tópicos existentes se realizan explícitamente mediante PUT y admiten desde 60000 ms (1 minuto).

Productor, particiones, Kafka Streams, tuning y manejo de errores
- Particiones y offsets: productor REST (/api/kafka/send) sobre tutorial-particiones usando TutorialPartitioner (Round Robin sin clave y Sticky Partitioning por hash cuando se entrega key) y consumidor KafkaTutorialConsumer en el grupo mi-grupo-educativo.
- Kafka Streams: topología en tiempo real sobre shipments.events (aplicación rutaexpress-streams-processor) con operaciones stateless (map, filter, branch hacia rama-estandar y rama-prioritaria), agregaciones stateful (count y reduce sobre store-conteo-por-servicio, store-conteo-por-estado y store-acumulado-por-zona) y ventanas temporales (Tumbling de 5 minutos y Hopping de 10 minutos con avance de 5 minutos), consultables mediante Interactive Queries.
- Tuning de productores y pruebas de carga: productores configurados con `batch.size=32768` (32 KB), `linger.ms=20`, `compression.type=snappy`, `buffer.memory=67108864` (64 MB) y `enable.idempotence=true`, junto a brokers con retención por defecto de 5 minutos o 50 MB, `num.network.threads=8` y `num.io.threads=8`. Incluye `LoadTestController` (`/api/test/health`, `/api/test/start`, `/api/test/sustained`, `/api/test/status`) para ráfagas paralelas o carga sostenida sobre `sensor-topic`, y `SlowConsumer` en el grupo `analytics-group` (`concurrency=3`, `max-poll-records=500`, retardo simulado de 10 ms por mensaje) para observar Consumer Lag.
- Manejo de errores en streaming: uso de ErrorHandlingDeserializer, validación JSR-380 registrada en contenedores Kafka, DefaultErrorHandler con ExponentialBackOff (1 s, 2 s, 4 s) y exclusión de excepciones no recuperables (Poison Pills), DeadLetterPublishingRecoverer hacia .DLT con headers enriquecidos y herramienta administrativa de reprocesamiento, control de idempotencia Check-then-Act sobre ordenes-creadas (error-handling-group) y trazabilidad con X-Correlation-Id mediante CorrelationIdProducerInterceptor y SLF4J MDC.

El clúster del caso usa Kafka 3.9.2 y tres ZooKeeper; kafka-clients está gestionado por Spring Boot. Kafka 4 eliminó ZooKeeper, por lo que no se debe cambiar la imagen a Kafka 4 manteniendo esta infraestructura.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Usa localhost:9092,localhost:9093,localhost:9094. Después del arranque: Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/kafka-admin/topology. KAFKA_REPLICATION_FACTOR=1 solo sirve para una prueba con broker único; el caso requiere tres réplicas.

cd ms-rutaexpress-kafka-admin
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8087. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
GET /api/kafka-admin/cluster
GET /api/kafka-admin/topics
GET /api/kafka-admin/topics/{name}
POST /api/kafka-admin/topics
POST /api/kafka-admin/topology
PUT /api/kafka-admin/topics/{name}/partitions
PUT /api/kafka-admin/topics/{name}/retention
GET /api/kafka-admin/groups/{group}/lag (grupos permitidos: ms-rutaexpress-audit-group, ms-rutaexpress-report-group, mi-grupo-educativo, rutaexpress-streams-processor, analytics-group, error-handling-group)
GET /api/kafka/health
GET /api/kafka/send?message=...&key=...
POST /api/kafka/send
GET /api/kafka/messages
GET /api/kafka/streams/state
GET /api/kafka/streams/simulate?scenario=rafaga
POST /api/kafka/streams/simulate
POST /api/kafka/tuning/init
GET /api/test/health
GET /api/test/start?count=10000&topic=sensor-topic
POST /api/test/start?count=10000&topic=sensor-topic
GET /api/test/sustained?messagesPerSecond=1000&seconds=60
POST /api/test/sustained?messagesPerSecond=1000&seconds=60
GET /api/test/status
GET /api/kafka/errors/state
POST /api/kafka/errors/simulate?scenario=valido
GET /api/kafka/dlt
POST /api/kafka/dlt/reprocess
Creación: {"name":"shipments.events","partitions":3,"replicas":3,"retentionMs":604800000,"cleanupPolicy":"delete"}
Particiones: {"partitions":6}
Retención: {"retentionMs":300000,"cleanupPolicy":"delete"}

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.

## Análisis de Tuning, Trade-offs y Escalabilidad (Actividad 3.3.2)

### Comparativa Configuración Default vs Tuned

| Parámetro / Aspecto | Valor Default | Valor Tuned (RutaExpress) | Efecto Observado |
|---|---|---|---|
| `batch.size` | 16 KB (16384 B) | 32 KB (32768 B) | Menos peticiones de red al agrupar más mensajes; mayor throughput. |
| `linger.ms` | 0 ms | 20 ms | Pequeña latencia individual añadida para llenar mejor cada batch. |
| `compression.type` | `none` | `snappy` | Balance entre uso de CPU y ahorro de ancho de banda en red y disco. |
| `buffer.memory` | 32 MB | 64 MB (67108864 B) | Mayor búfer de acumulación en productor ante ráfagas (100K–500K eventos). |
| `log.retention.minutes` | 10080 min (7 días) | 5 min (`hours=0`, `minutes=5`) | Purga rápida de segmentos antiguos (verificación cada 60000 ms). |
| `log.retention.bytes` | -1 (infinito) | 50 MB (52428800 B) | Protege el disco eliminando datos antiguos cuando el tópico supera 50 MB. |
| `num.network.threads` / `num.io.threads` | 3 / 8 | 8 / 8 | Mayor paralelismo de sockets de red y operaciones de I/O en brokers. |
| `Consumer Lag` (`analytics-group`) | N/A | Alto en ráfagas (100K+) | Productor supera ampliamente a `SlowConsumer` (10 ms/msg, `concurrency=3`, `max.poll.records=500`). |

### Trade-offs y Pruebas de Carga

- **Latencia individual vs Throughput total**: Aumentar `batch.size` y `linger.ms` añade hasta 20 ms de espera por lote, pero reduce drásticamente el número de viajes de red y maximiza el throughput en escenarios de alta concurrencia (IoT / telemetría).
- **Compresión (`snappy` vs `gzip`)**: `snappy` ofrece menor sobrecarga de CPU para ráfagas en tiempo real; `gzip` logra mayor compresión a costa de más CPU.
- **Almacenamiento vs Retención**: Limitar retención por tiempo (5 min) y bytes (50 MB) evita saturar discos bajo carga masiva (`count=500000`), asumiendo que los consumidores procesan los eventos dentro de esa ventana.
- **Escenarios verificables en `/api/test` y la consola**:
  1. **Carga pequeña (`count=1000`)**: Completa en milisegundos con lag mínimo.
  2. **Carga masiva (`count=100000`)**: Respuesta inmediata en productor (fire-and-forget paralelo) y lag observable en `analytics-group` (consola y Kafka UI en `http://localhost:8090`).
  3. **Retención (`count=500000`)**: Tras esperar 5 minutos o superar 50 MB por partición, el broker depura segmentos antiguos automáticamente.

### Soluciones para Reducir el Consumer Lag

1. **Escalamiento horizontal de consumidores y particiones**: Levantar múltiples instancias (o `concurrency=3` sobre `sensor-topic` de 3 particiones) para que cada hilo/instancia procese una partición en paralelo.
2. **Optimización del procesamiento (`SlowConsumer`)**: Reducir bloqueos síncronos mediante procesamiento en lotes (`max.poll.records=500`), caché o ejecución asíncrona.
3. **Procesamiento en tiempo real con Kafka Streams**: Sustituir consumidores bloqueantes por topologías de streaming con State Stores locales cuando se requieren agregaciones o filtrados continuos.

