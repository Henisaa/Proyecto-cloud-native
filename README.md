RutaExpress
Plataforma de envíos de última milla del Caso 3.

Estructura y función
frontend: Angular 19 con MSAL; login Azure AD, envíos, catálogo, reportes, auditoría y monitoreo de mensajería para Admin.
ms-rutaexpress-bff: entrada segura y agregación, puerto 8080.
ms-rutaexpress-catalog: servicios, tarifas y capacidad de flota, puerto 8081.
ms-rutaexpress-notify: email, push y PDF por RabbitMQ, puerto 8082.
ms-rutaexpress-audit: timeline inmutable e idempotente desde Kafka, puerto 8083.
ms-rutaexpress-report: proyecciones de envíos y KPIs desde Kafka, puerto 8084.
ms-rutaexpress-shipments: envíos, estados y coordinación del despacho, puerto 8085.
ms-rutaexpress-mq-admin: topología, retención, DLQ y estado RabbitMQ, puerto 8086.
ms-rutaexpress-kafka-admin: tópicos, particiones, retención y lag Kafka, puerto 8087.
infra/apps, infra/mq e infra/kafka: tres stacks Docker Compose del caso.

Tecnologías
Kotlin 2.3.21 sobre JVM 21, Spring Boot 4.1.1, Gradle Wrapper 9.7.1, Jackson 3, Oracle/JPA/Flyway, RabbitMQ 4, Kafka 3.9.2 con ZooKeeper 3.9, Redis, SMTP/Mailpit, OpenPDF y Firebase opcional. Se conserva Kotlin, que es el lenguaje del repositorio; la pauta general menciona Java. Frontend Angular con TypeScript y MSAL.

Arranque local
Instale JDK 21 o superior, Node compatible con Angular 19 y Docker con Compose v2.
Desde la raíz siga infra/README.md para crear la red y levantar brokers. Arranque primero mq-admin y kafka-admin y ejecute POST topology en ambos. Después arranque catalog, notify, audit, report, shipments y bff usando los comandos de sus README. Por último siga frontend/README.md.
El perfil local de los servicios con base usa H2 en memoria. Para persistencia Oracle use el perfil prod y los esquemas configurados. Las pruebas unitarias y HTTP no necesitan Docker; los tests de integración de notify se activan explícitamente con RUN_DOCKER_TESTS=true.

Seguridad
Flujo: access token Azure AD -> AWS API Gateway -> BFF -> servicios internos. Cada servicio REST verifica JWT y roles; el BFF conserva Bearer. Admin administra y ve KPIs; Despachador opera envíos; Cliente crea y consulta; Auditor lee timeline. Las APIs administrativas son exclusivas de Admin. SECURITY_ENABLED=false es solo para desarrollo. No abra directamente las APIs internas en Internet.

Mensajería
RabbitMQ: cmd.direct, cmd.topic y cmd.dead.dlx; tres colas de comandos y tres DLQ. ACK/NACK manual en notify, idempotencia Redis, reintentos y mensajes persistentes con publisher confirms en shipments. Las políticas de TTL y límite se administran desde mq-admin.
Kafka: shipments.events y catalog.events con delete y siete días; audit.timeline con compact,delete y treinta días. Los consumidores fallidos van a DLT independiente después de tres intentos. Los tópicos se crean con tres particiones y tres réplicas. Auditoría deduplica eventId y conserva identidad y timestamp originales.

Validación y limpieza
En cada servicio ejecute .\gradlew.bat test bootJar. En frontend ejecute npm ci y npm run build. Para pruebas Docker de notify: $env:RUN_DOCKER_TESTS="true"; .\gradlew.bat test.
Al terminar, use gradlew clean, detenga los daemons y elimine solamente build, .gradle, .kotlin, node_modules, dist y .angular creados durante la validación. La limpieza de contenedores, volúmenes e imágenes se explica en infra/README.md. No use docker system prune sobre recursos ajenos.

Ramas y documentación
main integra el proyecto completo. Ramas de servicios: ms_catalog, ms_notify, ms-rutaexpress-audit, ms-rutaexpress-report, ms-rutaexpress-bff, ms-rutaexpress-shipments, ms-rutaexpress-mq-admin y ms-rutaexpress-kafka-admin. frontend corresponde al frontend Angular actual.
docs contiene material de referencia local y está excluido de nuevos commits. Los archivos ya versionados de docs no se eliminan ni se actualizan en este trabajo. Cada servicio y frontend tienen README en texto plano con responsabilidades, configuración, comandos y limitaciones.
