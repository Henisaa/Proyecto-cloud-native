ms-rutaexpress-shipments
Gestión de envíos y estados

Funcionamiento
Persiste envíos en Oracle. La máquina de estados permite CREADO, ACEPTADO, EN_BODEGA, EN_RUTA, ENTREGADO y CANCELADO, respetando sus transiciones. Aceptar reserva capacidad mediante catalog; cancelar la libera. Publica shipments.events y audit.timeline, con eventId compartido, identidad del usuario y fecha original. El timeline se publica con eventId como clave para conservar cada evento bajo compactación. Los comandos email.send, warehouse.ticket y label.gen usan RabbitMQ, JSON persistente y confirmación del broker. El destinatario recibe emails y la bodega recibe PDFs mediante notify.
La integración con brokers y capacidad no es una transacción distribuida ni un outbox; una caída entre la escritura de Oracle y el envío requiere reconciliación. No se promete entrega exactamente una vez.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Arranque catalog en 8081 y notify en 8082. El perfil local usa H2 en memoria. Para pruebas aisladas sin brokers, APP_KAFKA_ENABLED=false, APP_RABBIT_ENABLED=false y APP_CATALOGO_ENABLED=false.

cd ms-rutaexpress-shipments
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
$env:RABBITMQ_USERNAME="rutaexpress"
$env:RABBITMQ_PASSWORD="rutaexpress-local"
$env:KAFKA_ENABLED="true"
$env:RABBIT_ENABLED="true"
$env:CATALOGO_ENABLED="true"
$env:APP_KAFKA_ENABLED="true"
$env:APP_RABBIT_ENABLED="true"
$env:APP_CATALOGO_ENABLED="true"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8085. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
POST /api/shipments
GET /api/shipments
GET /api/shipments/{id}
PUT /api/shipments/{id}/status
GET /api/shipments?status=ACEPTADO&from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z
Cambiar estado utiliza JSON: {"status":"ACEPTADO"}. Crear utiliza serviceId, zone, packagesCount, weightKg, volumeM3, recipientName, recipientEmail, originAddress y destinationAddress.

Oracle
Para Oracle defina SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME y SPRING_DATASOURCE_PASSWORD antes de bootRun. Use un esquema separado por servicio con permisos de tablas y secuencias. Flyway ejecuta src/main/resources/db/migration. El perfil local H2 es solo para desarrollo.

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
