ms-rutaexpress-kafka-admin
Administración de Kafka

Funcionamiento
Sin base de datos. Utiliza Kafka AdminClient para consultar brokers, crear tópicos, ampliar particiones, cambiar retención y medir lag de los grupos de auditoría y reportes. Solo Admin accede a la API. No borra tópicos ni reduce particiones. Las consultas tienen timeout limitado.
POST topology crea los tópicos faltantes sin reemplazar los existentes: shipments.events, catalog.events, audit.timeline, shipments.events.audit.DLT, audit.timeline.audit.DLT y shipments.events.report.DLT. Todos tienen tres particiones y tres réplicas por defecto. shipments.events/catalog.events: delete, siete días; audit.timeline: compact,delete, treinta días; DLT: delete, catorce días. min.insync.replicas=2 para tres réplicas. Los cambios de retención sobre tópicos existentes se hacen explícitamente mediante PUT.
El clúster del caso usa Kafka 3.9.2 y tres ZooKeeper; kafka-clients está gestionado por Spring Boot. Kafka 4 eliminó ZooKeeper. No cambie la imagen a Kafka 4 manteniendo esta infraestructura.

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
GET /api/kafka-admin/groups/ms-rutaexpress-audit-group/lag
GET /api/kafka-admin/groups/ms-rutaexpress-report-group/lag
Creación: {"name":"shipments.events","partitions":3,"replicas":3,"retentionMs":604800000,"cleanupPolicy":"delete"}
Particiones: {"partitions":6}
Retención: {"retentionMs":1209600000,"cleanupPolicy":"delete"}

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
