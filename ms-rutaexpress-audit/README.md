ms-rutaexpress-audit
Auditoría y timeline de eventos

Funcionamiento
Consume shipments.events y audit.timeline con el grupo ms-rutaexpress-audit-group. Guarda una sola vez cada eventId, incluso cuando llega por ambos tópicos. Conserva actor, entidad, tipo de evento, origen, payload y timestamp. Oracle aplica una restricción única para eventId mediante la migración V2. Ofrece únicamente lecturas para Admin y Auditor. Los errores se propagan al manejador Kafka: tres intentos totales y luego <topic>.audit.DLT, conservando partición y metadatos de error. La publicación DLT debe confirmarse antes de dar por recuperado el mensaje.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. El perfil local usa H2 en memoria y no conserva datos tras reiniciar. Kafka debe estar disponible y los tópicos creados. KAFKA_LISTENERS_ENABLED=false permite arrancar solo HTTP sin consumir eventos.

cd ms-rutaexpress-audit
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8083. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
GET /api/audit
GET /api/audit/entidad/{idEntidad}
GET /api/audit/tipo/{tipoEntidad}

Oracle
Para Oracle defina SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME y SPRING_DATASOURCE_PASSWORD antes de bootRun. Use un esquema separado por servicio con permisos de tablas y secuencias. Flyway ejecuta src/main/resources/db/migration. El perfil local H2 es solo para desarrollo.

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
