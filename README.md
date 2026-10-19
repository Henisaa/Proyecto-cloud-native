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
Si Docker Desktop está instalado por usuario en Windows y docker no aparece en PATH, agregue su carpeta de herramientas a la sesión de PowerShell:
$env:PATH="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker version
docker compose version
Que docker no aparezca en PATH no significa que Docker Desktop no esté instalado; compruebe también la conexión con el motor.
Desde la raíz siga infra/README.md para crear la red y levantar brokers. Arranque primero mq-admin y kafka-admin y ejecute POST topology en ambos. Después arranque catalog, notify, audit, report, shipments y bff usando los comandos de sus README. Por último siga frontend/README.md.
El perfil local de los servicios con base usa H2 en memoria. Para persistencia Oracle use el perfil prod y los esquemas configurados. Las pruebas unitarias y HTTP no necesitan Docker; los tests de integración de notify se activan explícitamente con RUN_DOCKER_TESTS=true.

Seguridad
Flujo: access token Azure AD -> AWS API Gateway -> BFF -> servicios internos. Cada servicio REST verifica JWT y roles; el BFF conserva Bearer. Admin administra y ve KPIs; Despachador opera envíos; Cliente crea y consulta; Auditor lee timeline. Las APIs administrativas son exclusivas de Admin. SECURITY_ENABLED=false es solo para desarrollo. No abra directamente las APIs internas en Internet.

Mensajería
RabbitMQ usa `ex.rutaexpress.cmd` (direct) para `capacity.reserve` y `capacity.release`, y `ex.rutaexpress.events` (topic) para auditoría (`shipment.#`, `catalog.#`) y notificaciones (`notification.#`). Las colas `q.rutaexpress.command`, `q.rutaexpress.notification` y `q.rutaexpress.audit` tienen una DLQ propia enlazada mediante `ex.rutaexpress.dlx`. Los consumidores usan ACK manual; los errores no recuperables y los que agotan sus reintentos se envían a DLQ sin requeue. Notify usa idempotencia Redis y shipments espera confirmación del broker antes de responder. Las políticas `rutaexpress.trabajo` y `rutaexpress.dlq` aplican TTL y límites administrados por mq-admin.
Kafka: shipments.events y catalog.events con delete y siete días; audit.timeline con compact,delete y treinta días. Los consumidores fallidos van a DLT independiente después de tres intentos. Los tópicos se crean con tres particiones y tres réplicas. Auditoría deduplica eventId y conserva identidad y timestamp originales.

Validación y estado de la revisión (19 de octubre de 2026)
Estado de compilación y pruebas: Todos los módulos compilan y pasan sus pruebas al 100% en local sobre JDK 22 y Node 22 (104 pruebas unitarias backend y 3 frontend ejecutadas y aprobadas; 3 pruebas de integración en notify quedan omitidas sin Docker activo).

Correcciones aplicadas en esta iteración:
1. ms-rutaexpress-bff: corregidos errores de inferencia en arrayOf<Any> para los endpoints del proxy de broker admin, corregido import en BrokerAdminSecurityTests y agregada la dependencia org.mockito.kotlin:mockito-kotlin:6.2.2 en build.gradle.kts.
2. ms-rutaexpress-shipments: especificado tipo explícito any<Envio>() en EnvioServicioTests para evitar fallos de compilación en pruebas. Ampliado el tamaño de la columna event_id a 100 caracteres en RegistroAuditoriaEnvio y en la migración Flyway V2__capacidad_y_auditoria_rabbit.sql, previniendo desbordamientos en eventos de compensación derivados (clave de más de 64 caracteres).
3. infra/apps/oracle-init.sql: añadido ALTER SESSION SET CONTAINER = FREEPDB1; para que los esquemas de aplicación se inicialicen correctamente en la PDB por defecto de Oracle 23c Free al arrancar contenedores limpios.

Problemas detectados y advertencias pendientes:
1. Docker CLI local: si 'docker' no se reconoce en PowerShell, debe agregarse al PATH la ruta de instalación de Docker Desktop o ejecutarse en un entorno con Docker disponible para levantar los clústeres reales de RabbitMQ y Oracle.
2. Separación de repositorios: para la entrega de la EP2 se requiere disponer de repositorios GitHub individuales para frontend, BFF, principal (shipments), catálogo y administrador RabbitMQ. Actualmente se gestionan como ramas en este monorepo.
3. Advertencias CSS frontend: al compilar el frontend con Angular 19 y Tailwind v4 se emiten 11 advertencias no bloqueantes por subselectores vacíos ('&').
4. Despliegue externo: si se emplea el script deploy-aws.bat (fuera del repositorio), verificar que utilice los nombres de host 'rabbit1' y 'rabbit2'.

Ramas y documentación
main integra el proyecto completo. Ramas de servicios: ms_catalog, ms_notify, ms-rutaexpress-audit, ms-rutaexpress-report, ms-rutaexpress-bff, ms-rutaexpress-shipments, ms-rutaexpress-mq-admin y ms-rutaexpress-kafka-admin. frontend corresponde al frontend Angular actual.
docs contiene material de referencia local y está excluido de nuevos commits. Los archivos ya versionados de docs no se eliminan ni se actualizan en este trabajo. Cada servicio y frontend tienen README en texto plano con responsabilidades, configuración, comandos y limitaciones.
