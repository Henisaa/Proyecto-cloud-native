# Análisis EP2 RutaExpress: estado, pendientes y plan de trabajo

> **Para el agente de IA que lea esto.** Este documento resume qué exige la EP2, qué estaba mal antes, qué se arregló, qué sigue faltando y **qué archivos de qué servicio hay que tocar**. Las tareas están en la **sección 7**, ordenadas por prioridad, con archivos, pasos y criterio de aceptación. Antes de programar, lee las secciones 0, 7 y 9.
>
> - Última actualización: 2026-10-17.
> - Base de esta revisión: `main` en `32f23d4`, con los cambios de implementación de `19e364a`. La sección 13 registra los hallazgos y los resultados de las pruebas de la revisión independiente.
> - Revisión de ramas (2026-10-17): `main` y las ramas remotas `frontend`, `ms-rutaexpress-bff`, `ms_catalog`, `ms_notify`, `ms-rutaexpress-audit`, `ms-rutaexpress-report`, `ms-rutaexpress-shipments`, `ms-rutaexpress-mq-admin` y `ms-rutaexpress-kafka-admin` ya recibieron los cambios. `testFrontend` se dejó intacta porque es una aplicación React/Vite independiente en la raíz y no corresponde al frontend Angular de este análisis.
> - Documentos de la evaluación: `C:\Users\pv-alumno\Downloads\EP2_Guia_Evaluacion.docx` y `C:\Users\pv-alumno\Downloads\Explicacion_de_Evaluacion_2.docx`
> - Script de despliegue (**fuera del repo**): `C:\Users\pv-alumno\Downloads\deploy-aws.bat`

---

## 0. Resumen rápido

**Estado general tras la revisión independiente del 2026-10-17:** los cambios sí están en `main` y en las nueve ramas publicadas del monorepo, pero todavía no funcionan correctamente en conjunto. El BFF no compila; sus tests y el test nuevo de shipments tienen otros errores de compilación. Se reprodujeron fallos de idempotencia y compensación de capacidad. El frontend compila y pasa sus 3 tests, con 11 advertencias CSS pendientes. Los tres Compose validan su configuración, pero el script de inicialización Oracle requiere correcciones. Los resultados y límites de esta verificación están en la **sección 13**, que prevalece sobre los estados anteriores.

**Estado de ramas:** se revisaron `origin/main` y las diez ramas remotas. Se integró `main` mediante merge en las nueve ramas activas del monorepo, preservando sus commits propios, y se publicaron `main` y esas nueve ramas con éxito el 2026-10-17. `testFrontend` conserva su HEAD original (`abffea3`) porque es un frontend React/Vite separado; el merge automático produjo conflictos de renombres y README, se canceló y no se alteró su historial.

**Pendientes de entrega o validación:**

| Prioridad | Servicio | Qué falta | Tarea |
|---|---|---|---|
| **P0 obligatorio** | `ms-rutaexpress-bff` | Rutas presentes, pero falla `compileKotlin`; también hay errores independientes en sus tests | T4, revisión R1/R2 |
| P0 (config) | `infra/apps` | Compose corregido; script de despliegue externo aún usa valores antiguos | T1, T2, T3 |
| P1 (docs) | todos | READMEs de topología y despliegue actualizados | T6 |
| P1 (config) | `ms-rutaexpress-kafka-admin` | `.gitignore` agregado | T7 |
| P2 (mejora) | shipments + catalog | Orden aplicado; idempotencia y compensaciones incompletas, con fallos reproducidos | T10, revisión R4/R5/R6 |
| P2 (mejora) | `ms-rutaexpress-catalog` | Reserva y liberación REST usan bloqueo pesimista | T11 |
| P3 (limpieza) | `ms-rutaexpress-shipments` | Routing key de resultado centralizada en `RabbitNombres` | T12 |
| P3 (menores) | bff, shipments, frontend | Cambios presentes; build Angular ejecutado, persisten 11 advertencias CSS | T13 |
| Opcional | `frontend` | Vista Admin de RabbitMQ agregada | T14 |

**Acciones pendientes:** corregir los hallazgos R1-R6 de la sección 13, T1/T3 en el script externo, T5 (API Gateway), T9 (repos separados), T13d (advertencias CSS) y T15 (preparar y ejecutar la demo). `testFrontend` requeriría una adaptación independiente a React/Vite antes de incorporar cambios de interfaz.

---

## 1. Contexto del proyecto

- Es un **monorepo**: cada carpeta `ms-*` es un proyecto Gradle **independiente**. No hay build en la raíz; los comandos se ejecutan dentro de cada carpeta.
- **Stack:** Kotlin 2.3.21, Spring Boot 4.1.1, Spring AMQP, Jackson 3 (paquete `tools.jackson.*`), Gradle 9.7.1 (wrapper), JDK 21+, Angular 19 + MSAL, Oracle (perfil `prod`) o H2 (perfil `local`), RabbitMQ 4 y Kafka 3.9 (heredado de una evaluación anterior).
- **Tests:** JUnit 5, `mockito-kotlin 6.2.2`, `spring-security-test` y Testcontainers (solo en notify, activado con `RUN_DOCKER_TESTS=true`).

| Servicio | Carpeta | Puerto | Rol en la EP2 | ¿Cambió en esta iteración? |
|---|---|---|---|---|
| Frontend | `frontend/` | 4200 / 80 | Angular + MSAL, login con Entra ID | No |
| BFF | `ms-rutaexpress-bff/` | 8080 | Valida el JWT, autoriza por rol y reenvía a los servicios internos | Sí (incluye proxy admin) |
| Catálogo | `ms-rutaexpress-catalog/` | 8081 | Consume comandos de capacidad y publica el resultado | Sí |
| Notificaciones | `ms-rutaexpress-notify/` | 8082 | Consume solicitudes de notificación (email, ticket, etiqueta) | Sí |
| Auditoría | `ms-rutaexpress-audit/` | 8083 | Timeline por Kafka (no es parte de RabbitMQ) | No |
| Reportes | `ms-rutaexpress-report/` | 8084 | KPIs por Kafka | No |
| **Principal** | `ms-rutaexpress-shipments/` | 8085 | Envíos: publica comandos y eventos, consume la cola de auditoría | Sí |
| **Admin RabbitMQ** | `ms-rutaexpress-mq-admin/` | 8086 | API REST de administración de la topología | Sí |
| Admin Kafka | `ms-rutaexpress-kafka-admin/` | 8087 | Tópicos de Kafka | No |
| Infraestructura | `infra/mq`, `infra/apps`, `infra/kafka` | — | Docker Compose | `infra/mq` sí |

> La guía llama al admin `ms-<caso>-rabbit-admin`. Aquí se llama `ms-rutaexpress-mq-admin`. Es aceptable, pero al separar los repos conviene nombrar el repo `ms-rutaexpress-rabbit-admin`.

---

## 2. Qué exigen los documentos (resumen fiel)

### 2.1 Ponderación

- La EP2 vale **30 % de la asignatura**.
  - **Encargo:** 40 % de la EP2, en parejas, con código entregado por GitHub.
  - **Presentación:** 60 % de la EP2, individual, de 5 a 10 minutos, con demostración en vivo en la nube.

| Indicador | Encargo / Presentación | Qué se evalúa |
|---|---|---|
| **IL 2.1** Configura colas | 35 % / 30 % | **Encargo:** nombres de colas, exchanges y bindings centralizados; beans `Queue`, `Exchange` y `Binding`; configuración separada de la lógica de negocio. **Presentación:** 2 nodos RabbitMQ en clúster funcionando, desplegados con Docker Compose en la nube |
| **IL 2.2** Mensajería asíncrona | 35 % / 40 % | **Encargo:** consumidores `@RabbitListener` agrupados por dominio, ACK explícito y manejo de errores. **Presentación:** 3 colas con su DLQ, exchanges direct y topic, microservicios que escriben y leen, y logs de los mensajes que llegan a la DLQ |
| **IL 2.3** Administración | 30 % / 30 % | **Encargo:** endpoints REST del administrador, `RabbitAdminService` y validación de entrada. **Presentación:** administrador creando y eliminando, explicación de las métricas y de las políticas de retención |

### 2.2 Alcance obligatorio (guía, sección 4)

1. Dos nodos RabbitMQ (`rabbit1` y `rabbit2`) unidos en un clúster.
2. Clúster en la nube (AWS EC2) levantado con Docker Compose, con los servicios accesibles y documentados.
3. Tres colas para funcionalidades diferentes, cada una con su DLQ.
4. Exchanges **direct** y **topic** con routing keys y bindings definidos.
5. Configuración centralizada (`application.yml` o una clase `@Configuration`), con beans en clases separadas de la lógica de negocio.
6. Productores y consumidores con `@RabbitListener` agrupados por dominio, **ACK manual** y manejo explícito de errores. Hay que **distinguir los errores recuperables (reintento acotado) de los no recuperables (NACK sin requeue hacia la DLQ)**.
7. **Consumo de las DLQ**, registrando en logs los mensajes no entregados.
8. Microservicio administrador con API REST para **crear colas, exchanges y bindings, y eliminar colas y exchanges**, con `RabbitAdminService` y validación de entrada (nombre vacío → 400 con un mensaje claro).
9. Políticas de retención y limpieza (límites de cola y expiración).
10. Explicación de las métricas: colas activas, mensajes pendientes, consumidores y mensajes en DLQ.
11. Repositorios en GitHub con `.gitignore` estricto según la tecnología.

### 2.3 Responsabilidades (guía, sección 5)

- **API Gateway:** valida el JWT y **enruta también `/api/admin/*`**.
- **BFF:** valida otra vez el JWT y aplica autorización por rol (**la administración es solo para Admin**). No accede a la base de datos.
- **Principal y catálogo:** ejecutan la lógica de negocio, persisten y **producen y consumen** en RabbitMQ.

### 2.4 Flujo que se debe demostrar (guía, sección 6, en resumen)

1. Login con MSAL y Entra ID.
2. Llamada a través del API Gateway, que valida el JWT y enruta al BFF.
3. El BFF valida el JWT y el rol, y llama al principal.
4. El principal valida la regla, persiste, **publica en el exchange** y responde sin esperar.
5. RabbitMQ enruta (direct o topic).
6. **El consumidor del catálogo recibe el comando, descuenta capacidad, hace ACK y publica un evento con el resultado.**
7. Si falla: NACK sin requeue, el mensaje va a la DLQ y su listener lo registra en logs.
8. Los consumidores de notificación y auditoría procesan con ACK.
9. **El Admin crea o elimina una cola, un exchange o un binding con el microservicio administrador, y el cambio se ve en el Management UI.**
10. Se explican las métricas en el Management UI.
11. La respuesta JSON vuelve a Angular.

### 2.5 Pruebas mínimas obligatorias (guía, sección 7)

| ID | Prueba | Resultado esperado |
|---|---|---|
| R1 | Sin token | Se rechaza |
| R2 | Token inválido o expirado | Se rechaza |
| R3 | Token válido con rol autorizado | Se procesa |
| R4 | Token válido sin permiso (un usuario no Admin crea una cola) | Se rechaza por autorización |
| R5 | Regla de negocio inválida | No se ejecuta y **no se publica nada** |
| R6 | Regla de negocio válida | Se completa, se publica el mensaje y se devuelve el JSON |
| R7 | Estado del clúster | `rabbitmqctl cluster_status` o el Management UI muestran los 2 nodos |
| R8 | Publicación y consumo en las 3 colas | ACK y la cola queda vacía |
| R9 | Error no recuperable | NACK sin requeue, el mensaje llega a la DLQ y queda en logs |
| R10 | Error recuperable | Reintentos acotados y, si persiste, DLQ |
| R11 | Admin crea una cola, un exchange o un binding válidos | Se crean y se ven en el Management UI |
| R12 | Admin elimina una cola o un exchange | Desaparecen del Management UI |
| R13 | Admin con nombre vacío o configuración inválida | 400 con un mensaje claro y no se crea nada |
| R14 | Política de retención | Al llegar al límite o a la expiración, los mensajes se descartan o pasan a la DLQ |

### 2.6 Entregables del encargo (guía, sección 8)

- **Repositorios separados:** frontend, BFF, microservicio principal, microservicio de catálogo y microservicio administrador de RabbitMQ.
- Código que compila, con buenas prácticas y pruebas básicas.
- Conexión a la base de datos **cloud** mediante entidades, repositorios y propiedades.
- Filtros JWT, frontend con OAuth 2.0 / OIDC y uso del JWT en las llamadas.
- Nombres centralizados, beans separados, productores y consumidores por dominio, ACK y errores, DLQ y logs, y administrador REST con `RabbitAdminService` y validación.
- `.gitignore` estricto y **README.md en cada repositorio**.
- Los enlaces se suben a AVA y se envían por correo al docente.

### 2.7 Conceptos que el docente puede preguntar (Explicación de Evaluación 2)

- La diferencia entre direct y topic, y cuándo conviene cada uno.
- Por qué existen las DLQ y qué problema resuelven.
- El papel de ACK y NACK en la confiabilidad.
- Cómo afecta el **prefetch (QoS)** al rendimiento y qué problemas evita.
- Por qué conviene separar la lógica de negocio de la lógica de mensajería.

> **Sugerencia del docente**, que este proyecto ya sigue: una cola de comandos con un exchange direct, y colas de notificaciones y auditoría con un exchange topic, cada una con su DLQ.

---

## 3. Topología RabbitMQ actual (referencia; NO cambiar nombres)

Coincide con el diagrama de la guía y está **verificada en vivo** en el clúster local.

| Elemento | Nombre | Tipo / detalle |
|---|---|---|
| Exchange de comandos | `ex.rutaexpress.cmd` | direct, durable |
| Exchange de eventos | `ex.rutaexpress.events` | topic, durable |
| Dead-letter exchange | `ex.rutaexpress.dlx` | direct, durable |
| Cola 1 (comandos) | `q.rutaexpress.command` | bindings en `cmd`: `capacity.reserve`, `capacity.release`. DLX key `command` |
| DLQ 1 | `q.rutaexpress.command.dlq` | binding en `dlx`: `command` |
| Cola 2 (notificaciones) | `q.rutaexpress.notification` | binding en `events`: `notification.#`. DLX key `notification` |
| DLQ 2 | `q.rutaexpress.notification.dlq` | binding en `dlx`: `notification` |
| Cola 3 (auditoría) | `q.rutaexpress.audit` | bindings en `events`: `shipment.#`, `catalog.#`. DLX key `audit` |
| DLQ 3 | `q.rutaexpress.audit.dlq` | binding en `dlx`: `audit` |
| Política `rutaexpress.trabajo` | las 3 colas de trabajo | `message-ttl` 86400000 (24 h), `max-length` 10000, `overflow` reject-publish |
| Política `rutaexpress.dlq` | las 3 DLQ | `message-ttl` 604800000 (7 días), `max-length` 10000, `overflow` drop-head |

**Quién produce y quién consume:**

| Routing key | Exchange | Productor | Cola | Consumidor (clase) |
|---|---|---|---|---|
| `capacity.reserve` / `capacity.release` | cmd (direct) | shipments `MensajeriaPublicador` | command | catalog `CapacidadComandoListener` |
| `catalog.capacity.reserved`, `.rejected` y `.released` | events (topic) | catalog `CapacidadEventoPublicador` | audit | shipments `AuditoriaListener` |
| `shipment.status.<ESTADO>` | events (topic) | shipments `MensajeriaPublicador` | audit | shipments `AuditoriaListener` |
| `notification.email.send`, `notification.warehouse.ticket` y `notification.label.generate` | events (topic) | shipments `MensajeriaPublicador` | notification | notify `NotificationConsumerViewModel` |
| (dead-letter) | dlx | RabbitMQ al hacer NACK o al vencer el TTL | *.dlq | `DeadLetterRegistro` en shipments, catalog y notify |

**Dónde se definen los nombres:** se declaran en 4 lugares, y si se cambia uno **hay que cambiar todos**.

- `ms-rutaexpress-shipments/src/main/resources/application.yml` (sección `app.rabbit`) y `.../configuracion/RabbitNombres.kt`
- `ms-rutaexpress-catalog/src/main/resources/application.yml` (sección `app.rabbit`) y `.../config/RabbitNombres.kt`
- `ms-rutaexpress-notify/src/main/resources/application.properties` (`app.rabbit.*`) y `.../config/RabbitNombres.kt`
- `ms-rutaexpress-mq-admin/src/main/resources/application.properties` (`app.rabbit.*`) y `.../RabbitNombres.kt` (topología completa en `RabbitTopology.kt`)

**Parámetros del consumidor:**

- `app.rabbit.consumer.prefetch=${RABBIT_PREFETCH:10}`
- `max-attempts=${RABBIT_RETRY_MAX_ATTEMPTS:3}` y `initial-interval-ms=500` (en notify: `rutaexpress.notify.retry.*`)
- `app.rabbit.dlq.auto-startup=${RABBIT_DLQ_CONSUMER_ENABLED:true}`

**Endpoints de mq-admin (base `/api/mq-admin`, solo `ROLE_ADMIN`):**

| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| GET | `/cluster`, `/queues`, `/alerts`, `/metrics`, `/exchanges`, `/bindings` | — | 200 |
| POST | `/topology` | — | Declara la topología fija y aplica la retención |
| PUT | `/retention` | `{commandTtlMs, dlqTtlMs, maxLength}` | 200 / 400 |
| POST | `/queues` | `{name, durable, ttlMs, maxLength, expiresMs, overflow, deadLetterExchange, deadLetterRoutingKey}` | 201 / 400 / 409 |
| DELETE | `/queues/{name}?force=false` | — | 200 / 404 / 409 (protegida sin `force`) |
| POST | `/exchanges` | `{name, type: direct, topic, fanout o headers, durable}` | 201 / 400 / 409 |
| DELETE | `/exchanges/{name}?force=false` | — | 200 / 404 / 409 |
| POST | `/bindings` | `{source, destination, destinationType: queue o exchange, routingKey}` | 201 / 400 / 404 / 409 |
| DELETE | `/bindings?source=&destination=&destinationType=queue&routingKey=&force=false` | — | 200 / 404 / 409 |
| POST | `/exchanges/{name}/messages` | `{routingKey, body}` | Publica un mensaje de prueba (`confirmed`, `routed`) |

Los errores se devuelven como `ProblemDetail` (`AdminErrors.kt`): 400 por validación, 404, 409 y 503 si el broker no responde.

---

## 4. Archivos creados o modificados en la iteración actual (ya hechos; no rehacer)

| Servicio | Archivos | Para qué |
|---|---|---|
| infra | `infra/mq/compose.yml` (servicios renombrados a `rabbit1` y `rabbit2`), `infra/mq/rabbitmq.conf` (`autoheal`) | Clúster con los nombres de la guía; arregla B3 |
| shipments | `configuracion/RabbitNombres.kt`, `configuracion/RabbitMqConfig.kt`, `mensajeria/MensajeriaPublicador.kt`, `mensajeria/AuditoriaListener.kt`, `mensajeria/AuditoriaEnvioService.kt`, `mensajeria/DeadLetterRegistro.kt`, `mensajeria/Errores.kt`, `mensajeria/Mensajes.kt`, `dominio/EstadoCapacidad.kt`, `dominio/RegistroAuditoriaEnvio.kt`, `dominio/RegistroAuditoriaRepositorio.kt`, `Envio.kt` (+`capacityStatus` y `@DynamicUpdate`), `EnvioServicio.kt`, `CatalogoCliente.kt` (sin reserva REST), `ManejadorGlobalExcepciones.kt` (503), `db/migration/V2__capacidad_y_auditoria_rabbit.sql`, `test/.../mensajeria/MensajeriaTests.kt`. **Borrado:** `comandos/NotificacionPublisher.kt` | Productor de comandos, eventos y notificaciones con confirmación del broker; consumidor de auditoría y de su DLQ |
| catalog | `config/RabbitNombres.kt`, `config/RabbitMqConfig.kt`, `messaging/CapacidadComandoListener.kt`, `messaging/CapacidadComandoProcesador.kt`, `messaging/CapacidadEventoPublicador.kt`, `messaging/DeadLetterRegistro.kt`, `messaging/MensajesCapacidad.kt`, `model/entity/RegistroComandoCapacidad.kt`, `model/repository/RegistroComandoCapacidadRepository.kt`, `FleetCapacityRepository.kt` (`findLocked...` con `PESSIMISTIC_WRITE`), `db/migration/V2__registro_comandos_capacidad.sql`, `V1` (quitado el índice duplicado que daba ORA-01408), `test/.../messaging/CapacidadComandoTests.kt` | Consumidor de comandos idempotente con bloqueo; publica el resultado |
| notify | `config/RabbitNombres.kt`, `config/RabbitMqConfig.kt`, `viewmodel/NotificationConsumerViewModel.kt`, `viewmodel/DeadLetterRegistro.kt`, `application.properties` y sus tests | Una cola con 3 routing keys; distingue errores; consumidor de la DLQ |
| mq-admin | `RabbitAdminService.kt`, `ClienteGestion.kt`, `Solicitudes.kt`, `Errores.kt`, `RabbitNombres.kt`, `RabbitTopology.kt`, `MqAdminService.kt`, `MqAdminController.kt`, `AdminErrors.kt`, `application.properties`, `.gitignore` y los tests `MqAdminControllerTests`, `RabbitAdminServiceTests`, `AdminSecurityTests` y `RabbitTopologyTests` | CRUD de la topología, validación, métricas y retención |

---

## 5. Análisis ANTERIOR (línea base, antes de la iteración actual)

> Se transcribe completo para tener la referencia. El estado actual de cada punto está en la sección 6.

**Veredicto de entonces:** el código compilaba y todos los tests pasaban. La parte de RabbitMQ iba bien encaminada (2 nodos, 3 colas con su DLQ, ACK manual, políticas de retención y métricas), pero **no cumplía la EP2**. Faltaba entera la parte que vale el 30 % del encargo: el administrador no creaba ni eliminaba colas, exchanges ni bindings. Además había dos bugs, comprobados ejecutando el código, que harían fallar la demo en la nube.

### 5.1 Compilación y tests (entonces)

| Componente | Compila | Tests |
|---|---|---|
| frontend (Angular 19) | ✅ | 3/3 |
| shipments / catalog / bff / audit | ✅ | 11 / 18 / 12 / 8 |
| notify / report / mq-admin / kafka-admin | ✅ | 20 (2 omitidos) / 5 / 5 / 4 |

- 83 tests de backend y 0 fallos.
- Los 2 tests de integración de notify se omitieron porque Docker no estaba corriendo.
- Maven Central respondió 429 y se usó un mirror de Google sin tocar el proyecto.
- Había 48 warnings de Kotlin.

### 5.2 Cumplimiento (entonces)

**IL 2.1**

| Requisito | Estado | Detalle |
|---|---|---|
| Beans Queue, Exchange y Binding separados | ✅ | `notify/RabbitMqConfig.kt` y `mq-admin/RabbitTopology.kt` |
| Nombres centralizados | ⚠️ | Routing keys escritas a mano en `NotificacionPublisher.kt:31,47,64`; topología duplicada en dos servicios |
| 2 nodos con Docker Compose | ✅/⚠️ | Ver B3 |

**IL 2.2**

| Requisito | Estado | Detalle |
|---|---|---|
| 3 colas con DLQ | ✅ | `q.cmd.email`, `q.cmd.warehouse`, `q.cmd.label` y sus `.dlq` |
| Exchanges direct y topic | ⚠️ | Existían, pero nadie publicaba en `cmd.topic` y cada cola estaba enlazada a ambos |
| `@RabbitListener` por dominio y ACK manual | ✅ | `NotificationConsumerViewModel.kt` |
| Distinguir errores recuperables y no recuperables | ❌ | Todo se reintentaba 3 veces (`NotificationConsumerViewModel.kt:52-67`) |
| DLQ consumidas y en logs | ❌ | No había listeners sobre las `.dlq` |
| Productores y consumidores en principal y catálogo | ⚠️ | Catalog no usaba RabbitMQ; la reserva era REST síncrona |

**IL 2.3**

| Requisito | Estado | Detalle |
|---|---|---|
| REST para crear y eliminar colas, exchanges y bindings | ❌ | Solo había GET cluster/queues/alerts, POST topology y PUT retention |
| `RabbitAdminService` y validación | ⚠️ | Solo se validaba la retención |
| Rutas admin por BFF/Gateway | ⚠️ | `BrokerAdminController.kt:26` solo reenviaba GET |
| Métricas | ✅ | `/queues`, gauge de Prometheus y dashboard Angular |
| Retención | ✅ | TTL de 24 h / 7 días, max-length 10000, reject-publish |

Esto dejaba sin cubrir R11, R12 y R13.

**Entrega**

| Requisito | Estado | Detalle |
|---|---|---|
| Repos separados | ❌ | Monorepo |
| `.gitignore` estricto | ⚠️ | mq-admin y kafka-admin sin `.gitignore`; el de la raíz mezcla Gradle y Node |
| README, JWT, MSAL e interceptor | ✅ | |

### 5.3 Bugs confirmados (entonces)

- **B1:** `.env.example` y `infra/apps/compose.yml` usan `SPRING_PROFILES_ACTIVE=local` (driver H2) con una URL de Oracle. Resultado: `Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:oracle:thin:@//oracle:1521/FREEPDB1`. Afecta a catalog, audit, report y shipments. `deploy-aws.bat` copia `.env.example` tal cual y no levanta Oracle.
- **B2:** con el perfil local, shipments no publicaba nada (`app.rabbit.enabled: false`). Los comandos se descartaban en silencio.
- **B3:** `pause_minority` con 2 nodos. Si cae uno, el otro se pausa y se cae todo el clúster.
- **B4:** fuga de capacidad. Se reservaba por REST en catalog; si después fallaba la publicación, la base hacía rollback pero la reserva quedaba. Al reintentar se reservaba otra vez.
- **B5:** sobreventa con concurrencia. `CatalogViewModelService.kt:297-321` lee, suma y guarda sin bloqueo.
- **Menores:**
  1. El dashboard del BFF falla completo si catalog o report están caídos (`BffGatewayService.kt:107-116`).
  2. `app.kafka.topics.audit.timeline` no existe en el yml (`EnvioEventPublisher.kt:19`).
  3. Broker caído → 500 en vez de 503.
  4. `/actuator/prometheus` es público en BFF y shipments.
  5. El build del frontend descarta 11 reglas CSS de Tailwind.
  6. `nginx.conf` envía `/api` directo al BFF, saltándose el API Gateway.

### 5.4 Prioridades que se propusieron entonces

1. mq-admin: POST/DELETE para colas, exchanges y bindings, con DTOs `@Valid`, `RabbitAdminService` y respuestas 400. Proxy en el BFF solo para Admin.
2. notify: listeners sobre las 3 DLQ con `x-death`; errores no recuperables a la DLQ sin reintentos; prefetch explícito.
3. Compose y despliegue: perfil `prod` con Oracle o corregir el perfil local; definir `APP_RABBIT_ENABLED` y `APP_CATALOGO_ENABLED`.
4. Usar el exchange topic en un flujo real y mover las routing keys a la configuración.
5. Cambiar `pause_minority` por `autoheal`, separar los repos y agregar los `.gitignore`.

No se pudo verificar entonces: el tenant de Entra ID, el API Gateway (autorizador JWT y CORS), EC2, ni si `bff.atlaa.app` (de `vercel.json`) es el API Gateway o el BFF directo.

---

## 6. Análisis registrado el 2026-10-15 (histórico; revisión actual en la sección 13)

### 6.1 Compilación y tests (en una copia aislada del working tree)

| Servicio | Antes | Ahora | Notas |
|---|---|---|---|
| shipments | 11 | **19** ✅ | `bootJar` OK |
| catalog | 18 | **25** ✅ | `bootJar` OK |
| notify | 20 (2 omitidos) | **23** ✅ | 3 de ellos son de integración y **pasan con Docker** (`RUN_DOCKER_TESTS=true`) |
| mq-admin | 5 | **26** ✅ | `bootJar` OK |
| bff / audit / report / kafka-admin / frontend | 12 / 8 / 5 / 4 / 3 | sin cambios | No se volvieron a ejecutar (el código no cambió) |

- **Total de backend: 122 tests y 0 fallos.**
- Hay 40 warnings de Kotlin (31 en shipments); no bloquean.
- Los tests de integración de notify contra un RabbitMQ 4 real confirman:
  - La cola, su DLQ y los bindings se declaran en el broker.
  - Un email que falla siempre termina en la DLQ tras los reintentos.
  - Un JSON inválido va a la DLQ **sin** reintentar.

### 6.2 Verificación en vivo (solo lectura, sobre el clúster que el usuario tenía levantado)

- `rabbit@rabbit1` y `rabbit@rabbit2` corriendo, sin particiones, con `cluster_partition_handling = autoheal`. ✅
- Los 3 exchanges, las 6 colas, los 8 bindings y las 2 políticas coinciden exactamente con la sección 3. ✅
- `GET /api/mq-admin/metrics`, `/queues`, `/exchanges` y `/bindings` responden 200 con los datos esperados. ✅
- **Las 6 colas tienen su líder en `rabbit1`.** Son colas classic y no se replican (ver T15).
- **Resolución DNS dentro de la red `rutaexpress`:** `rabbit1` y `rabbit2` resuelven; los nombres anteriores de host no resuelven (confirma N1).

### 6.3 Cumplimiento actual por requisito

| Requisito | Antes | Ahora | Evidencia / detalle |
|---|---|---|---|
| **IL 2.1** Nombres centralizados | ⚠️ | ✅ | `RabbitNombres` (`@ConfigurationProperties("app.rabbit")`) en los 4 servicios. Queda **1 texto fijo**: `AuditoriaListener.kt:40` (T12) |
| Beans separados | ✅ | ✅ | `RabbitMqConfig` en shipments, catalog y notify; `RabbitTopology` en mq-admin |
| 2 nodos en clúster | ⚠️ | ✅ local / ❌ nube | Nombres de la guía y `autoheal`; compose de apps corregido. La nube aún depende del script y despliegue (T1, T3, T8, T15) |
| **IL 2.2** 3 colas con DLQ | ✅ | ✅ | command, notification y audit, cada una con su `.dlq` |
| direct y topic | ⚠️ | ✅ | direct para comandos punto a punto; topic para eventos y notificaciones por patrón |
| `@RabbitListener` por dominio y ACK manual | ✅ | ✅ | `AcknowledgeMode.MANUAL` en 3 servicios |
| Distinguir errores | ❌ | ✅ | `MensajeNoRecuperableException` → `basicNack(tag,false,false)` inmediato; errores técnicos → 3 reintentos con backoff → DLQ |
| DLQ consumidas y en logs | ❌ | ✅ | `DeadLetterRegistro` en los 3 servicios: registra `x-death` (motivo, cola y routing key) y suma un contador Micrometer |
| Principal y catálogo producen y consumen | ⚠️ | ✅ | catalog consume `capacity.*` y publica `catalog.capacity.*`; shipments consume el resultado (paso 6 del flujo) |
| Prefetch explícito | — | ✅ | `setPrefetchCount(RABBIT_PREFETCH=10)` |
| **IL 2.3** REST crear y eliminar | ❌ | ✅ | Ver la tabla de endpoints de la sección 3 |
| `RabbitAdminService` y validación | ⚠️ | ✅ | `@NotBlank`, regex de nombre, prefijo `amq.` reservado, rangos de TTL, tipos válidos, 400/404/409/503, topología núcleo protegida con `force` |
| Rutas admin por BFF/Gateway | ⚠️ | ⚠️ | El BFF ahora reenvía las operaciones admin y restringe el acceso a Admin; falta configurar el API Gateway (**T5**) |
| Métricas | ✅ | ✅ | Además: `/metrics` (activeQueues, messagesReady, messagesUnacknowledged, consumers, dlqQueues, dlqMessages) |
| Retención | ✅ | ✅ | Aplicada en el broker (verificado) |
| **Entrega** Repos separados | ❌ | ❌ | **T9** |
| `.gitignore` | ⚠️ | ✅ | mq-admin y kafka-admin tienen reglas para sus herramientas y secretos (**T7**) |
| README | ✅ | ✅ | READMEs de topología, administración, despliegue y servicios actualizados (**T6**) |
| Cambios en GitHub | — | ✅ | `main` y las nueve ramas activas del monorepo se actualizaron el 2026-10-17 (**T8**); `testFrontend` quedó intacta por ser otra app |

### 6.4 Estado de los bugs anteriores

| Bug | Estado | Detalle |
|---|---|---|
| B1 | ⚠️ parcial | Compose y `.env.example` del repo ya usan el perfil Oracle y `DB_URL`; queda actualizar el script externo de despliegue (**T1, T3**) |
| B2 | ✅ | `application-local.yml` de shipments ya no apaga RabbitMQ; el compose define `RABBIT_ENABLED=true` y `CATALOGO_ENABLED=true` |
| B3 | ✅ | `autoheal` (verificado en vivo) |
| B4 | ✅ código | Se ordenó la publicación para que la reserva sea el último paso y se agregó idempotencia por envío; tests agregados, falta ejecutarlos (**T10**) |
| B5 | ✅ código | Los endpoints REST de reserva/liberación ahora usan consultas con bloqueo pesimista (**T11**); tests pendientes de ejecución |
| Menor 1: dashboard del BFF | ✅ código | Las fuentes de datos fallidas generan avisos y no tumban todo el dashboard (**T13a**); tests pendientes |
| Menor 2: propiedad de Kafka | ✅ | Propiedad de Kafka corregida (**T13b**) |
| Menor 3: 500 vs 503 | ✅ | shipments `MensajeriaNoDisponibleException` → 503; mq-admin → 503 |
| Menor 4: prometheus público | ✅ código | Métricas restringidas a Admin en BFF y shipments; tests agregados, falta ejecutarlos (**T13c**) |
| Menor 5: Tailwind | ⚠️ pendiente de verificación | El build del frontend no se pudo ejecutar porque no hay Node/npm (**T13d**) |
| Menor 6: nginx | ✅ | Configuración documentada como local y variables de entorno aclaradas (**T13e**) |

### 6.5 Problemas NUEVOS introducidos por la iteración actual

- **N1 (resuelto en el repositorio; queda el script externo):** los ocho servicios de `infra/apps/compose.yml` y `infra/README.md` usan `rabbit1`/`rabbit2`. Falta actualizar `deploy-aws.bat:652` (**T1**).
- **N2 (resuelto):** compose pasa `RABBITMQ_MANAGEMENT_URLS`, que es la variable que consume mq-admin.
- **N3 (resuelto en GitHub):** cambios guardados en `main` y publicados junto con las nueve ramas activas el 2026-10-17 (**T8**). El servidor los recibirá cuando se ejecute el despliegue actualizado.
- **N4 (resuelto):** READMEs raíz, frontend, infraestructura y servicios actualizados (**T6**).

### 6.6 Pruebas mínimas (sección 7 de la guía): estado actual

| ID | En el código | Evidencia | ¿Demostrable en la nube hoy? |
|---|---|---|---|
| R1-R3 | ✅ | Filtros JWT existentes; `AdminSecurityTests` (401) | ❌ (depende de T1-T3) |
| R4 | ✅ código | `AdminSecurityTests` y nuevos tests de seguridad del proxy BFF → 403. Falta ejecutar los tests | ❌ |
| R5 | ✅ | `EnvioServicio`: la transición inválida o la falta de capacidad lanzan la excepción antes de guardar y publicar | ❌ |
| R6 | ✅ | `MensajeriaTests` | ❌ |
| R7 | ✅ | Verificado en vivo en local | ❌ en la nube |
| R8 | ✅ | Los 3 consumidores con ACK | ❌ |
| R9 | ✅ | Tests unitarios y de integración (JSON inválido → DLQ) | ❌ |
| R10 | ✅ | Test de integración (SMTP caído → reintentos → DLQ) | ❌ |
| R11-R13 | ✅ código | `MqAdminControllerTests`, `RabbitAdminServiceTests` y proxy admin BFF; ejecución de tests pendiente | ❌ (falta despliegue) |
| R14 | ✅ | Políticas verificadas en el broker | ❌ |

### 6.7 Checklist final de la guía (sección 9)

| # | Punto | Antes | Ahora |
|---|---|---|---|
| 1 | La EP1 sigue operativa (login, rutas protegidas y roles) | ✅ | ✅ (sin cambios; no verificado en vivo) |
| 2 | API Gateway rechaza lo no autorizado y enruta todo, incluido `/api/admin/*` | ⚠️ | ⚠️ (BFF completado; falta configurar API Gateway, T5) |
| 3 | El BFF valida el JWT y no accede a la base | ✅ | ✅ |
| 4 | Dos nodos en clúster | ⚠️ | ✅ |
| 5 | Clúster en la nube con Compose y accesible | ❌ | ❌ (script externo y despliegue pendientes: T1, T3, T15) |
| 6 | Tres colas diferenciadas con DLQ | ✅ | ✅ |
| 7 | direct y topic con keys y bindings correctos | ⚠️ | ✅ |
| 8 | Nombres centralizados y separados | ⚠️ | ✅ |
| 9 | Microservicios producen y consumen con `@RabbitListener` | ⚠️ | ✅ |
| 10 | ACK y errores explícitos (NACK, reintentos y DLQ) | ⚠️ | ✅ |
| 11 | DLQ consumidas y en logs | ❌ | ✅ |
| 12 | El admin crea y elimina por REST con `RabbitAdminService` | ❌ | ✅ |
| 13 | El admin valida nombres vacíos y configuraciones inválidas | ❌ | ✅ |
| 14 | Políticas de retención | ✅ | ✅ |
| 15 | Puedo explicar las métricas | ✅ | ✅ |
| 16 | Repos en GitHub con `.gitignore` y README, enlaces enviados | ❌ | ❌ (repositorios separados y enlaces pendientes: T9) |
| 17 | Flujo completo de extremo a extremo | ❌ | ❌ (depende de todo lo anterior) |
| | **Puntaje (✅=1, ⚠️=0,5, ❌=0)** | **8 / 17 ≈ 47 %** | **13,5 / 17 ≈ 79 %** |

### 6.8 Avance sobre el análisis anterior

| Bloque del análisis anterior | Puntos | Resueltos | Parciales | Pendientes |
|---|---|---|---|---|
| Requisitos con ⚠️ o ❌ | 10 | 7 | 1 (`.gitignore`) | 2 (proxy BFF, repos) |
| Bugs B1-B5 | 5 | 2 (B2, B3) | 2 (B4, B5) | 1 (B1) |
| Bugs menores | 6 | 1 (503) | 0 | 5 |
| **Total** | **21** | **10** | **3** | **8** |

- Ponderado (críticos ×3, importantes ×2, menores ×1): **27,5 / 42 ≈ 65 %**. Contando los puntos por igual: ≈55 %.
- Lo de mayor peso en la nota ya está hecho: IL 2.3 completo, DLQ, errores y catálogo por cola.
- **Lo que falta es despliegue y entrega.** Eso es lo que permite mostrar todo en la presentación, que vale el 60 % de la EP2.
- El código actualizado ya está en `main` y las nueve ramas del monorepo en GitHub (**T8**). El script externo y el despliegue en la nube siguen pendientes.

---

## 7. PLAN DE TRABAJO: tareas pendientes

> Formato: cada tarea indica el servicio, los archivos, el problema, los pasos, el criterio de aceptación y cómo verificar. Marca `[x]` al terminar. Las líneas pueden moverse; busca por el texto indicado.

### P0: bloqueantes para la demo en la nube

#### T1. Corregir los nombres de host de RabbitMQ `[ ]` (compose del repo completado; falta el script externo)

- **Servicio:** infra (configuración, no código Kotlin).
- **Archivos:**
  - `infra/apps/compose.yml`, en los 8 servicios `bff`, `catalog`, `notify`, `audit`, `report`, `shipments`, `mq-admin` y `kafka-admin`.
  - `infra/README.md:9`.
  - `C:\Users\pv-alumno\Downloads\deploy-aws.bat:652` (**fuera del repo: pedir permiso al usuario antes de editarlo**).
- **Pasos:**
  1. En los ocho servicios de `infra/apps/compose.yml`, usar `rabbit1` y `rabbit2` en `RABBITMQ_HOST` y `RABBITMQ_ADDRESSES`. **Aplicado.**
  2. En `infra/README.md`, consultar el clúster con `rabbit1`. **Aplicado.**
  3. Actualizar el comando equivalente en `deploy-aws.bat:652`. **Pendiente:** está fuera del repositorio y no se modificó.
- **Aceptación:**
  - No quedan referencias a los hostnames anteriores en la configuración activa del repositorio.
  - `docker compose -f infra/apps/compose.yml config --quiet` termina sin errores (requiere `docker network create rutaexpress`).

#### T2. Variable de gestión de mq-admin en el compose `[x]`

- **Servicio:** infra / mq-admin.
- **Archivo:** `infra/apps/compose.yml:190` (servicio `mq-admin`).
- **Paso:** reemplazar la variable singular antigua y su host por `RABBITMQ_MANAGEMENT_URLS=http://rabbit1:15672,http://rabbit2:15672`.
- **Aceptación:** con todo levantado en Docker, `GET http://localhost:8086/api/mq-admin/cluster` devuelve los 2 nodos y `POST /api/mq-admin/topology` responde 200.

#### T3. Bug B1: perfil `local` (H2) mezclado con la URL de Oracle; Oracle no arranca `[ ]` (compose del repo completado; falta el script externo)

- **Servicio:** infra; afecta a catalog, audit, report y shipments.
- **Archivos:**
  - `infra/apps/compose.yml`, líneas `:43-44`, `:102-103`, `:131-132` y `:160-161`, y el servicio `oracle` (`"profiles": ["oracle"]`, línea ~248).
  - `infra/apps/.env.example:2`.
  - `infra/README.md` (sección Oracle).
  - `deploy-aws.bat:618` y `:663` (fuera del repo).
- **Causa:** `application-local.yml` fija `driver-class-name: org.h2.Driver`, mientras que la variable de entorno `SPRING_DATASOURCE_URL` (Oracle) tiene prioridad sobre la URL H2 del perfil. Resultado: `Driver org.h2.Driver claims to not accept jdbcUrl`. Además, Oracle está bajo el perfil de compose `oracle` y el deploy nunca lo levanta.
- **Decisión aplicada en el compose:** Oracle Free en contenedor, con `DB_URL` configurable para el servicio JDBC. H2 sigue reservado al perfil local de ejecución con Gradle.
- **Pasos (opción recomendada: contenedor Oracle en EC2, con URL configurable):**
  1. En los 4 servicios con base de datos: `"SPRING_PROFILES_ACTIVE": "${SPRING_PROFILES_ACTIVE:-prod}"`. No existe `application-prod.yml`; el `application.yml` base ya usa Oracle y Flyway.
  2. Hacer configurable la URL: `"SPRING_DATASOURCE_URL": "${DB_URL:-jdbc:oracle:thin:@//oracle:1521/FREEPDB1}"`.
  3. Quitar `"profiles": ["oracle"]` del servicio `oracle` y agregar a los 4 servicios `"depends_on": {"oracle": {"condition": "service_healthy"}}`. Si se mantiene el perfil, usar `"required": false` y levantar con `--profile oracle`.
  4. `.env.example`: `SPRING_PROFILES_ACTIVE=prod` y una línea comentada `# DB_URL=jdbc:oracle:thin:@//<host-cloud>:1521/<servicio>`.
  5. `deploy-aws.bat`: después de "Redis y Mailpit" (línea ~663), levantar Oracle y esperar a que esté healthy, por ejemplo `dc_apps up -d --wait --wait-timeout 900 oracle` (o con `--profile oracle`).
  6. `infra/README.md`: documentar el perfil `prod`, `DB_URL` y el orden de arranque.
- **Notas:**
  - Las contraseñas de los esquemas vienen de `infra/apps/oracle-init.sql` (`Rutaexpress123`), y el compose pasa `${DB_PASSWORD:-Rutaexpress123}`.
  - Oracle Free necesita unos 2 GB de RAM; el deploy ya advierte que se requieren alrededor de 8 GB en total.
  - Las migraciones `V1` y `V2` de catalog y shipments usan sintaxis Oracle; no se probaron contra Oracle real en los tests (que usan H2 con Flyway desactivado). Vigila el primer arranque.
- **Aceptación:** `docker compose ... up` deja catalog, audit, report y shipments con `/actuator/health` en `UP` sobre Oracle, y Flyway aplica V1 y V2 sin errores.

#### T4. BFF: reenviar las operaciones de administración de RabbitMQ (CÓDIGO, obligatorio) `[ ]` (reabierta: rutas aplicadas, compilación fallida; R1/R2)

- **Servicio:** `ms-rutaexpress-bff`.
- **Archivos:**
  - `ms-rutaexpress-bff/src/main/kotlin/com/rutaexpress/bff/controller/BrokerAdminController.kt` (rutas).
  - `ms-rutaexpress-bff/src/main/kotlin/com/rutaexpress/bff/service/MqAdminCliente.kt` (proxy y propagación de respuestas).
  - `ms-rutaexpress-bff/src/test/kotlin/com/rutaexpress/bff/controller/BrokerAdminSecurityTests.kt` (extender).
  - Opcional: un nuevo `ms-rutaexpress-bff/src/main/kotlin/com/rutaexpress/bff/service/MqAdminCliente.kt`, para poder mockearlo en los tests.
- **Problema:**
  - Hoy solo existe `GET /api/bff/admin/mq/{resource}` con `cluster`, `queues` y `alerts`.
  - La guía exige que el Admin cree y elimine por la cadena API Gateway → BFF (solo Admin).
  - Además, el helper `get(...)` cambia cualquier error por el mensaje genérico "El administrador no pudo completar la consulta", con lo que **se pierde el mensaje 400 claro** que devuelve mq-admin.
- **Pasos:**
  1. Ampliar los GET permitidos: `cluster`, `queues`, `alerts`, `metrics`, `exchanges` y `bindings`.
  2. Agregar estos endpoints (prefijo `/api/bff/admin/mq`, se mantiene `@PreAuthorize("hasRole('ADMIN')")` en la clase):

     | BFF | → mq-admin |
     |---|---|
     | `POST /queues` (body JSON tal cual) | `POST /api/mq-admin/queues` |
     | `DELETE /queues/{name}?force=false` | `DELETE /api/mq-admin/queues/{name}?force=` |
     | `POST /exchanges` | `POST /api/mq-admin/exchanges` |
     | `DELETE /exchanges/{name}?force=false` | `DELETE /api/mq-admin/exchanges/{name}?force=` |
     | `POST /bindings` | `POST /api/mq-admin/bindings` |
     | `DELETE /bindings?source&destination&destinationType=queue&routingKey=&force=false` | `DELETE /api/mq-admin/bindings?...` |
     | `POST /topology` | `POST /api/mq-admin/topology` |
     | `PUT /retention` | `PUT /api/mq-admin/retention` |
     | `POST /exchanges/{name}/messages` (útil para la demo de la DLQ) | `POST /api/mq-admin/exchanges/{name}/messages` |

  3. Reenviar `Authorization: Bearer <jwt.tokenValue>`, como ya hace `get(...)`.
  4. Usar plantillas de URI con variables (`"/api/mq-admin/queues/{name}"`) para que los nombres se codifiquen bien. **La `#` de las routing keys debe viajar como `%23`.**
  5. **Propagar el estado y el cuerpo de mq-admin:** ante una `RestClientResponseException`, devolver `ResponseEntity.status(ex.statusCode).contentType(application/problem+json).body(ex.responseBodyAsString)`. Si es una `RestClientException` sin respuesta, devolver 503.
  6. Mantener el estilo del archivo: Kotlin compacto y `RestClient` con `JdkClientHttpRequestFactory`.
- **Seguridad:** `SecurityConfig.kt` ya restringe `/api/bff/admin/**` a ADMIN y tiene `csrf.disable()`; no hay que cambiarlo.
- **Tests a agregar:**
  - Anónimo `POST /api/bff/admin/mq/queues` → 401.
  - `DESPACHADOR` → 403.
  - `ADMIN` con un mock del cliente → 201.
  - Un error 400 de mq-admin se propaga como 400 con el mismo `detail`.
- **Aceptación:** `.\gradlew.bat test bootJar` pasa en `ms-rutaexpress-bff`. Con el stack levantado, un Admin puede crear y borrar una cola vía `http://localhost:8080/api/bff/admin/mq/queues`, y un nombre vacío devuelve 400 con un mensaje claro.

#### T5. API Gateway en AWS (NO es código; lo hace el usuario en la consola) `[ ]`

- Rutas hacia el BFF para `/api/bff/admin/{proxy+}` con los métodos **GET, POST, PUT y DELETE**, o bien `ANY /api/{proxy+}`.
- La guía menciona `/api/admin/*`. Si se quiere esa ruta literal, crear un alias `/api/admin/{proxy+}` → BFF `/api/bff/admin/{proxy}`.
- Autorizador JWT:
  - issuer `https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09/v2.0`
  - audience `api://f3136620-804c-4b15-b22d-b6fad957937e`
- CORS: métodos `GET, POST, PUT, DELETE, OPTIONS`; cabeceras `Authorization, Content-Type`.
- Confirmar que `bff.atlaa.app` (`frontend/vercel.json`) es el **dominio del API Gateway** y no el BFF directo.

### P1: entrega

#### T6. Actualizar los READMEs `[x]`

| Archivo | Qué cambiar |
|---|---|
| `README.md` (raíz), sección "Mensajería" (~línea 33) | Reemplazar `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` por la topología de la sección 3; explicar direct (comandos) y topic (eventos y notificaciones); ACK manual, DLQ y retención |
| `README.md` (raíz), "Validación y limpieza" | Inventariar 136 métodos de prueba backend (shipments 23, catalog 26, notify 24 con 3 de Docker, mq-admin 26, bff 20, audit 8, report 5, kafka-admin 4) y 3 de frontend |
| `ms-rutaexpress-mq-admin/README.md` | Reescribir "Funcionamiento" (topología nueva y protección con `force`); **borrar** "No existe un endpoint para purgar o borrar colas"; listar todos los endpoints con cuerpos de ejemplo y códigos de error; cambiar `RABBITMQ_MANAGEMENT_URL` por `RABBITMQ_MANAGEMENT_URLS` |
| `ms-rutaexpress-notify/README.md:5-6` | Cola `q.rutaexpress.notification` enlazada a `ex.rutaexpress.events` con `notification.#`; las 3 routing keys; errores no recuperables a la DLQ sin reintentos y recuperables con 3 reintentos; `DeadLetterRegistro`; `RABBIT_PREFETCH` y `RABBIT_DLQ_CONSUMER_ENABLED` |
| `ms-rutaexpress-shipments/README.md:5` | La reserva ahora es asíncrona (`capacity.reserve`/`release` por `ex.rutaexpress.cmd`); eventos `shipment.status.<ESTADO>`; notificaciones `notification.*`; consume `q.rutaexpress.audit` y su DLQ; campo `capacityStatus` (NO_APLICA, PENDIENTE, RESERVADA, RECHAZADA, LIBERADA); 503 si el broker no confirma |
| `ms-rutaexpress-catalog/README.md` | Agregar una sección RabbitMQ: consume `q.rutaexpress.command`, bloqueo pesimista, idempotencia en `tb_capacity_commands` (V2), publica `catalog.capacity.reserved`/`rejected`/`released`, DLQ y variables de entorno |
| `infra/README.md` | `rabbit1`, `RABBITMQ_MANAGEMENT_URLS`, perfil `prod` con Oracle y `DB_URL` (después de T1-T3) |
| `docs/dependencias-microservicios.md:140` | **No tocar**: `docs/` está excluido de los commits según el README de la raíz, salvo que el usuario lo pida |

#### T7. `.gitignore` faltante `[x]`

- Crear `ms-rutaexpress-kafka-admin/.gitignore` copiando `ms-rutaexpress-mq-admin/.gitignore` (Gradle/Kotlin, IDEs, `.env`).
- Al separar los repos (T9), cada repo backend lleva su `.gitignore` de Gradle y el frontend el suyo de Angular (`frontend/.gitignore` ya existe).

#### T8. Integración de los cambios en las ramas y publicación `[x]`

- Commit de código/documentación en `main`: `19e364a feat(ep2): apply ANALISIS_EP2 improvements`.
- Merge de `main` aplicado sin conflictos y publicado en `frontend`, `ms-rutaexpress-bff`, `ms_catalog`, `ms_notify`, `ms-rutaexpress-audit`, `ms-rutaexpress-report`, `ms-rutaexpress-shipments`, `ms-rutaexpress-mq-admin` y `ms-rutaexpress-kafka-admin`.
- `testFrontend` se conserva intacta: contiene otra app React/Vite en la raíz, no el frontend Angular de este plan; el merge de monorepo se canceló tras detectar conflictos.
- Push de `main` y las nueve ramas anteriores completado el 2026-10-17.

#### T9. Repositorios separados `[ ]` (**el usuario debe crear los repos en GitHub**)

- **Obligatorios según la guía:** frontend, BFF, principal (`ms-rutaexpress-shipments`), catálogo (`ms-rutaexpress-catalog`) y administrador RabbitMQ (`ms-rutaexpress-mq-admin`, que puede llamarse `ms-rutaexpress-rabbit-admin`).
- **Recomendados:** notify (consumidor de la EP2) e infra (los compose).
- **Método que conserva el historial:**
  ```bash
  git subtree split --prefix=ms-rutaexpress-mq-admin -b split/mq-admin
  git push https://github.com/<usuario>/ms-rutaexpress-rabbit-admin.git split/mq-admin:main
  ```
- Cada repo necesita su `README.md` y su `.gitignore`. Los enlaces se suben a AVA y se envían por correo al docente.

### P2: mejoras de robustez (no bloquean la demo)

#### T10. Lo que queda del bug B4: reserva huérfana o doble (CÓDIGO) `[ ]` (reabierta: orden aplicado, fallos de idempotencia y compensación reproducidos; R4/R5/R6)

- **Servicios:** `ms-rutaexpress-shipments` y `ms-rutaexpress-catalog`.
- **Archivos:**
  - `ms-rutaexpress-shipments/src/main/kotlin/com/rutaexpress/shipments/servicio/EnvioServicio.kt` (`cambiarEstado`, ~líneas 110-133).
  - `ms-rutaexpress-catalog/src/main/kotlin/com/rutaexpress/catalog/messaging/CapacidadComandoProcesador.kt` (`procesar` / `reservar`).
  - `ms-rutaexpress-catalog/src/main/kotlin/com/rutaexpress/catalog/model/repository/RegistroComandoCapacidadRepository.kt`.
  - Tests: `ms-rutaexpress-shipments/src/test/.../mensajeria/MensajeriaTests.kt` y `ms-rutaexpress-catalog/src/test/.../messaging/CapacidadComandoTests.kt`.
- **Problema (cadena de pasos):**
  1. Al pasar a ACEPTADO, `mensajeria.solicitarReserva(guardado)` se publica y el broker la confirma **antes** que `publicarEstado` y `notificarSegunEstado`.
  2. Si una de esas publicaciones falla, se lanza `MensajeriaNoDisponibleException` y la base hace rollback: el envío queda en CREADO.
  3. Pero el comando ya está en la cola, así que catalog reserva igual.
  4. Al reintentar ACEPTADO se genera otro `commandId` y catalog reserva **otra vez** (la idempotencia es por `commandId`, no por envío).
- **Pasos:**
  1. shipments: mover `mensajeria.solicitarReserva(guardado)` para que sea la **última** publicación de `cambiarEstado` (después de `publicarEstado`, `eventPublisher.publicarCambioEstado` y `notificarSegunEstado`).
  2. catalog: idempotencia **por envío** en RESERVAR.
     - Si ya existe para el mismo `shipmentId` un registro `RESERVAR` con resultado `RESERVADA` sin un `LIBERAR` posterior, devolver ese resultado **sin mover la capacidad** (pero registrando el nuevo `commandId`).
     - Agregar `fun findByShipmentIdOrderByProcesadoEnAsc(shipmentId: String): List<RegistroComandoCapacidad>` en el repositorio; el índice `idx_capacity_commands_shipment` ya existe.
  3. Se mantiene la compensación actual (`AuditoriaEnvioService.kt:90` y `AuditoriaListener.kt:40-48`) para el caso de una cancelación antes de reservar.
- **Tests:**
  - catalog: "un segundo RESERVAR del mismo envío con otro commandId no descuenta cupo".
  - shipments: verificar con `inOrder` que la reserva se publica al final.
- **Aceptación:** los tests de ambos servicios pasan y el cupo no se descuenta dos veces para el mismo envío.

#### T11. Lo que queda del bug B5: el endpoint REST de reserva sin bloqueo (CÓDIGO) `[x]`

- **Servicio:** `ms-rutaexpress-catalog`.
- **Archivo:** `ms-rutaexpress-catalog/src/main/kotlin/com/rutaexpress/catalog/viewmodel/service/CatalogViewModelService.kt`, en `reserveCapacity` (~línea 297) y `releaseCapacity` (~línea 346).
- **Paso:** reemplazar `capacityRepository.findByServiceIdAndZoneAndVehicleType(...)` por `capacityRepository.findLockedByServiceIdAndZoneAndVehicleType(...)`, que ya existe en `FleetCapacityRepository.kt`. La clase ya tiene `@Transactional`, así que el bloqueo vale durante el método.
- **Tests:** revisar `ms-rutaexpress-catalog/src/test/.../viewmodel/service/CatalogViewModelServiceTests.kt:175,190`. Si mockean `findBy...`, actualizar el stub a `findLockedBy...`.
- **Alternativa:** si nadie usa estos endpoints (shipments ya no los usa), se pueden dejar solo para ADMIN. **Preguntar al usuario.**

### P3: limpieza y bugs menores (CÓDIGO)

#### T12. Texto fijo de routing key en shipments `[x]`

- **Archivo:** `ms-rutaexpress-shipments/src/main/kotlin/com/rutaexpress/shipments/mensajeria/AuditoriaListener.kt:40`, donde dice `clave.startsWith("catalog.capacity.")`.
- **Pasos:**
  1. Agregar `val capacityResultPrefix: String = "catalog.capacity."` en `RabbitNombres.Enrutamiento` (`configuracion/RabbitNombres.kt:39-49`).
  2. Agregar `capacity-result-prefix: "catalog.capacity."` en `app.rabbit.routing` de `ms-rutaexpress-shipments/src/main/resources/application.yml`.
  3. Usar `nombres.routing.capacityResultPrefix` en el listener.

#### T13. Bugs menores `[ ]` (a, b, c y e aplicados; build ejecutado, d conserva 11 advertencias CSS)

- **a) Dashboard del BFF frágil.** En `ms-rutaexpress-bff/src/main/kotlin/com/rutaexpress/bff/service/BffGatewayService.kt:107-113`, `buildDashboard` llama a `fetchCatalogServices` y `fetchKpis`, que lanzan `ResponseStatusException` y tumban todo el dashboard. Envolver cada llamada (`runCatching`) y agregar un aviso a `notices` en vez de fallar (`fetchAuditSummary` ya lo hace). Test en `BffDownstreamTests.kt`.
- **b) Propiedad de Kafka.** En `ms-rutaexpress-shipments/src/main/kotlin/com/rutaexpress/shipments/eventos/EnvioEventPublisher.kt:19`, `@Value("\${app.kafka.topics.audit.timeline:audit.timeline}")` debe ser `@Value("\${app.kafka.topics.envios-events:audit.timeline}")`, que es la clave real del yml.
- **c) `/actuator/prometheus` público.**
  - `ms-rutaexpress-shipments/.../configuracion/SeguridadConfig.kt:56-58` y `ms-rutaexpress-bff/.../config/SecurityConfig.kt:60-62`.
  - Sacar prometheus de `permitAll` y exigir `hasRole("ADMIN")`, como hacen notify y mq-admin.
  - Agregar tests con 401 y 403.
- **d) Tailwind.** `npm ci` y `npm run build` ejecutados en la revisión independiente. El build termina correctamente, pero sigue informando 11 reglas descartadas por `Empty sub-selector`; falta corregir esas advertencias.
- **e) nginx.** `frontend/nginx.conf` reenvía `/api/` a `http://bff:8080` y se salta el API Gateway. Documentar que es solo para desarrollo local, o apuntarlo al dominio del Gateway en despliegue.

### Opcional

#### T14. Pantalla de administración de RabbitMQ en el frontend (CÓDIGO) `[x]`

- No lo exigen los documentos: basta con la API REST y el Management UI. Pero mejora la demo.
- **Archivos:**
  - `frontend/src/app/core/bff.service.ts`: agregar `mqMetrics()`, `crearCola()`, `eliminarCola()`, `crearExchange()`, `eliminarExchange()`, `crearBinding()` y `eliminarBinding()` contra `/api/bff/admin/mq/...` (requiere T4).
  - `frontend/src/app/pages/admin-mq/admin-mq.page.ts`, con formularios que muestran el `detail` de los errores 400 y 409.
- Mantener el patrón de interceptor y MSAL existente.

### Preparación de la demo (sin código)

#### T15. Checklist de demo `[ ]`

- **Resiliencia:** todas las colas son classic con líder en `rabbit1` y no se replican. **Apagar `rabbit2`**, no `rabbit1`. Si se apaga `rabbit1`, el clúster sigue vivo gracias a `autoheal`, pero las colas quedan no disponibles. Saber explicarlo; las colas quorum necesitarían 3 nodos para tolerar una caída.
- **Mensajes en la DLQ:** los listeners de DLQ hacen ACK de inmediato, así que el Management UI siempre muestra 0. Para enseñar la métrica, arrancar con `RABBIT_DLQ_CONSUMER_ENABLED=false`, o mostrar los logs y los contadores `rutaexpress.{shipments,catalog,notify}.dlq.messages`.
- **Eliminar:** borrar una cola del núcleo da 409 sin `force=true`. Para la demo, crear `q.demo`, enlazarla y después eliminarla.
- **Error no recuperable en vivo (R9):** `POST /api/mq-admin/exchanges/ex.rutaexpress.events/messages` con `{"routingKey":"notification.email.send","body":"{esto no es json"}`. Notify hace NACK, el mensaje va a `q.rutaexpress.notification.dlq` y aparece el log `Mensaje no entregado...`.
- **Management UI en EC2:** los puertos están ligados a `127.0.0.1` (`infra/mq/compose.yml`). Usar `ssh -i <llave.pem> -L 15672:localhost:15672 <usuario>@<ip>`, o abrir el puerto en el Security Group solo para tu IP.
- **Estado del clúster (R7):** `docker compose -p rutaexpress-mq -f infra/mq/compose.yml exec rabbit1 rabbitmqctl cluster_status`.

---

## 8. Estado por servicio registrado antes de la revisión independiente

> Esta tabla conserva el registro previo de implementación. La sección 13 contiene los resultados ejecutados y las correcciones pendientes; un cambio presente en el código no equivale a un criterio de aceptación superado.

| Servicio | ¿Completo para la EP2? | Archivos pendientes | Tareas |
|---|---|---|---|
| `ms-rutaexpress-bff` | Código aplicado; tests pendientes de ejecutar | Proxy Admin, dashboard tolerante a fallos, protección de Prometheus | T4, T13a, T13c |
| `infra/apps` | Compose actualizado; validación Docker pendiente | Script externo y verificación de Oracle | T1, T2, T3 |
| `infra` (README) | Actualizado | Configuración documentada; queda el script externo | T1, T3, T6 |
| `deploy-aws.bat` (fuera del repo) | Pendiente, sin editar | Líneas 618, 652 y 663 | T1, T3 |
| `ms-rutaexpress-shipments` | Código y README aplicados; tests pendientes | Orden de reserva, routing key, propiedad Kafka y Prometheus protegidos | T6, T10, T12, T13b, T13c |
| `ms-rutaexpress-catalog` | Código y README aplicados; tests pendientes | Idempotencia por envío y bloqueo pesimista REST implementados | T6, T10, T11 |
| `ms-rutaexpress-notify` | README actualizado | Topología, DLQ, reintentos y variables documentados | T6 |
| `ms-rutaexpress-mq-admin` | README actualizado | CRUD, retención, errores y URL de Management documentados | T6 |
| `ms-rutaexpress-kafka-admin` | `.gitignore` agregado | Reglas de Gradle/Kotlin, IDE y `.env` | T7 |
| `frontend` | Vista Admin y documentación nginx aplicadas; build pendiente | Gestión RabbitMQ y ruta de producción por Gateway | T13d, T13e, T14 |
| `ms-rutaexpress-audit`, `ms-rutaexpress-report` | Sin cambios de código | — | — |
| Ramas Git | `main` y nueve ramas actualizadas en GitHub; `testFrontend` separado | Adaptación específica solo si se mantiene la app React/Vite | T8 |
| Repos separados, Gateway y AWS | Pendiente | Crear repos, configurar API Gateway y preparar demo | T5, T9, T15 |

**Hecho:** código, documentación y pruebas nuevas aplicados; commit local en `main`; merges limpios en nueve ramas locales.

**Pendiente:** adaptar `testFrontend` solo si se decide mantener esa app React/Vite; ejecutar validaciones cuando estén disponibles JDK, Node/npm y Docker; editar el script externo, configurar API Gateway y preparar la demo.

---

## 9. Reglas para el agente

1. **No cambiar los nombres de la topología** (sección 3). Si fuera inevitable, cambiarlos en los 4 servicios: los `application.yml` o `.properties` **y** los valores por defecto de cada `RabbitNombres.kt`, más `RabbitTopology.kt`.
2. **No escribir nombres de colas, exchanges ni routing keys a mano** en servicios o listeners: leerlos de `RabbitNombres`.
3. **Respetar las convenciones del código existente:**
   - Kotlin con identificadores en español en shipments, catalog y mq-admin.
   - Comentarios KDoc breves en español.
   - Errores de mq-admin con `ProblemDetail`.
   - `tools.jackson.*` (Jackson 3), no `com.fasterxml`.
   - Tests con `mockito-kotlin` y `@MockitoBean`.
4. **Cada servicio es un proyecto Gradle independiente.** Probar dentro de su carpeta: `.\gradlew.bat test bootJar`. Si Maven Central responde 429, usar un init script fuera del repo con el mirror `https://maven-central.storage-download.googleapis.com/maven2/`.
5. **No hacer commit, push, crear repos ni tocar AWS** sin autorización explícita. Para esta iteración el usuario autorizó y se completó la publicación de `main` y nueve ramas existentes.
6. **No editar `C:\Users\pv-alumno\Downloads\deploy-aws.bat` sin preguntar**: está fuera del repo.
7. **No modificar `docs/`**: está excluido de los commits por convención del proyecto.
8. Después de cada tarea, ejecutar los tests del servicio afectado, marcar el `[x]` en este documento y actualizar el README correspondiente si cambió el comportamiento.
9. Mantener el ACK manual: el ACK solo va al final, cuando el procesamiento terminó, y todo rechazo es `basicNack(tag, false, false)`. No usar `requeue=true`, porque genera bucles.
10. El clúster local del usuario (`rutaexpress-mq-rabbit1-1` y `rutaexpress-mq-rabbit2-1`) puede estar corriendo. No borrar sus volúmenes ni ejecutar `docker system prune`.

---

## 10. Cómo verificar

En la iteración anterior no se ejecutaron las validaciones por las herramientas ausentes del `PATH`. En la revisión independiente se encontró Node en `C:\Program Files\nodejs` y se utilizaron un JDK 21 y Docker Compose portátiles en `%TEMP%`. Se ejecutaron compilaciones y tests de los ocho servicios, el build y los tests Angular y la validación de los tres Compose. Hay fallos de compilación y comportamiento; véanse los resultados exactos en la sección 13. No se dispone de un motor Docker para las pruebas con contenedores reales.

```powershell
# Tests por servicio (repetir en cada carpeta afectada)
cd ms-rutaexpress-bff; .\gradlew.bat test bootJar

# Integración de notify contra un RabbitMQ real (requiere Docker)
cd ms-rutaexpress-notify; $env:RUN_DOCKER_TESTS="true"; .\gradlew.bat test --tests "*RabbitMqIntegrationTests*"

# Validar los compose (desde la raíz)
docker network create rutaexpress   # si no existe
docker compose -f infra/mq/compose.yml config --quiet
docker compose --env-file infra/apps/.env -f infra/apps/compose.yml config --quiet

# Clúster (R7)
docker compose -p rutaexpress-mq -f infra/mq/compose.yml exec rabbit1 rabbitmqctl cluster_status
docker exec rutaexpress-mq-rabbit1-1 getent hosts rabbit1   # debe resolver

# Administrador en local (SECURITY_ENABLED=false). R11, R12 y R13
$h = @{ 'Content-Type' = 'application/json' }
Invoke-RestMethod -Method Post   -Uri http://localhost:8086/api/mq-admin/queues    -Headers $h -Body '{"name":"q.demo","ttlMs":60000,"maxLength":100}'
Invoke-RestMethod -Method Post   -Uri http://localhost:8086/api/mq-admin/exchanges -Headers $h -Body '{"name":"ex.demo","type":"topic"}'
Invoke-RestMethod -Method Post   -Uri http://localhost:8086/api/mq-admin/bindings  -Headers $h -Body '{"source":"ex.demo","destination":"q.demo","routingKey":"demo.#"}'
Invoke-RestMethod -Method Delete -Uri "http://localhost:8086/api/mq-admin/bindings?source=ex.demo&destination=q.demo&routingKey=demo.%23"
Invoke-RestMethod -Method Delete -Uri http://localhost:8086/api/mq-admin/queues/q.demo
Invoke-RestMethod -Method Delete -Uri http://localhost:8086/api/mq-admin/exchanges/ex.demo
Invoke-WebRequest -Method Post   -Uri http://localhost:8086/api/mq-admin/queues    -Headers $h -Body '{"name":""}' -SkipHttpErrorCheck   # 400 con detail

# Métricas
Invoke-RestMethod http://localhost:8086/api/mq-admin/metrics

# Mensaje no recuperable hacia la DLQ (R9)
Invoke-RestMethod -Method Post -Uri http://localhost:8086/api/mq-admin/exchanges/ex.rutaexpress.events/messages -Headers $h -Body '{"routingKey":"notification.email.send","body":"{esto no es json"}'
```

> `-SkipHttpErrorCheck` requiere PowerShell 7. En Windows PowerShell 5.1, usar `try { ... } catch { $_.ErrorDetails.Message }`.

---

## 11. Guía para la defensa: concepto y dónde está en el código

| Concepto | Respuesta corta | Dónde mostrarlo |
|---|---|---|
| direct vs topic | **direct:** la routing key exacta va a una cola. Se usa para **comandos** punto a punto (`capacity.reserve` → catalog). **topic:** patrones con `*` y `#`; un evento puede llegar a varias colas. Se usa para **eventos y notificaciones** (`shipment.#`, `catalog.#`, `notification.#`) | `RabbitTopology.kt` (mq-admin), `RabbitMqConfig.kt` de cada servicio |
| DLQ | Aísla los mensajes que no se pueden procesar, evita bucles infinitos de reintento, conserva el mensaje para inspeccionarlo y no bloquea la cola principal. DLX `ex.rutaexpress.dlx` con una clave por cola | `DeadLetterRegistro.kt` (3 servicios) y el log con `x-death` |
| ACK / NACK | ACK manual solo después de procesar; si el consumidor cae antes, el mensaje vuelve a entregarse (al menos una vez). Por eso hay **idempotencia**: Redis en notify, `tb_capacity_commands` en catalog y `tb_auditoria_envio` en shipments. NACK sin requeue manda a la DLQ | `NotificationConsumerViewModel.kt`, `CapacidadComandoListener.kt`, `AuditoriaListener.kt` |
| Recuperable vs no recuperable | JSON inválido, datos fuera de rango o routing key desconocida → DLQ inmediata. SMTP, base de datos o broker caídos → 3 reintentos con backoff y luego DLQ | Método `reintentar` de cada listener |
| Prefetch (QoS) | Hasta 10 mensajes sin ACK por consumidor. Evita que un consumidor acapare mensajes y sature la memoria, y reparte la carga entre instancias. Con reintentos bloqueantes, un prefetch alto retrasa los mensajes ya entregados | `setPrefetchCount(nombres.consumer.prefetch)`, `RABBIT_PREFETCH` |
| Separar negocio y mensajería | La configuración (`RabbitMqConfig`, `RabbitNombres`) está separada; los listeners solo traducen el mensaje y delegan en servicios (`AuditoriaEnvioService`, `CapacidadComandoProcesador`, `EmailNotificationViewModel`). Se puede probar y cambiar cada parte por separado | Estructura de paquetes `configuracion/` y `mensajeria/` |
| Publisher confirms | shipments espera la confirmación del broker (10 s) con `mandatory`; si no llega, revierte la transacción y responde 503. Un envío nunca cambia sin que su mensaje haya llegado | `MensajeriaPublicador.kt` |
| Métricas | Colas activas, mensajes pendientes (`messages_ready`), sin ACK, consumidores y mensajes en DLQ. Si una DLQ crece, hay errores; si los pendientes crecen, faltan consumidores o hay que escalar | `GET /api/mq-admin/metrics`, gauge `rutaexpress.mq.dlq.depth`, Management UI |
| Retención | Cola de trabajo: TTL de 24 h, 10000 mensajes y reject-publish (el productor recibe NACK y shipments responde 503). DLQ: 7 días, 10000 mensajes y drop-head. Un TTL vencido en una cola de trabajo pasa a la DLQ | `MqAdminService.aplicarRetencion`, `rabbitmqctl list_policies` |
| Escalabilidad y resiliencia | El usuario recibe respuesta inmediata; catalog procesa a su ritmo; mensajes persistentes y colas durables; clúster con `autoheal` | Flujo ACEPTADO → `capacityStatus` PENDIENTE → RESERVADA |

---

## 12. No verificable desde el código (confirmar con el usuario)

- El tenant de Entra ID con usuarios y roles (Admin, Despachador, Cliente y Auditor).
- El API Gateway: autorizador JWT, CORS, rutas y si `bff.atlaa.app` es el Gateway.
- La instancia EC2: RAM (alrededor de 8 GB), Security Groups y Docker.
- Base de datos cloud desplegada y accesible desde EC2 (el compose usa Oracle Free en contenedor y permite `DB_URL`; el despliegue real no se verificó).
- Los pipelines locales sí se ejecutaron en la revisión independiente; el funcionamiento del stack real en AWS y sus integraciones externas sigue sin verificarse.

---

## 13. Revisión independiente ejecutada el 2026-10-17

**Veredicto:** implementación presente y propagada, pero validación funcional incompleta y con errores confirmados. T4 y T10 se reabren. Esta revisión documenta los resultados; las correcciones del código de producción siguen pendientes.

### 13.1 Comprobación de ramas

Se consultó GitHub con `git ls-remote --heads origin` y se comprobó que las nueve ramas del monorepo contienen el commit `19e364a`. El código de aplicación coincide con `main`; las diferencias restantes entre árboles son PDFs históricos.

| Rama | HEAD remoto revisado |
|---|---|
| `main` | `32f23d4` |
| `frontend` | `725e2b0` |
| `ms-rutaexpress-bff` | `e1ce149` |
| `ms_catalog` | `b397059` |
| `ms_notify` | `b1b6c15` |
| `ms-rutaexpress-audit` | `d8436fa` |
| `ms-rutaexpress-report` | `4e5f7bc` |
| `ms-rutaexpress-shipments` | `2a5d72a` |
| `ms-rutaexpress-mq-admin` | `049fca5` |
| `ms-rutaexpress-kafka-admin` | `9586042` |
| `testFrontend` | `abffea3`, sin los cambios: aplicación React/Vite separada, excepción ya documentada |

### 13.2 Resultados sobre el código publicado, sin corregirlo

Se usaron Node 24.22.0, el wrapper Gradle 9.7.1, un JDK 21 temporal y Docker Compose 5.6.0 portátil. No se modificó la instalación del sistema.

| Componente | Resultado medido |
|---|---|
| BFF | `test bootJar` falla en `compileKotlin`: tres arrays con tipos heterogéneos no compilan |
| Shipments | Código principal y `bootJar` generados; `compileTestKotlin` falla en `EnvioServicioTests.kt:32` |
| Catalog | `test bootJar` correcto; 26 tests existentes pasan. Dos pruebas adicionales reproducen fallos de capacidad |
| mq-admin | `test bootJar` correcto; 26 tests pasan |
| Notify | `test bootJar` correcto; 20 tests pasan y 3 de integración quedan omitidos |
| Audit | `test bootJar` correcto; 8 tests pasan |
| Report | `test bootJar` correcto; 5 tests pasan |
| Kafka-admin | `test bootJar` correcto; 4 tests pasan |
| Angular | `npm ci`, build de producción y 3 tests Karma/ChromeHeadless correctos; 11 advertencias CSS persisten |
| Compose apps, mq y kafka | `config --quiet` devuelve 0 en los tres; esto valida configuración, no arranque ni conectividad |

Los recuentos anteriores proceden de los resultados JUnit ejecutados, no de contar apariciones de `@Test` en los fuentes. El inventario previo de 136 métodos no demuestra que esos tests compilen o se ejecuten.

### 13.3 Hallazgos que requieren corrección

**R1 — P1: el BFF no compila (T4).** En `ms-rutaexpress-bff/src/main/kotlin/com/rutaexpress/bff/controller/BrokerAdminController.kt:48,64,83`, `arrayOf(name, force)` y el array de parámetros de bindings mezclan `String` y `Boolean`. Kotlin 2.3.21 rechaza el tipo de intersección inferido para el parámetro reificado. Es necesario explicitar un tipo común, por ejemplo `arrayOf<Any>(...)`. La versión publicada no puede generar su ejecutable.

**R2 — P1: los tests nuevos tienen errores independientes de compilación (T4/T10).**

- BFF: `BrokerAdminSecurityTests.kt:22` importa `SecurityMockMvcRequestPostProcessors` desde `org.springframework.test...`; la clase pertenece a `org.springframework.security.test...`.
- BFF: el test importa `org.mockito.kotlin.any/eq/whenever`, pero `build.gradle.kts` no declara `org.mockito.kotlin:mockito-kotlin`. El compilador confirma referencias no resueltas después de aislar R1.
- Shipments: `EnvioServicioTests.kt:32` no permite inferir el tipo genérico de `saveAndFlush(any())`. En la copia de diagnóstico se hizo explícito `any<Envio>()` y `getArgument<Envio>(0)` para continuar la ejecución.

**R3 — P1: el arranque Oracle con un volumen nuevo no inicializa los esquemas esperados (T3).** `infra/apps/oracle-init.sql` comienza con `CREATE USER ruta_catalog`, sin cambiar a `FREEPDB1`. La imagen ejecuta los `.sql` como SYS conectado a la instancia `FREE`; los usuarios locales deben crearse dentro de la PDB. Falta `ALTER SESSION SET CONTAINER=FREEPDB1;`. Además, las contraseñas de los cuatro usuarios están fijadas en el SQL mientras compose permite sobrescribir `DB_PASSWORD`: un valor distinto rompe las credenciales de las aplicaciones incluso después de corregir la PDB. Este hallazgo se confirmó por inspección del script y de la [documentación oficial de la imagen](https://github.com/gvenzl/oci-oracle-free#initialization-scripts), cuyo ejemplo para `23-slim` cambia de contenedor antes de crear el usuario, y de su [entrypoint](https://github.com/gvenzl/oci-oracle-free/blob/main/container-entrypoint.sh#L222). No se ejecutó Oracle real.

**R4 — P1: el resultado de una compensación no cabe en auditoría (T10).** El comando original usa un UUID de 36 caracteres; catalog genera `UUID:resultado` (46), `AuditoriaListener.kt:45` agrega `compensacion:` (59) y catalog agrega de nuevo `:resultado` (69). Sin embargo, `RegistroAuditoriaEnvio.kt:17` y la migración `V2__capacidad_y_auditoria_rabbit.sql:11` limitan `event_id` a 64. Una prueba de integración con H2 en modo Oracle reproduce `Value too long for column EVENT_ID ... (69)`. El resultado de liberación hace rollback en shipments y terminaría rechazado tras los reintentos. Se requiere un identificador determinista acotado o una migración que amplíe el campo, manteniendo coherencia entre entidad y esquema.

**R5 — P1: una redelivery puede reabrir una reserva y provocar una segunda liberación (T10).** `AuditoriaEnvioService.aplicarResultadoCapacidad` cambia el estado antes de consultar si el evento ya estaba registrado: el `existsById` solo evita duplicar la fila de auditoría. Repetir un resultado `RESERVADA` después de una liberación cambia `LIBERADA` a `RESERVADA` y vuelve a pedir compensación si el envío está cancelado. Por su parte, `CapacidadComandoProcesador.liberar` resta capacidad para cada `commandId` nuevo sin comprobar una reserva activa de ese envío. Las pruebas reproducen la regresión de estado y que una cancelación más su compensación dejan 0 cupos ocupados cuando debían quedar 2 pertenecientes a otro envío. Se necesita idempotencia persistente de liberación por reserva/envío y gestionar la compensación sin reabrir estados ni perder el reintento si falla su publicación.

**R6 — P2: la idempotencia por envío no protege reservas concurrentes (T10).** `CapacidadComandoProcesador.kt:37,70` consulta el historial antes de bloquear la fila de flota en `reservar`. Con dos consumidores o instancias, ambas transacciones pueden observar que no hay reserva, y luego adquirir sucesivamente el bloqueo y descontar cupo dos veces. Una prueba determinista con dos hilos, barrera en la lectura del historial y bloqueo de fila simulado reproduce 6 cupos en vez de 3 para el mismo envío. Se debe serializar la comprobación y modificación por una clave estable de reserva/envío, con garantía en la base de datos; el test previo solo cubre el caso secuencial.

### 13.4 Pruebas adicionales y límites de la evidencia

Las pruebas de diagnóstico están en `%TEMP%\rutaexpress-ep2-review`, fuera del árbol de fuentes versionado:

- Catalog: dos tests adicionales ejecutan el procesador real con dobles de repositorio; fallan con 6 frente a 3 cupos para reservas simultáneas y 0 frente a 2 para dos liberaciones. No sustituyen una prueba de concurrencia contra Oracle real.
- Shipments: en una copia temporal, tras hacer explícitos los tipos del test que bloqueaba la compilación, pasan sus 22 tests existentes; fallan dos tests adicionales por redelivery y longitud del evento de compensación. La prueba de longitud usa el servicio transaccional y la base H2 real, con la columna de 64 caracteres.
- BFF: en una copia temporal, tras corregir únicamente los arrays de R1, el import y la dependencia de R2, pasan sus 19 tests existentes y cuatro pruebas nuevas con un servidor HTTP local. Estas últimas comprueban Bearer y JSON/201, conservación de 400 y `ProblemDetail`, codificación de `#` como `%23`, y 503 ante conexión rechazada. Este resultado no convierte en válido el build del BFF publicado, que sigue fallando.

Quedan sin verificar el stack con RabbitMQ, Redis y Oracle reales, los tres tests Docker de notify, las migraciones Oracle, Entra ID, API Gateway y la demo AWS. `deploy-aws.bat` no se encuentra en la ruta de Downloads indicada en este documento; no se pudo revisar ni modificar.

Los cambios de T1/T2 dentro de compose, el bloqueo REST de T11, el prefijo centralizado de T12, la propiedad Kafka y las restricciones Prometheus están presentes. Las comprobaciones en copias temporales permiten validar dashboard y seguridad después de aislar los errores de compilación. El build de Angular confirma que T14 está incluido, pero los tres tests existentes no cubren específicamente la pantalla Admin.
