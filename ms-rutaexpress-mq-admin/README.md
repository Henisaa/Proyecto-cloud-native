ms-rutaexpress-mq-admin
Administración de RabbitMQ

Funcionamiento
Sin base de datos. Spring AMQP declara tres exchanges durables y seis colas compatibles con notify: cmd.direct, cmd.topic, cmd.dead.dlx; q.cmd.email, q.cmd.warehouse, q.cmd.label y sus .dlq. Cada cola principal tiene una routing key DLX propia y bindings direct y topic. Consulta la API Management para estado del clúster, mensajes pendientes, sin ACK y consumidores. Solo Admin puede utilizar su API.
POST topology reconcilia declaraciones y aplica retención mediante políticas actualizables, sin borrar colas. TTL por defecto: comandos 24 horas y DLQ siete días, máximo 10000 mensajes por cola y overflow reject-publish. Se rechaza TTL fuera de 1 segundo a 30 días y límites fuera de 1 a 1000000. GET alerts muestra DLQ con mensajes según MQ_ALERT_THRESHOLD, por defecto 1. rutaexpress.mq.dlq.depth refleja el último muestreo de las colas. Un sondeo cada 30 segundos actualiza la métrica y registra alertas DLQ; APP_MQ_POLL_INTERVAL_MS permite cambiar ese intervalo. Cambiar retención puede expirar mensajes existentes. No existe un endpoint para purgar o borrar colas.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Usa localhost:5672,localhost:5673 y Management http://localhost:15672. Configure RABBITMQ_ADDRESSES, RABBITMQ_MANAGEMENT_URL, RABBITMQ_USERNAME, RABBITMQ_PASSWORD y RABBITMQ_VHOST si cambian. Después del arranque: Invoke-RestMethod -Method Post -Uri http://localhost:8086/api/mq-admin/topology

cd ms-rutaexpress-mq-admin
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8086. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
GET /api/mq-admin/cluster
GET /api/mq-admin/queues
GET /api/mq-admin/alerts
POST /api/mq-admin/topology
PUT /api/mq-admin/retention
Cuerpo de retención: {"commandTtlMs":86400000,"dlqTtlMs":604800000,"maxLength":10000}

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
