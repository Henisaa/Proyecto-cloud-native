ms-rutaexpress-kafka-admin
Administración de Kafka

Funcionamiento
Sin base de datos. Utiliza Kafka AdminClient para consultar brokers, crear tópicos, ampliar particiones, cambiar retención y medir lag de los grupos de auditoría, reportes, tutorial educativo, procesamiento en streaming y análisis lento. La administración de tópicos está reservada para el rol Admin, mientras que las rutas de demostración y simulación (/api/kafka/* y /api/test/*) operan sin autenticación en entornos locales o protegidas a través del BFF. No borra tópicos ni reduce particiones y las consultas tienen timeout limitado.

POST /api/kafka-admin/topology crea los tópicos base faltantes sin reemplazar los existentes: tutorial-particiones, shipments.events, catalog.events, audit.timeline, shipments.events.audit.DLT, audit.timeline.audit.DLT y shipments.events.report.DLT (tres particiones y tres réplicas por defecto, min.insync.replicas=2). Adicionalmente, POST /api/kafka/tuning/init inicializa los tópicos de tuning y manejo de errores: sensor-topic (3 particiones, retención de 5 minutos), ordenes-creadas (3 particiones) y ordenes-creadas.DLT (3 particiones). Los cambios de retención sobre tópicos existentes se realizan explícitamente mediante PUT y admiten desde 60000 ms (1 minuto).

Productor, particiones, Kafka Streams, tuning y manejo de errores
- Particiones y offsets: productor REST (/api/kafka/send) sobre tutorial-particiones usando TutorialPartitioner (Round Robin sin clave y Sticky Partitioning por hash cuando se entrega key) y consumidor KafkaTutorialConsumer en el grupo mi-grupo-educativo.
- Kafka Streams: topología en tiempo real sobre shipments.events (aplicación rutaexpress-streams-processor) con operaciones stateless (map, filter, branch hacia rama-estandar y rama-prioritaria), agregaciones stateful (count y reduce sobre store-conteo-por-servicio, store-conteo-por-estado y store-acumulado-por-zona) y ventanas temporales (Tumbling de 5 minutos y Hopping de 10 minutos con avance de 5 minutos), consultables mediante Interactive Queries.
- Tuning de productores y pruebas de carga: productores configurados con batch.size=32768 (32 KB), linger.ms=20, compression.type=snappy y enable.idempotence=true, además de retención por defecto de 5 minutos o 50 MB en brokers Kafka. Incluye LoadTestController (/api/test/start) para envío paralelo sobre sensor-topic y SlowConsumer en el grupo analytics-group con retardo simulado de 10 ms por mensaje para observar Consumer Lag.
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
GET /api/test/start?count=10000
POST /api/test/start?count=10000
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
