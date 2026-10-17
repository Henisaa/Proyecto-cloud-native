ms-rutaexpress-catalog
Servicios, tarifas y capacidad de flota

Funcionamiento
Persiste servicios, tarifas y capacidad en Oracle con JPA y Flyway. Permite consultar y administrar servicios y tarifas, y verificar disponibilidad. Usa caché Caffeine y publica cambios en Kafka `catalog.events`. Las operaciones de administración requieren Admin; las consultas permiten los roles del dominio indicados por cada endpoint.

RabbitMQ
Consume comandos desde `q.rutaexpress.command`, enlazada a `ex.rutaexpress.cmd` con `capacity.reserve` y `capacity.release`. `CapacidadComandoProcesador` bloquea la línea de capacidad con `PESSIMISTIC_WRITE`, registra cada commandId en `tb_capacity_commands` (migración Flyway V2) y evita repetir reservas de un envío ya reservado. Publica `catalog.capacity.reserved`, `catalog.capacity.rejected` o `catalog.capacity.released` en `ex.rutaexpress.events`. Los rechazos no recuperables y los errores que agotan los reintentos van a `q.rutaexpress.command.dlq` sin requeue; `DeadLetterRegistro` los confirma y deja constancia en logs. `RABBIT_PREFETCH` define el QoS, `RABBIT_RETRY_MAX_ATTEMPTS` limita los reintentos, `RABBIT_RETRY_INITIAL_MS` define su espera inicial y `RABBIT_DLQ_CONSUMER_ENABLED=false` permite inspeccionar mensajes en Management UI.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. El perfil local usa H2 en memoria y migraciones. La publicación Kafka puede desactivarse con APP_KAFKA_ENABLED=false y SPRING_KAFKA_ENABLED=false.

cd ms-rutaexpress-catalog
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
$env:APP_KAFKA_ENABLED="true"
$env:SPRING_KAFKA_ENABLED="true"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8081. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
GET, POST /api/catalog/services
GET, PUT, DELETE /api/catalog/services/{id}
PATCH /api/catalog/services/{id}/status
GET, POST /api/catalog/services/{id}/capacities
PUT /api/catalog/services/{id}/capacities/{capacityId}
GET, POST /api/catalog/services/{id}/tariffs
PUT /api/catalog/services/{id}/tariffs/{tariffId}
POST /api/catalog/services/{id}/capacity/check
POST /api/catalog/services/{id}/capacity/reserve
POST /api/catalog/services/{id}/capacity/release
Estas operaciones aceptan la zona, el tipo de vehículo y la cantidad de paquetes; check recibe peso y volumen.

Oracle
Para Oracle defina SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME y SPRING_DATASOURCE_PASSWORD antes de bootRun. Use un esquema separado por servicio con permisos de tablas y secuencias. Flyway ejecuta src/main/resources/db/migration. El perfil local H2 es solo para desarrollo.

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
