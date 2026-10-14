ms-rutaexpress-bff
Backend para frontend y acceso seguro

Funcionamiento
No persiste datos. Recibe el access token de Angular/MSAL después de AWS API Gateway, valida JWT y permisos, y reenvía Bearer a los servicios del dominio. Devuelve códigos de error de los downstream, o 503 si no están disponibles. Expone perfil, dashboard, catálogo, KPIs, timeline y envíos. La sección administrativa permite consultas de RabbitMQ y Kafka exclusivamente para Admin; no hace proxy arbitrario de URLs ni expone operaciones destructivas.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Arranque los demás servicios en los puertos 8081 a 8087. Las URLs pueden cambiarse con CATALOG_SERVICE_URL, NOTIFY_SERVICE_URL, AUDIT_SERVICE_URL, REPORT_SERVICE_URL, SHIPMENTS_SERVICE_URL, MQ_ADMIN_SERVICE_URL y KAFKA_ADMIN_SERVICE_URL.

cd ms-rutaexpress-bff
$env:SECURITY_ENABLED="false"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8080. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
GET /api/bff/auth/me
GET /api/bff/dashboard
GET /api/bff/status
GET /api/bff/catalog/services
GET /api/bff/report/kpis
GET /api/bff/audit/timeline/{idEntidad}
GET, POST /api/bff/shipments
GET /api/bff/shipments/{id}
PUT /api/bff/shipments/{id}/status
GET /api/bff/admin/mq/cluster
GET /api/bff/admin/mq/queues
GET /api/bff/admin/mq/alerts
GET /api/bff/admin/kafka/cluster
GET /api/bff/admin/kafka/topics
GET /api/bff/admin/kafka/topics/{name}
GET /api/bff/admin/kafka/groups/{group}/lag

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
