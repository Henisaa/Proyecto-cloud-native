ms-rutaexpress-mq-admin
Administración de RabbitMQ

Funcionamiento
Sin base de datos. Administra el clúster existente y declara la topología RutaExpress: `ex.rutaexpress.cmd` (direct), `ex.rutaexpress.events` (topic) y `ex.rutaexpress.dlx` (direct). Las colas `q.rutaexpress.command`, `q.rutaexpress.notification` y `q.rutaexpress.audit` tienen cada una su DLQ y bindings por routing key. Consulta RabbitMQ Management para el estado de los nodos, colas, mensajes pendientes, sin ACK y consumidores. Solo `ROLE_ADMIN` puede utilizar la API.

`POST /topology` reconcilia la topología fija y aplica retención mediante políticas actualizables. El borrado de colas y exchanges protegidos requiere `force=true`; una cola o exchange en uso puede seguir devolviendo 409. La política predeterminada da 24 horas a las colas de trabajo y siete días a las DLQ, con máximo de 10000 mensajes. Se rechaza TTL fuera de 1 segundo a 30 días y límites fuera de 1 a 1000000. `GET /alerts` muestra DLQ con mensajes según `MQ_ALERT_THRESHOLD` (por defecto 1). `rutaexpress.mq.dlq.depth` refleja el último muestreo; `MQ_POLL_INTERVAL_MS` permite cambiar el intervalo de sondeo. Cambiar retención puede expirar mensajes existentes.

Requisitos
JDK 21 o superior, JAVA_HOME configurado y acceso a Maven Central. El bytecode generado es Java 21. Se usa Kotlin 2.3.21, Spring Boot 4.1.1, Jackson 3 y Gradle 9.7.1 mediante el wrapper incluido. No se necesita Gradle instalado. En Linux/macOS sustituya .\gradlew.bat por ./gradlew y defina las variables con export.

Verificación y limpieza
.\gradlew.bat test bootJar
.\gradlew.bat clean
.\gradlew.bat --stop
Remove-Item -LiteralPath .gradle,.kotlin -Recurse -Force -ErrorAction SilentlyContinue
No elimine gradle/wrapper: contiene los archivos versionados necesarios para compilar.

Ejecución local desde la raíz del repositorio
Antes de arrancar, levante la infraestructura según infra/README.md. Usa localhost:5672,localhost:5673 y Management http://localhost:15672. Configure `RABBITMQ_ADDRESSES`, `RABBITMQ_MANAGEMENT_URLS`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` y `RABBITMQ_VHOST` si cambian. `RABBITMQ_MANAGEMENT_URLS` es una lista separada por comas; mq-admin prueba los nodos en orden. Después del arranque: `Invoke-RestMethod -Method Post -Uri http://localhost:8086/api/mq-admin/topology`.

cd ms-rutaexpress-mq-admin
$env:SECURITY_ENABLED="false"
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun

La aplicación escucha en http://localhost:8086. Salud: /actuator/health. Swagger: /swagger-ui/index.html. Métricas: /actuator/prometheus.

Endpoints
| Método | Ruta | Cuerpo o parámetros | Respuestas principales |
|---|---|---|---|
| GET | `/api/mq-admin/cluster`, `/queues`, `/alerts`, `/metrics`, `/exchanges`, `/bindings` | — | 200, 503 |
| POST | `/api/mq-admin/topology` | — | 200, 503 |
| PUT | `/api/mq-admin/retention` | `{"commandTtlMs":86400000,"dlqTtlMs":604800000,"maxLength":10000}` | 200, 400, 503 |
| POST | `/api/mq-admin/queues` | `{"name":"q.demo","durable":true,"ttlMs":86400000,"maxLength":10000,"overflow":"reject-publish","deadLetterExchange":"ex.rutaexpress.dlx","deadLetterRoutingKey":"demo"}` | 201, 400, 409, 503 |
| DELETE | `/api/mq-admin/queues/{name}?force=false` | `force=true` para quitar una protección | 200, 404, 409, 503 |
| POST | `/api/mq-admin/exchanges` | `{"name":"ex.demo","type":"topic","durable":true}` | 201, 400, 409, 503 |
| DELETE | `/api/mq-admin/exchanges/{name}?force=false` | `force=true` para quitar una protección | 200, 404, 409, 503 |
| POST | `/api/mq-admin/bindings` | `{"source":"ex.demo","destination":"q.demo","destinationType":"queue","routingKey":"demo.#"}` | 201, 400, 404, 409, 503 |
| DELETE | `/api/mq-admin/bindings?source=ex.demo&destination=q.demo&destinationType=queue&routingKey=demo.%23&force=false` | Los parámetros identifican el binding | 200, 404, 409, 503 |
| POST | `/api/mq-admin/exchanges/{name}/messages` | `{"routingKey":"notification.email.send","body":"{\"type\":\"demo\"}"}` | 200, 400, 404, 503 |

Los nombres vacíos, tipos o parámetros inválidos responden 400 con `ProblemDetail`. Los recursos inexistentes responden 404; duplicados, recursos protegidos o conflictos del broker responden 409; la falta de conexión responde 503.

Seguridad
En despliegue seguro, SECURITY_ENABLED=true. Configure AZURE_AD_ISSUER_URI con el tenant corporativo y AZURE_AD_AUDIENCE con el identificador de la API. AZURE_AD_CLIENT_ID es el GUID de la API cuando se utiliza el validador automático de Spring. Se valida firma, issuer, audiencia y vigencia. Roles reconocidos: Admin, Despachador, Cliente y Auditor. Operador del dominio y Cliente del dominio se normalizan a Despachador y Cliente.
La desactivación de seguridad se utiliza solamente en desarrollo local. No despliegue ese modo detrás de un Gateway público.
