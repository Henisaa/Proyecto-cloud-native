# Plan maestro EP2: RutaExpress

Corrección completa, cumplimiento de la evaluación y despliegue 100 % en Docker.

| Dato | Valor |
|---|---|
| Versión del plan | 2 (reemplaza a la versión 1 de este mismo archivo) |
| Fecha | 20-10-2026 |
| Rama base | `frontend_panel_MsgAsincronica` (commit `9bf8c75`), la única que hoy compila |
| Asignatura | DSY1107 – Desarrollo Cloud Native I. La EP2 vale el 30 % de la asignatura (encargo 40 %, presentación 60 %) |
| Documentos de referencia | `EP2_Guia_Evaluacion.docx`, `Explicacion_de_Evaluacion_2.docx`, `ANALISIS_EP2.md` |

Este plan describe **todo** lo necesario para que el proyecto:

1. compile y pase sus pruebas;
2. no tenga los bugs encontrados;
3. cumpla punto por punto la guía y la explicación de la EP2;
4. tenga un frontend completo;
5. se construya, pruebe y ejecute **solo dentro de Docker**, sin instalar nada en el PC.

No trae código. Cada tarea indica qué hacer, cómo comprobar que quedó bien y cómo verificarlo en Docker.

---

## Índice

0. [Reglas y forma de trabajo](#0-reglas-y-forma-de-trabajo)
1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Ramas y punto de partida](#2-ramas-y-punto-de-partida)
3. [Cumplimiento de la evaluación](#3-cumplimiento-de-la-evaluación)
4. [Arquitectura objetivo](#4-arquitectura-objetivo)
5. [Plataforma Docker (nada en el PC)](#5-plataforma-docker-nada-en-el-pc)
6. [Backlog P0: bloqueantes](#6-backlog-p0-bloqueantes)
7. [Backlog P1: bugs funcionales y de seguridad](#7-backlog-p1-bugs-funcionales-y-de-seguridad)
8. [Backlog P2: rendimiento y resiliencia](#8-backlog-p2-rendimiento-y-resiliencia)
9. [Backlog P3: calidad y orden](#9-backlog-p3-calidad-y-orden)
10. [Frontend completo](#10-frontend-completo)
11. [Contratos del sistema](#11-contratos-del-sistema)
12. [Nube: Entra ID, AWS y seguridad](#12-nube-entra-id-aws-y-seguridad)
13. [Pruebas y evidencias](#13-pruebas-y-evidencias)
14. [Repositorios separados](#14-repositorios-separados)
15. [Demo y defensa](#15-demo-y-defensa)
16. [Orden de ejecución](#16-orden-de-ejecución)
17. [Decisiones y riesgos](#17-decisiones-y-riesgos)
18. [Anexos](#18-anexos)

---

## 0. Reglas y forma de trabajo

### 0.1 Nada en el PC

- **En el PC** solo se usan un editor de texto, Git, un navegador y SSH (Windows ya trae un cliente). **No se instalan** JDK, Gradle, Node, Oracle, RabbitMQ, Redis ni Kafka.
- **Compilar y probar** ocurre en **GitHub Actions**: cada push construye las imágenes Docker, y las pruebas se ejecutan *dentro* de esa construcción.
- **Ejecutar el sistema** ocurre en el **EC2**, con Docker Engine y Docker Compose. El EC2 descarga las imágenes ya construidas, así que no compila nada.
- **Depurar o probar sin instalar nada** (opcional): GitHub Codespaces, un contenedor de desarrollo en la nube con JDK 21, Node 22 y Docker (ver 5.8).
- **Docker Desktop** en el PC es una alternativa válida que funciona con el mismo flujo, pero no es necesario.

### 0.2 Definición de terminado

Una tarea está terminada solo si cumple todo esto:

1. El pipeline de CI está en verde: la imagen Docker se construye y **sus pruebas pasan dentro de la construcción**.
2. Si cambia el comportamiento, existe una prueba automatizada nueva que lo cubre.
3. La imagen está publicada en el registro con la etiqueta del commit.
4. Está desplegada en el EC2 y su prueba de aceptación (sección 13) tiene evidencia: captura, log o respuesta HTTP.
5. El README del repositorio refleja el cambio.

### 0.3 Prioridades e identificadores

| Prefijo | Significado |
|---|---|
| **P0-xx** | Bloqueante: sin esto no compila, no se despliega o no se puede demostrar un punto obligatorio |
| **P1-xx** | Bug funcional, de consistencia o de seguridad (backend) |
| **P2-xx** | Rendimiento y resiliencia |
| **P3-xx** | Calidad, orden y documentación |
| **FE-xx** | Frontend completo (sección 10) |
| **AWS-xx** | Nube: Entra ID, EC2, red, API Gateway (sección 12) |
| **R-xx** | Requisito de la evaluación (sección 3) |

Esfuerzo estimado de cada tarea: **S** (menos de 1 h), **M** (de 1 a 4 h), **L** (más de 4 h).

---

## 1. Resumen ejecutivo

### 1.1 Estado verificado

Verificación hecha el 19-10-2026, compilando copias aisladas de las ramas. Los archivos generados ya se borraron del PC; desde ahora toda verificación se hace en Docker.

| Área | Estado | Detalle |
|---|---|---|
| Compilación de la rama base | ✅ | Los 8 servicios compilan y generan su jar. Se ejecutaron 133 pruebas: 130 pasan, 0 fallan y 3 se omiten (integración de notify, necesitan Docker). El frontend compila con 11 advertencias de CSS |
| Compilación de `main` y de las ramas por servicio | ❌ | El BFF no compila (3 errores en `BrokerAdminController.kt:48/64/83`) y el test de shipments tampoco (`EnvioServicioTests.kt:32`) |
| Topología RabbitMQ (3 colas con DLQ, direct + topic, nombres centralizados) | ✅ | No requiere cambios de diseño |
| Consumidores (ACK manual, NACK, reintentos, DLQ con logs) | ✅ con bugs | Reserva y liberación inconsistentes, compensación que se puede perder, idempotencia incompleta (sección 7) |
| Microservicio administrador | ✅ con huecos | Las políticas no se aplican solas, hay validaciones incompletas y la métrica de DLQ siempre marca 0 |
| Seguridad JWT y roles | ✅ con huecos | Endpoints de métricas públicos, detalles internos expuestos, un cliente ve los envíos de todos |
| Docker | ⚠️ | Las imágenes no ejecutan las pruebas ni tienen healthcheck. El frontend no está en el compose. El compose construye desde carpetas vecinas (no sirve con repositorios separados). Todos los puertos están en `127.0.0.1` |
| Nube (EC2, API Gateway, Entra ID) | ❌ | No hay autorizador JWT, rutas ni CORS en el Gateway, ni un despliegue reproducible |
| Frontend | ⚠️ | No muestra el resultado asíncrono (`capacityStatus`). El admin no lista colas, exchanges ni bindings. Menú y acciones no siguen el rol. Los errores se muestran genéricos |
| Repositorios separados | ❌ | Requisito explícito de la entrega |

### 1.2 Bloques de trabajo

| Bloque | Contenido | Secciones |
|---|---|---|
| A. Base | Integrar la rama base y crear la migración V3 en vez de editar V2 | 2.2, P0-01, P0-03 |
| B. Docker total | Imágenes que ejecutan las pruebas, compose completo con frontend y proxy de entrada, CI | 5, P0-08 a P0-10 |
| C. Consistencia de la mensajería | Capacidad, idempotencia, compensación, concurrencia | P1-01 a P1-06 |
| D. Seguridad y errores | Configuración segura, errores claros, notify, DLQ, Kafka, administrador | P1-07 a P1-16 |
| E. Frontend completo | Pantallas por rol, flujo asíncrono visible, administración RabbitMQ completa | 10 |
| F. Nube | Entra ID, EC2, red, TLS, API Gateway, secretos | 12, P0-02, P0-04, P0-05, P0-07, P0-11 |
| G. Entrega | Repositorios separados, README, `.gitignore` | 14, P0-06 |
| H. Validación | Las 14 pruebas obligatorias con evidencia, ensayo de la demo | 13, 15 |

### 1.3 Resultado al terminar

- Los repositorios de la entrega compilan **en Docker** y tienen el CI en verde.
- Las 14 pruebas obligatorias de la guía se ejecutan contra la nube y quedan con evidencia.
- La cadena completa funciona: Angular (MSAL) → API Gateway (JWT + CORS) → BFF → microservicios en EC2 → RabbitMQ (exchanges, colas y DLQ) → consumidores → base de datos.
- El frontend muestra pantallas y acciones según el rol, el resultado asíncrono de cada envío y la administración completa de RabbitMQ.
- Todo el sistema se levanta en el EC2 con Docker Compose y vuelve a levantarse solo tras un reinicio.
- No se instaló nada en el PC.

---

## 2. Ramas y punto de partida

### 2.1 Inventario

| Rama | Commit | Relación con `main` | Contenido | Decisión |
|---|---|---|---|---|
| **`frontend_panel_MsgAsincronica`** | `9bf8c75` | 1 commit adelante | `main` más estos arreglos: compilación del BFF, tests, `FREEPDB1` y `event_id` ampliado **editando V2** | **Base del proyecto.** Se integra en `main` (2.2) |
| `main` | `80efdc2` | — | No compila | Recibe la integración |
| `frontend`, `ms_catalog`, `ms_notify`, `ms-rutaexpress-audit`, `ms-rutaexpress-bff`, `ms-rutaexpress-kafka-admin`, `ms-rutaexpress-mq-admin`, `ms-rutaexpress-report`, `ms-rutaexpress-shipments` | varios | Historias con *merges* propios; el código es idéntico | Monorepo completo igual a `main`: solo difieren `ANALISIS_EP2.md` y el PDF de la EP1 | Archivar después de crear los repositorios separados (P0-06) |
| `testFrontend` | `abffea3` | Historia propia | App React/Vite antigua con pantallas estáticas, sin llamadas HTTP | Archivar. Sirve solo como **referencia visual** (2.3) |

### 2.2 Integrar la rama base (paso a paso)

1. Abrir un Pull Request de `frontend_panel_MsgAsincronica` hacia `main`.
2. **Antes de fusionar, corregir la migración** (ver P0-03):
   - Devolver `V2__capacidad_y_auditoria_rabbit.sql` a su contenido original (`event_id VARCHAR2(64)`).
   - Agregar una migración **V3** que amplíe `event_id` a 100.
   - Dejar la entidad `RegistroAuditoriaEnvio` con largo 100.

   **Por qué:** Flyway guarda una firma de cada migración aplicada. Si una base ya ejecutó la V2 original y luego recibe una V2 distinta, el servicio no arranca. Con V3 funciona tanto en bases nuevas como en las ya migradas.
3. Revisar el README de la rama: conservar los pendientes que lista y **corregir el conteo de pruebas** (son 130 que pasan, no 104).
4. Fusionar cuando el CI esté en verde (P0-10). Mientras el CI no exista, basta la verificación ya hecha (anexo A).
5. Etiquetar el resultado como `ep2-base`.
6. Desde ese momento, todo cambio entra por Pull Request a `main` con el CI en verde.

### 2.3 Qué rescatar de las otras ramas

- **Ramas por servicio:** no tienen código propio. No hay nada que rescatar.
- **`testFrontend`:** tiene diseños estáticos útiles como referencia visual para las nuevas pantallas de Angular (FE-08 y FE-10):
  - vista de capacidad de flota por zona y tipo de vehículo (`FleetCapacityView`);
  - matriz de tarifas (`TariffMatrixView`);
  - monitor de notificaciones (`NotificationMonitorView`);
  - línea de tiempo de auditoría (`AuditTimelineView`).

  Su código no se reutiliza: los datos son fijos y no consulta ninguna API.

### 2.4 Flujo de trabajo desde ahora

- `main` protegida: los cambios entran solo por Pull Request, con el CI en verde y la revisión de la pareja.
- Una rama corta por tarea, con nombre del tipo `p1-03-reservas-por-envio`.
- Se trabaja en el monorepo hasta la fase 6. Ahí se publican los repositorios separados conservando el historial (P0-06), así cada cambio se hace una sola vez.

---

## 3. Cumplimiento de la evaluación

### 3.1 Indicadores de logro

| IL | Peso (encargo / presentación) | Qué pide | Cómo se cumple | Tareas |
|---|---|---|---|---|
| **IL 2.1** Configura un componente que use colas | 35 % / 30 % | Nombres centralizados; beans Queue, Exchange y Binding separados; dos nodos en clúster con Docker Compose en la nube | `RabbitNombres` y `application.yml`; clases `RabbitMqConfig`; `infra/mq` con `rabbit1` y `rabbit2` | P0-04, P0-07, P0-09, AWS-02 a AWS-07 |
| **IL 2.2** Implementa una solución con mensajes asíncronos | 35 % / 40 % | `@RabbitListener` por dominio; ACK y manejo de errores; tres colas con DLQ; direct y topic; logs de las DLQ | Listeners de catalog, notify y shipments; DLX y DLQ; reintentos acotados | P1-01 a P1-06, P1-11, P1-12, FE-04 a FE-07 |
| **IL 2.3** Administra colas | 30 % / 30 % | REST del administrador, `RabbitAdminService`, validación; métricas; retención | `ms-rutaexpress-mq-admin`, que pasa a llamarse `ms-rutaexpress-rabbit-admin` | P0-07, P1-12, P1-14, FE-11 a FE-16 |

### 3.2 Matriz de requisitos

Fuentes: **G§n** es la sección n de la guía. **E** es `Explicacion_de_Evaluacion_2.docx`.

| ID | Requisito | Fuente | Cómo se cumple | Hoy | Tareas | Evidencia |
|---|---|---|---|---|---|---|
| R-01 | Dos nodos RabbitMQ unidos en clúster | G§4, G§8, G§9, E | `infra/mq` con descubrimiento de pares clásico y `autoheal` | ✅ | P0-04 (acceso) | `cluster_status` y el Management UI con los dos nodos |
| R-02 | Clúster en AWS EC2 con Docker Compose, accesible y documentado | G§4, G§8, E | Compose en el EC2, Management UI por túnel SSH | ⚠️ | P0-04, P0-09, AWS-02 a AWS-07 | Servicios del compose en el EC2 |
| R-03 | Tres colas con funciones distintas, cada una con su DLQ | G§4, G§9, E | command, notification y audit, más sus `.dlq` | ✅ | — | Lista de colas en el Management UI |
| R-04 | Exchanges direct y topic con routing keys y bindings | G§4, G§9, E | `ex.rutaexpress.cmd` (direct) y `ex.rutaexpress.events` (topic) | ✅ | Explicación (15.2) | Exchanges y bindings en el Management UI |
| R-05 | Nombres centralizados, sin valores sueltos en el código | G§4, E | `RabbitNombres` y `application.yml` en cada servicio | ✅ | P3-05 (alinear notify) | Archivos de configuración |
| R-06 | Beans Queue, Exchange y Binding en clases de configuración separadas | G§4, E | `RabbitMqConfig` por servicio; servicios y controladores no declaran colas | ✅ | — | Código |
| R-07 | Productores y consumidores con `@RabbitListener` agrupados por dominio | G§4, G§9, E | Capacidad (catalog), notificación (notify), auditoría (shipments) | ✅ | — | Código y logs |
| R-08 | ACK explícito; recuperable con reintento acotado; no recuperable con NACK sin reencolar hacia la DLQ | G§4, G§9, E | Escucha con ACK manual y reintentos en catalog, notify y shipments | ✅ con bugs | P1-02 a P1-05, P1-11 | Pruebas 9 y 10 |
| R-09 | DLQ consumidas y registradas en logs | G§4, G§9, E | `DeadLetterRegistro` en los tres consumidores | ✅ | P1-07e, P1-12 | Log con `x-death` |
| R-10 | Administrador `ms-<caso>-rabbit-admin` que crea colas, exchanges y bindings y elimina colas y exchanges por REST | G§4, G§9, E | `/api/mq-admin/*` | ✅ con otro nombre | P0-06 (nombre), FE-11 a FE-16 | Pruebas 11 y 12 |
| R-11 | Lógica encapsulada en `RabbitAdminService` | G§4, E | Ya existe | ✅ | — | Código |
| R-12 | Validación de entrada con 400 y un mensaje claro | G§4, G§7, G§9, E | `@Valid` y reglas en el servicio | ⚠️ | P1-14 | Prueba 13 |
| R-13 | Políticas de retención y limpieza aplicadas | G§4, G§9, E | Políticas `rutaexpress.trabajo` y `rutaexpress.dlq` | ⚠️ solo bajo demanda | P0-07 | Prueba 14 y la pestaña Policies |
| R-14 | Explicar las métricas: colas activas, pendientes, consumidores y DLQ | G§4, G§8, E | Métricas del administrador, Management UI y FE-11 | ⚠️ DLQ en 0 | P1-12, 11.7, 15.2 | Demo |
| R-15 | Repositorios separados: frontend, BFF, principal, catálogo y administrador | G§8, E | 14 | ❌ | P0-06 | Enlaces enviados a AVA y al docente |
| R-16 | `.gitignore` estricto y README en cada repositorio | G§4, G§8, G§9, E | 14.3 y 14.4 | ⚠️ | P0-06, P3-01 | Repositorios |
| R-17 | Código limpio, que compila y con pruebas básicas | G§8, E | Rama base, imágenes con pruebas y CI | ✅ en la base, ❌ en `main` | P0-01, P0-08, P0-10 | CI en verde |
| R-18 | Conexión a una base de datos en la nube (entidades, repositorios, propiedades) | G§8 | Oracle con Flyway | ⚠️ | P0-02, AWS-07 | Logs de Flyway y datos |
| R-19 | Filtros que validan el JWT del IDaaS | G§8 | Resource server en el BFF y en los servicios | ✅ | P1-07 | Pruebas 1 a 4 |
| R-20 | Frontend con login OAuth 2.0 / OIDC que usa el JWT | G§5, G§8 | MSAL con redirección | ✅ | FE-01, FE-20 | Demo de login |
| R-21 | La EP1 sigue operativa: login, rutas protegidas y roles con diferencias reales | G§9 | Matriz de roles (10.2) | ⚠️ | FE-02, FE-21, P1-13 | Demo con cada rol |
| R-22 | El API Gateway valida el JWT, rechaza lo inválido y enruta todo, incluido `/api/admin/*` | G§5, G§8, G§9 | HTTP API con autorizador JWT | ❌ | P0-05, AWS-05 | Pruebas 1 y 2 y capturas de la configuración |
| R-23 | CORS en el Gateway dentro de la cadena completa | E | Frontend y API en dominios distintos | ❌ | P0-05, FE-19 | *Preflight* visible en las herramientas del navegador |
| R-24 | El BFF vuelve a validar el JWT y no se conecta a la base de datos | G§5, G§9 | Ya se cumple | ✅ | — | Código y configuración |
| R-25 | El BFF autoriza por rol (la administración solo para Admin) | G§5 | `@PreAuthorize` y reglas de seguridad | ✅ | — | Prueba 4 |
| R-26 | Principal y catálogo ejecutan la lógica, persisten y producen y consumen mensajes | G§5, G§6 | shipments y catalog | ✅ con bugs | P1-01 a P1-06 | Pruebas 5, 6 y 8 |
| R-27 | Tenant de Entra ID con usuarios registrados | G§8 | Tenant de la EP1 | ✅ | P0-11, AWS-01 | Capturas |
| R-28 | Backend y frontend desplegados e integrados en la nube | G§8, E | EC2 con compose, Caddy y Gateway | ❌ | P0-04, P0-09, AWS-02 a AWS-07 | Demo |
| R-29 | El flujo completo funciona de extremo a extremo | G§6, G§9, E | 3.3 | ❌ | Todo el plan | Demo |
| R-30 | Dominar los conceptos: direct y topic, DLQ, ACK y NACK, prefetch, separación de lógicas | E | 15.2 | — | Ensayo | Defensa |
| R-31 | Explicar cómo la mensajería mejora la escalabilidad y la resiliencia | G§1, E | 15.3 | — | Ensayo | Defensa |

### 3.3 Flujo completo (sección 6 de la guía)

| # | Paso de la guía | Implementación | Estado | Tareas |
|---|---|---|---|---|
| 1 | El usuario abre la aplicación Angular | Frontend servido por Caddy en `app.<dominio>` | ❌ (hoy en Vercel) | FE-26, AWS-04 |
| 2 | El usuario selecciona Login | Página de login | ✅ | FE-01 |
| 3 | MSAL redirige a Entra ID | Flujo de redirección | ✅ | P0-11 (URI de redirección nueva) |
| 4 | Inicia sesión con un usuario de prueba | Un usuario por rol | ✅ | AWS-01 |
| 5 | MSAL obtiene el access token | Scope `access_as_user` | ✅ | FE-20 |
| 6 | Angular muestra pantallas o acciones según el rol | Menú y acciones por rol | ⚠️ | FE-02, FE-21 |
| 7 | El usuario navega a una ruta protegida | Guards | ✅ | FE-21 (normalización de roles) |
| 8 | Angular llama a través del API Gateway | `apiBaseUrl` apunta al Gateway | ❌ | FE-19, P0-05 |
| 9 | `MsalInterceptor` adjunta `Authorization: Bearer` | Hoy hay dos interceptores | ⚠️ | FE-20 |
| 10 | El Gateway valida el JWT y enruta al BFF | Autorizador JWT | ❌ | P0-05, AWS-05 |
| 11 | El BFF valida issuer, audiencia, firma, vigencia y rol | Resource server | ✅ | — |
| 12 | El BFF llama al microservicio principal | Clientes REST | ✅ sin timeouts | P2-01 |
| 13 | El principal valida la regla, persiste, publica y responde sin esperar | shipments | ✅ con bugs | P1-01, P1-04, P1-13 |
| 14 | RabbitMQ enruta según el tipo de exchange | direct y topic | ✅ | — |
| 15 | Catalog consume el comando, descuenta capacidad, hace ACK y publica el resultado | `CapacidadComandoListener` | ✅ con bugs | P1-03, P1-06 |
| 16 | Si falla: NACK sin reencolar, DLQ y log | DLX, DLQ y `DeadLetterRegistro` | ✅ | P1-12 |
| 17 | Notificación y auditoría procesan y hacen ACK | notify y shipments | ✅ con bugs | P1-02, P1-11 |
| 18 | El Admin crea o elimina y se ve en el Management UI | Administrador | ✅ | FE-12 a FE-14, P0-04 |
| 19 | Se explican las métricas en el Management UI | — | ⚠️ | P1-12, 11.7 |
| 20 | La interfaz muestra el resultado al actor autenticado | Respuesta JSON y estado de capacidad | ⚠️ | FE-04 a FE-07, P1-10 |

### 3.4 Puntos obligatorios de la presentación (Explicación)

| Punto | Cómo se muestra | Tareas |
|---|---|---|
| Dos nodos y su clúster | `cluster_status` en `rabbit1` y la vista Overview → Nodes | P0-04 |
| docker-compose levantando RabbitMQ en la nube | Servicios del compose de RabbitMQ en el EC2 | P0-09 |
| Tres colas con sus DLQ funcionando | Vista Queues, más un error provocado que llega a la DLQ | FE-16 |
| Direct y topic, y cuándo usar cada uno | Vista Exchanges, más la explicación 15.2 | — |
| Microservicios que escriben y leen, y logs de las DLQ | Logs de catalog, notify y shipments | P1-07e |
| Administrador creando y eliminando | FE-12 a FE-14 y el Management UI | P0-07 |
| Métricas | FE-11 y el Management UI | P1-12 |
| Retención y limpieza | Vista Policies y la prueba 14 | P0-07 |
| Cadena completa Angular → Gateway (JWT + CORS) → EC2 → RabbitMQ → consumidores → base de datos | Recorrido de la demo (15.1) | Todo el plan |

---

## 4. Arquitectura objetivo

### 4.1 Componentes

| Componente | Tecnología | Repositorio | Imagen | Puerto interno | Expuesto a | Depende de |
|---|---|---|---|---|---|---|
| Frontend | Angular 19 + MSAL, servido con nginx | `frontend-rutaexpress` | `ghcr.io/<org>/frontend-rutaexpress` | 8080 | Internet, vía Caddy en `app.<dominio>` | API Gateway, Entra ID |
| Proxy de entrada | Caddy (TLS automático) | `rutaexpress-infra` | imagen oficial | 80/443 | Internet | Frontend, BFF |
| API Gateway | AWS HTTP API | consola de AWS | — | — | Internet en `api.<dominio>` | Caddy (`origen.<dominio>`) |
| BFF | Spring Boot 4 | `ms-rutaexpress-bff` | `ghcr.io/<org>/ms-rutaexpress-bff` | 8080 | Solo Caddy | Servicios internos |
| Principal (envíos) | Spring Boot 4 + Oracle | `ms-rutaexpress-shipments` | `ghcr.io/<org>/ms-rutaexpress-shipments` | 8085 | Red interna | Oracle, RabbitMQ, catalog, Kafka |
| Catálogo | Spring Boot 4 + Oracle | `ms-rutaexpress-catalog` | `ghcr.io/<org>/ms-rutaexpress-catalog` | 8081 | Red interna | Oracle, RabbitMQ, Kafka |
| Notificaciones | Spring Boot 4 + Redis + SMTP | `ms-rutaexpress-notify` | `ghcr.io/<org>/ms-rutaexpress-notify` | 8082 | Red interna | RabbitMQ, Redis, Mailpit |
| Administrador RabbitMQ | Spring Boot 4 | `ms-rutaexpress-rabbit-admin` | `ghcr.io/<org>/ms-rutaexpress-rabbit-admin` | 8086 | Red interna | RabbitMQ (AMQP y gestión) |
| Auditoría (EP1) | Spring Boot 4 + Kafka + Oracle | `ms-rutaexpress-audit` | `ghcr.io/<org>/ms-rutaexpress-audit` | 8083 | Red interna | Kafka, Oracle |
| Reportes (EP1) | Spring Boot 4 + Kafka + Oracle | `ms-rutaexpress-report` | `ghcr.io/<org>/ms-rutaexpress-report` | 8084 | Red interna | Kafka, Oracle |
| Administrador Kafka (EP1) | Spring Boot 4 | `ms-rutaexpress-kafka-admin` | `ghcr.io/<org>/ms-rutaexpress-kafka-admin` | 8087 | Red interna | Kafka |
| RabbitMQ | `rabbitmq:4-management`, 2 nodos | `rutaexpress-infra` | oficial | 5672, 15672 | Red interna; Management UI solo por túnel | — |
| Oracle | `gvenzl/oracle-free:23-slim` | `rutaexpress-infra` | oficial | 1521 | Red interna | — |
| Redis | `redis:8-alpine` | `rutaexpress-infra` | oficial | 6379 | Red interna | — |
| Mailpit | `axllent/mailpit` | `rutaexpress-infra` | oficial | 1025, 8025 | Red interna; interfaz web por túnel | — |
| Kafka (EP1) | 3 brokers con 3 ZooKeeper, más kafka-ui | `rutaexpress-infra` | oficiales | 9092 | Red interna; kafka-ui por túnel | — |

Los puertos internos son los de `infra/apps/compose.yml`. Confirmarlos al reescribir el compose (P0-09).

### 4.2 Exposición y red

- **Desde Internet solo se alcanzan:**
  - Caddy en los puertos 80 y 443;
  - el API Gateway, que es un servicio de AWS.
- **Caddy:**
  - En `app.<dominio>` sirve el frontend.
  - En `origen.<dominio>` reenvía al BFF **solo** si la petición trae la cabecera secreta que agrega el API Gateway. Así nadie se salta el Gateway.
- **Todo lo demás** queda dentro de la red de Docker. Los paneles de gestión (Management UI de RabbitMQ, Mailpit, kafka-ui) quedan en `127.0.0.1` y se abren con un túnel SSH durante la demo.
- **Alternativa más profesional y más cara:** API Gateway con VPC Link hacia un balanceador privado, sin `origen.<dominio>` público (17.2, D5).

### 4.3 Flujos de negocio

| Flujo | Secuencia |
|---|---|
| Crear envío | UI → Gateway → BFF → shipments: valida datos, consulta capacidad por REST a catalog, guarda en estado CREADO y publica `shipment.status.*`. Responde 201 sin esperar a ningún consumidor |
| Aceptar envío | UI → … → shipments: valida la transición, guarda ACEPTADO con capacidad PENDIENTE y publica el estado, la notificación y al final `capacity.reserve` (direct). Responde 200 |
| Reservar capacidad | Catalog consume `capacity.reserve`, reserva de forma idempotente por envío (P1-03), hace ACK y publica `catalog.capacity.reserved` o `rejected` (topic). Shipments lo consume desde la cola de auditoría y actualiza `capacityStatus` (P1-02). La UI lo muestra al refrescar (FE-07) |
| Avanzar | Solo si la capacidad está RESERVADA (P1-01): EN_BODEGA genera ticket de bodega; EN_RUTA genera etiqueta y correo; ENTREGADO genera correo |
| Cancelar | Shipments guarda CANCELADO y publica el estado, las notificaciones y al final `capacity.release` con un id determinista (P1-04). Si el resultado RESERVADA llega después de cancelar, se compensa con reconciliación (P1-05) |
| Notificar | Notify consume `notification.*`, deduplica en Redis (P1-11), envía correos y PDF a Mailpit y hace ACK |
| Auditar | Shipments consume `shipment.#` y `catalog.#` en `q.rutaexpress.audit` y registra en `tb_auditoria_envio`. Además, la auditoría de la EP1 consume Kafka |
| Error no recuperable | NACK sin reencolar → DLX → `.dlq` → `DeadLetterRegistro` deja el log con `x-death` y el contador sube (P1-12) |
| Administrar | UI admin → Gateway (`/api/admin/…`) → BFF (rol Admin) → administrador → API de gestión de RabbitMQ → el cambio se ve en el Management UI y en la propia UI |

---

## 5. Plataforma Docker (nada en el PC)

### 5.1 Herramientas y cuentas

| Dónde | Qué se necesita |
|---|---|
| PC | Editor de texto, Git, navegador y SSH. Nada más |
| GitHub | Repositorios, GitHub Actions (CI), GitHub Container Registry (GHCR) para las imágenes y, opcionalmente, Codespaces |
| AWS | EC2, API Gateway (HTTP API), ACM (certificado del dominio del Gateway) y DNS: Route 53 o el proveedor del dominio |
| Microsoft Entra ID | El tenant de la EP1 (`bc307149-…`) y el registro de aplicación (`f3136620-…`) |
| Dominio | Un dominio propio (por ejemplo, `atlaa.app`, que ya aparece en `vercel.json`) para `app.`, `api.` y `origen.` |
| EC2 | Solo Docker Engine y el plugin Compose v2, instalados con *user data* (AWS-02) |

### 5.2 Imagen estándar de cada microservicio (parte de P0-08)

**Situación actual:** los 8 `Dockerfile` son iguales. Compilan con `bootJar` (**no ejecutan las pruebas**), copian todo antes de descargar dependencias (sin caché) y toman el jar con el comodín `*-SNAPSHOT.jar`. No tienen healthcheck ni límites de memoria de la JVM.

**Estándar que se pide** (igual en todos los servicios):

1. **Etapa de dependencias**
   - Base: JDK 21 (`eclipse-temurin:21-jdk`).
   - Copia **solo** el *wrapper* de Gradle y los archivos de build (`settings`, `build.gradle.kts`, `gradle/`).
   - Descarga las dependencias. Esa capa se reutiliza mientras no cambien los archivos de build.
2. **Etapa de construcción y pruebas**
   - Copia `src/` y ejecuta el build completo: compilación, pruebas unitarias y de integración ligera, y `bootJar`. **Si una prueba falla, la imagen no se construye.**
   - Usa la caché de BuildKit para la carpeta de Gradle, así el CI no descarga todo cada vez.
3. **Etapa de reportes (opcional)**
   - Permite extraer `build/reports` y `build/test-results` como artefacto del CI.
4. **Etapa de ejecución**
   - Base: JRE 21 (`eclipse-temurin:21-jre`) con el usuario no root que ya existe (uid 10001).
   - Copia el jar con un **nombre fijo**, definido en la configuración de `bootJar`, en vez del comodín.
   - Valores por defecto en `JAVA_TOOL_OPTIONS`:
     - porcentaje máximo de RAM al 65 % (`MaxRAMPercentage`). Deja espacio para metaspace, hilos y buffers: con 75 %, un contenedor de 512 MB puede morir por falta de memoria;
     - salir ante falta de memoria (`ExitOnOutOfMemoryError`);
     - codificación UTF-8.
   - Zona horaria UTC.
   - `EXPOSE` del puerto.
   - **Healthcheck** contra `/actuator/health/readiness`. Hay que activar las *probes* de Actuator y agregar `curl` o `wget` si la imagen base no los trae.
   - Metadatos OCI: repositorio, commit y versión.
5. **`.dockerignore`** (ya existe en cada servicio): confirmar que excluye `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `*.iml`, `out/`, `.env` y `*.log`.
6. **Pruebas que necesitan Docker** (Testcontainers con RabbitMQ, Oracle o Redis) **no** van dentro del build de la imagen. Se ejecutan en un job aparte del CI (5.5).

**Criterio de aceptación:**
- La imagen no se construye si una prueba falla.
- La imagen final pesa menos de unos 350 MB.
- El contenedor queda *healthy* en menos de 90 s con sus dependencias arriba.
- Corre como usuario no root.

### 5.3 Imagen del frontend (parte de P0-08)

1. **Etapa de build**
   - **Node 22 LTS.** Angular 19 soporta Node 18, 20 y 22; no usar Node 24.
   - Instalación limpia (`npm ci`) y build de producción.
2. **Etapa de pruebas**
   - Misma base con **Chromium** instalado.
   - Configurar Karma con un lanzador sin interfaz y sin *sandbox* (obligatorio dentro de contenedores), y ejecutarlo sin modo *watch*.
   - Si una prueba falla, la imagen no se construye.
3. **Etapa de ejecución:** nginx en su variante sin privilegios (puerto 8080), solo con la carpeta `browser` del build. Configuración:
   - *fallback* de SPA hacia `index.html`;
   - caché larga para los archivos con hash, y **sin caché** para `index.html` y `config.json`;
   - compresión gzip;
   - cabeceras de seguridad:
     - CSP que permita `login.microsoftonline.com` y el dominio del API;
     - `frame-ancestors 'none'`;
     - `nosniff`;
     - `Referrer-Policy` y `Permissions-Policy`.
   - **Sin el proxy `/api` hacia `bff:8080`** en producción: el frontend llama al API Gateway. El proxy actual se puede dejar solo en una configuración de desarrollo.
   - Un script de arranque que genera `config.json` a partir de variables de entorno (FE-19). Así la misma imagen sirve en cualquier entorno.
4. **Healthcheck** HTTP a la raíz.

**Criterio de aceptación:** la imagen sirve la SPA y `config.json` con los valores del entorno; las pruebas corren dentro de la construcción; las cabeceras de seguridad aparecen en las respuestas.

### 5.4 Compose de ejecución (P0-09)

**Situación actual:**
- `infra/apps/compose.yml` construye las imágenes desde carpetas vecinas (`../../ms-*`), lo que no funciona con repositorios separados.
- No incluye el frontend ni un proxy de entrada.
- Los servicios de aplicación no tienen healthcheck ni política de reinicio.
- Todos los puertos están en `127.0.0.1`.

**Estructura objetivo** (en el repositorio `rutaexpress-infra`):

| Archivo o perfil | Servicios | Notas |
|---|---|---|
| Red | `rutaexpress` | Se crea una vez. La comparten todos los compose |
| Compose `mq` | `rabbit1`, `rabbit2` | Cookie de Erlang como **archivo o secreto** (no como variable); archivo de definiciones con usuarios, vhost y políticas cargado al arrancar; `queue_leader_locator` balanceado; marca de memoria (`vm_memory_high_watermark`) ajustada al límite del contenedor, para que la alarma de memoria no bloquee a los productores; healthcheck; reinicio; Management UI solo en `127.0.0.1` |
| Compose `apps`: datos | `oracle`, `redis`, `mailpit` | Init de Oracle con contraseñas por variable (P0-02); Redis sin puerto publicado; interfaz de Mailpit solo en `127.0.0.1` |
| Compose `apps`: EP2 | BFF, shipments, catalog, notify, administrador RabbitMQ | `image` desde GHCR con la etiqueta en una variable; `env_file` común más uno específico; healthcheck; `depends_on` con condición *healthy* (Oracle, RabbitMQ, Redis); `restart: unless-stopped`; límites de memoria; logs con rotación; **ningún puerto publicado** |
| Compose `apps`, perfil `ep1` | audit, report, kafka-admin | Igual que los de la EP2. Se activan junto con Kafka (17.2, D1) |
| Compose `kafka` (EP1) | 3 ZooKeeper, 3 brokers y kafka-ui | **Heap fijado bajo el límite**: `KAFKA_HEAP_OPTS` en los brokers (por defecto usan 1 GB) y `ZK_SERVER_HEAP` en ZooKeeper (por defecto 1000 MB). Si no, el contenedor muere por falta de memoria. kafka-ui solo en `127.0.0.1` |
| Compose `edge` | `frontend`, `caddy` | Caddy publica 80 y 443, obtiene los certificados de `app.<dominio>` y `origen.<dominio>`, sirve el frontend y reenvía a `bff:8080` solo con la cabecera secreta |
| Archivo de desarrollo (*override*) | Cambia `image` por `build` y publica puertos en `127.0.0.1` | Solo para Docker Desktop o Codespaces; nunca en el EC2 |

**Límites de memoria recomendados** (EC2 de 16 GiB con todo encendido). Son topes, no reservas: el consumo real en la demo queda por debajo.

| Servicio | Límite |
|---|---|
| BFF, shipments, catalog, notify, audit, report | 768 MB cada uno |
| Administrador RabbitMQ, kafka-admin | 512 MB cada uno |
| Oracle Free | 3 GB (la base usa hasta 2 GB de RAM, más sus procesos) |
| `rabbit1` y `rabbit2` | 768 MB cada uno |
| Redis, Mailpit, Caddy, frontend | 128 MB cada uno |
| Brokers Kafka | 768 MB cada uno (heap de 512 MB) |
| ZooKeeper | 384 MB cada uno (heap de 256 MB) |
| kafka-ui | 512 MB |
| **Suma de topes** | **≈ 14,4 GB** con todo; **≈ 8,5 GB** sin Kafka ni el perfil `ep1` |

**Variables:**
- `.env.example` (ya existe en `infra/apps`, con valores de desarrollo) se amplía con todas las variables de 11.6.
- El `.env` real existe solo en el EC2, con permisos 600.

**Orden de arranque:**
1. Red.
2. RabbitMQ y esperar que ambos nodos estén *healthy*.
3. Datos.
4. Kafka y el perfil `ep1`, si aplica.
5. Aplicaciones.
6. Entrada.

**Criterio de aceptación:**
- En un EC2 limpio, solo con Docker y el `.env`, todo queda *healthy*.
- Después de reiniciar el EC2, todo vuelve a levantarse solo.
- Desde Internet solo responden los puertos 80 y 443.

### 5.5 Integración continua (P0-10)

Mientras se trabaja en el monorepo hay **un solo workflow**, con una matriz de servicios. Al separar los repositorios (P0-06), cada uno recibe su propio workflow equivalente.

| Paso | Qué hace |
|---|---|
| Disparadores | Pull Request y push a `main`; etiquetas `v*`. En el monorepo, filtros por carpeta: cada servicio se construye solo si cambió su carpeta. Ahorra minutos: un repositorio privado tiene una cuota mensual (2.000 minutos en el plan gratuito); los públicos no tienen límite |
| Construir y probar | Construye la imagen hasta la etapa de pruebas (5.2 y 5.3). Si falla una prueba, el job falla. Sube los reportes de prueba como artefacto |
| Integración con Docker | Para shipments, catalog, notify y el administrador RabbitMQ: ejecuta las pruebas de Testcontainers dentro de un contenedor con JDK 21 que monta el socket de Docker del runner (Testcontainers detecta que corre dentro de un contenedor y levanta los suyos al lado). Incluye RabbitMQ real, Oracle Free para validar **todas las migraciones Flyway** y Redis |
| Publicar | En `main`: publica en GHCR con las etiquetas del commit corto y `main`. En una etiqueta `vX.Y.Z`: publica también esa versión |
| Caché | Caché de BuildKit guardada en la caché de GitHub Actions |
| Protección | `main` exige el CI en verde y la revisión de la pareja |
| Infraestructura | Valida la sintaxis de los compose. Un workflow manual opcional de despliegue entra por SSH al EC2 y descarga e inicia la versión indicada. Si no, el despliegue se hace a mano (5.7) |

**Imágenes:** paquetes de GHCR públicos (lo más simple; las imágenes no contienen secretos) o privados con un token de solo lectura en el EC2.

### 5.6 Pruebas dentro de Docker

| Tipo | Dónde corre | Cuándo |
|---|---|---|
| Unitarias y de *slice* (controladores, servicios, seguridad) | Etapa de pruebas de cada imagen | Cada Pull Request y push |
| Integración con broker, base de datos y Redis reales (Testcontainers) | Job del CI con Docker | Cada Pull Request y push |
| Migraciones Flyway sobre Oracle | Job de integración con `gvenzl/oracle-free` | Cada Pull Request que toque `db/migration` |
| Frontend (Karma con Chromium) | Etapa de pruebas de la imagen del frontend | Cada Pull Request y push |
| Aceptación HTTP (pruebas 1 a 6, 11 a 13) | Contenedor `newman` (Postman CLI) con una colección versionada en el repositorio de infraestructura, contra la URL del Gateway | Antes de la entrega y de la demo |
| Aceptación de mensajería (pruebas 7 a 10 y 14) | Management UI por túnel, herramientas dentro de los contenedores de RabbitMQ y logs del compose | Antes de la entrega y de la demo |

### 5.7 Operación diaria en el EC2

| Tarea | Cómo |
|---|---|
| Levantar | Seguir el orden de 5.4 con `docker compose up -d` en cada archivo, siempre con el `.env` |
| Estado | `docker compose ps` (todo debe estar *healthy*) y `cluster_status` en `rabbit1` |
| Logs | Logs del servicio por compose. La rotación está configurada (máximo 3 archivos de 10 MB por contenedor) |
| Actualizar | Cambiar la etiqueta de imagen en el `.env`, descargar la nueva versión y recrear solo ese servicio |
| Volver atrás | Volver a la etiqueta anterior y recrear |
| Reiniciar un servicio | Reiniciarlo por compose. Las dependencias se esperan por healthcheck |
| Apagar | Bajar los compose sin borrar volúmenes. Después de la evaluación, borrar volúmenes e imágenes (5.9) |
| Acceder a los paneles | Túnel SSH a 15672 (RabbitMQ), 8025 (Mailpit) y 8090 (kafka-ui) |

### 5.8 Desarrollo sin instalar nada

- **GitHub Codespaces** con un *devcontainer* en el repositorio, que trae JDK 21, Node 22, Docker dentro del contenedor y el plugin de Compose. Desde ahí se compila, se prueba y se levanta el compose de desarrollo, todo en la nube.
- **Alternativa:** Docker Desktop en el PC, con el archivo de desarrollo (*override*) del compose.
- **En ningún caso** se instala JDK, Node ni Gradle directamente en el PC.

### 5.9 Limpieza

- Después de la evaluación: bajar todos los compose **con** volúmenes, borrar las imágenes del EC2 y detener o terminar la instancia (AWS-08).
- En GHCR: conservar solo las etiquetas de las versiones entregadas.

---

## 6. Backlog P0: bloqueantes

### P0-01. Integrar la rama base (M)

- **Estado:** compilación verificada en la rama (anexo A).
- **Pasos:** los de 2.2.
- **Pendiente:** que el CI (P0-10) confirme lo mismo dentro de Docker y que la integración incluya la corrección de V2/V3 (P0-03).
- **Criterio de aceptación:** `main` = rama base + V3; CI en verde para los 8 servicios y el frontend.

### P0-02. Oracle: usuarios en el PDB y contraseñas desacopladas (M)

**Dónde:**
- `infra/apps/oracle-init.sql`: ya incluye el cambio al PDB `FREEPDB1` en la rama base.
- `infra/apps/compose.yml`.
- Los `application.yml` de cada servicio.

**Problema:**
- La contraseña de los usuarios de aplicación está fija en el SQL ("Rutaexpress123"), y `DB_PASSWORD` se usa a la vez para el administrador de Oracle y para las aplicaciones. Si se cambia una, las otras dejan de coincidir.
- Los valores por defecto no coinciden entre servicios:
  - shipments: `Ruta1234`;
  - catalog: `c##rutaexpress` / `secret`;
  - audit y report: `SYSTEM` / `password`.

**Solución:**
1. Reemplazar el SQL por un script de inicialización en shell (la imagen también ejecuta `.sh`) que cambie al PDB y cree los cuatro usuarios leyendo sus contraseñas de variables de entorno: una por esquema, o una común de aplicación separada de la del administrador.
2. Entregar a cada servicio solo su usuario y su contraseña.
3. Quitar las contraseñas por defecto de los `application.yml` fuera del perfil local, para que un servicio sin credenciales falle de inmediato con un mensaje claro.
4. Documentar que el script solo corre con el volumen vacío.
5. Si la base de la demo es gestionada (Autonomous o RDS), crear los usuarios allí con el mismo script, sin el cambio de contenedor, y apuntar `SPRING_DATASOURCE_URL` a esa base. En el compose actual se define con la variable `DB_URL` (17.2, D3).

**Criterio de aceptación:** con el volumen vacío, Oracle crea los cuatro usuarios, los cuatro servicios quedan *healthy* y Flyway aplica todas sus migraciones.

### P0-03. `event_id` de la compensación sin editar V2 (S)

**Problema:** el resultado de una compensación tiene un `eventId` de 69 caracteres:

| Paso | Formato | Largo |
|---|---|---|
| Reserva original | `commandId` (UUID) | 36 |
| Resultado de la reserva | 36 + `":resultado"` | 46 |
| `commandId` de compensación | `"compensacion:"` + 46 | 59 |
| Resultado de la compensación | 59 + `":resultado"` | **69** |

La columna admite 64. La rama base lo resuelve **editando V2**, lo que rompe Flyway en cualquier base que ya tenía V2.

**Solución:**
1. Restaurar V2 a su contenido original y crear **V3** con la ampliación a 100.
2. Además, cambiar el `commandId` de compensación por un **UUID basado en nombre** (versión 3 o 5) derivado del `eventId` original. Siempre mide 36 caracteres y es el mismo en cada reintento.

**Criterio de aceptación:**
- El job de migraciones sobre Oracle (5.6) pasa en dos casos: base vacía, y base con la V2 original ya aplicada.
- La compensación completa termina con el envío en CANCELADO + LIBERADA.

### P0-04. EC2: exposición, reinicio y memoria (M)

**Problema:**
- Todo está en `127.0.0.1`, así que el Gateway no llega al BFF.
- No hay política de reinicio.
- No hay límites de memoria. Cada JVM puede tomar hasta un cuarto de la RAM del host como heap.

**Solución:** aplicar 4.2 y 5.4:
- solo Caddy publica 80 y 443;
- el BFF se alcanza únicamente a través de `origen.<dominio>` con la cabecera secreta;
- paneles por túnel SSH;
- `restart`, healthchecks, límites de memoria, rotación de logs y *swap* de 4 GB en el host.

Confirmar qué es hoy `bff.atlaa.app`: si es un proxy instalado en el host, reemplazarlo por Caddy dentro del compose.

**Criterio de aceptación:** el de 5.4.

### P0-05. API Gateway (M)

Ver el detalle en AWS-05. En resumen:
- autorizador JWT con el emisor exacto de los tokens y las dos formas de la audiencia (el client ID y `api://…`);
- rutas por método (GET, POST, PUT y DELETE) para `/api/bff/{proxy+}` y `/api/admin/{proxy+}`. **No usar `ANY`**: también captura el *preflight* `OPTIONS`, que llega sin token, y el autorizador lo rechaza con 401;
- CORS configurado en el Gateway;
- integración HTTP hacia `https://origen.<dominio>` con la cabecera secreta;
- dominio `api.<dominio>`;
- *throttling* y logs de acceso.

Para `/api/admin/*` hay dos opciones:
- **Recomendada:** reescribir la ruta en la integración del Gateway (`/api/admin/{proxy+}` → `/api/bff/admin/{proxy}`). No requiere código y el BFF sigue exigiendo el rol Admin.
- Que el BFF exponga el alias `/api/admin/**` con la misma regla de rol (P1-16).

En ambos casos, el frontend llama a las rutas de administración con el prefijo `/api/admin/`, para que la demo pase por esa ruta del Gateway.

**Criterio de aceptación:** las pruebas 1 a 4 (13.2) contra `api.<dominio>`, y el *preflight* CORS respondido por el Gateway sin pasar por el autorizador.

### P0-06. Repositorios separados (M)

Ver la sección 14.

**Criterio de aceptación:** los 5 repositorios exigidos (más notify y la infraestructura) se pueden clonar en limpio y tienen el CI en verde, su README y su `.gitignore`. Los enlaces están enviados.

### P0-07. Políticas de retención aplicadas solas (S/M)

**Problema:** las políticas solo se crean al llamar a `POST /api/mq-admin/topology` o a `PUT …/retention`. En un clúster nuevo no existen.

**Solución:**
1. El administrador aplica la topología y las políticas al terminar de arrancar, de forma idempotente y con reintentos mientras RabbitMQ no responda.
2. Además, el archivo de definiciones de RabbitMQ (5.4) incluye las mismas políticas, así existen desde que arranca el clúster.
3. La UI muestra las políticas vigentes y permite volver a aplicarlas (FE-15).

**Criterio de aceptación:** después de un arranque en limpio, el Management UI (Admin → Policies) muestra `rutaexpress.trabajo` y `rutaexpress.dlq`, y cada cola indica su política efectiva.

### P0-08. Imágenes Docker con pruebas, caché y healthcheck (M)

Aplicar 5.2 a los 8 servicios y 5.3 al frontend.

**Criterio de aceptación:** los de 5.2 y 5.3.

### P0-09. Compose completo para el EC2 (M)

Aplicar 5.4.

**Criterio de aceptación:** el de 5.4.

### P0-10. Integración continua (M)

Aplicar 5.5. Es bloqueante porque, sin nada instalado en el PC, el CI es el lugar donde se compila y se prueba.

**Criterio de aceptación:** el check en verde en `main` y las imágenes en GHCR con la etiqueta del commit.

### P0-11. Entra ID listo para el despliegue (S)

Ver AWS-01.
- URI de redirección del nuevo dominio del frontend;
- roles de aplicación asignados;
- un usuario de prueba por rol, más uno sin rol;
- tokens v2;
- *claims* opcionales `email` y `preferred_username`.

**Criterio de aceptación:** cada usuario de prueba inicia sesión y ve exactamente lo que indica la matriz 10.2.

---

## 7. Backlog P1: bugs funcionales y de seguridad

### P1-01. Un envío avanza sin capacidad confirmada (M). shipments

**Dónde:** `EnvioServicio.kt:97-134`, `EstadoEnvio.kt:15-21` y `AuditoriaEnvioService.kt:66-69`.

**Problema:** con capacidad RECHAZADA, o todavía PENDIENTE, el envío puede pasar a EN_BODEGA, EN_RUTA y ENTREGADO. La máquina de estados no mira la capacidad.

**Solución:**
1. Cuando catalog está integrado, exigir capacidad RESERVADA para pasar de ACEPTADO a EN_BODEGA. Si no se cumple, responder 409 con un mensaje claro: "capacidad pendiente; intente en unos segundos" o "capacidad rechazada: cancele o reintente la reserva".
2. Agregar la acción "reintentar reserva" (solo con RECHAZADA): vuelve a PENDIENTE y publica un nuevo `capacity.reserve`. La alternativa mínima es permitir solo CANCELADO.
3. Notificar al cliente cuando la reserva sea rechazada.
4. El backend devuelve `allowedTransitions` en cada respuesta, ya calculadas con la regla de capacidad.

**Criterio de aceptación:**
- Tests unitarios con cada estado de capacidad.
- Prueba de punta a punta: capacidad agotada → RECHAZADA → EN_BODEGA da 409.

### P1-02. El resultado de capacidad no es idempotente (M). shipments

**Dónde:** `AuditoriaEnvioService.kt:52-91`.

**Problema:**
- Cambia el envío **antes** de comprobar el `eventId`.
- Una reentrega puede devolver un envío LIBERADA a RESERVADA y volver a disparar la compensación.
- Un resultado atrasado puede pisar a uno más nuevo.

**Solución:**
1. Comprobar primero el `eventId`. Si ya está registrado, terminar sin cambios.
2. Aceptar solo transiciones de capacidad válidas:
   - PENDIENTE → RESERVADA o RECHAZADA;
   - RESERVADA → LIBERADA (también LIBERACION_PENDIENTE → LIBERADA, ver P1-05).

   Lo demás se registra en auditoría y se ignora sin error.
3. Guardar en el envío el `commandId` de la última reserva y descartar los resultados de comandos anteriores.
4. Si la clave del registro de auditoría ya existe porque otro consumidor procesó lo mismo a la vez, tratarlo como duplicado y hacer ACK.

**Criterio de aceptación:** pruebas con un resultado duplicado y con resultados fuera de orden; como máximo una compensación.

### P1-03. Doble reserva y doble liberación (L). catalog

**Dónde:** `CapacidadComandoProcesador.kt:31-67`, `:70-76` y `:111-138`.

**Problema:**
- `reservaActiva` lee el historial **sin bloqueo**, así que dos comandos concurrentes reservan dos veces.
- `liberar` descuenta para cualquier `commandId` nuevo, aunque no haya reserva activa.
- La línea que se libera se elige con el `vehicleType` que envía shipments, no con la línea realmente reservada.

**Solución:**
1. Crear la tabla de reservas por envío (migración V3 de catalog) con:
   - `shipment_id` único;
   - id de la línea usada y número de paquetes;
   - estado (ACTIVA o LIBERADA);
   - `commandId` de reserva y de liberación;
   - fechas.
2. **Reservar:** insertar la reserva en la misma transacción que incrementa el contador. Si la clave única ya existe, devolver la reserva existente.
3. **Liberar:** bloquear la reserva del envío.
   - Si está ACTIVA: descontar en la línea guardada y marcarla LIBERADA.
   - Si no: responder LIBERADA sin tocar contadores.
4. Conservar `tb_capacity_commands` como registro de idempotencia por comando.

**Criterio de aceptación:**
- Dos hilos reservan el mismo envío → una sola reserva.
- Dos liberaciones → un solo descuento.
- Se libera siempre la línea que se reservó.

### P1-04. Orden de publicación al cancelar (S). shipments

**Dónde:** `EnvioServicio.kt:122-124` y `MensajeriaPublicador.kt:63`.

**Problema:** al cancelar, `capacity.release` se publica **primero** y con un UUID aleatorio. Si después falla otra publicación, la transacción se revierte pero la liberación ya salió, y un reintento genera otra.

**Solución:**
1. Publicarla al final.
2. Usar un `commandId` determinista: un UUID por nombre a partir de "liberar:" más el id del envío.

**Criterio de aceptación:** un fallo simulado después de publicar no deja liberaciones huérfanas, y un reintento no descuenta dos veces.

### P1-05. La compensación se puede perder (M). shipments

**Dónde:** `AuditoriaListener.kt:45-46`.

**Problema:** la liberación de compensación se publica **fuera** de la transacción. Si falla después de los reintentos, el mensaje va a la DLQ, nunca se compensa y el cupo queda tomado.

**Solución:**
1. Agregar el estado de capacidad LIBERACION_PENDIENTE y guardarlo en la misma transacción que aplica el resultado. Cabe justo en la columna (`VARCHAR2(20)`) y la columna no tiene restricción CHECK, así que no necesita migración: basta con el enum del backend y las insignias del frontend.
2. **Tarea de reconciliación** cada 1 a 5 minutos:
   - reenvía la liberación (con el id determinista) de los envíos CANCELADO con capacidad RESERVADA o LIBERACION_PENDIENTE;
   - reenvía la reserva, o marca el envío para revisión, si lleva más de N minutos en PENDIENTE.
3. Mediano plazo: patrón **outbox** para todos los mensajes salientes de shipments, es decir, una tabla de mensajes escrita en la misma transacción y un publicador que la vacía.

**Criterio de aceptación:** con RabbitMQ detenido durante la compensación, al volver el broker el cupo queda liberado.

### P1-06. Carreras por falta de bloqueo optimista (M). shipments y catalog

**Dónde:**
- `Envio.kt`: sin `@Version` y con `@DynamicUpdate`.
- `FleetCapacity.kt`: sin `@Version`.
- `CatalogViewModelService.updateCapacity`.

**Problema:**
- Dos PUT simultáneos sobre el mismo envío publican dos reservas.
- Una cancelación concurrente con un RESERVADA deja el envío en CANCELADO + RESERVADA sin compensación.
- La edición administrativa de capacidad puede pisar el contador de reservados y no valida que el máximo sea mayor o igual a lo reservado.

**Solución:**
1. Columna `version` en ambas tablas (nuevas migraciones).
2. En el controlador, el conflicto de versión responde 409 ("El envío cambió; recargue").
3. En los listeners, el conflicto es un error recuperable.
4. En catalog, `updateCapacity` lee con bloqueo o usa la versión, y valida el máximo.
5. En el frontend, los botones se deshabilitan mientras hay una petición en curso (FE-07).

**Criterio de aceptación:** una prueba con dos transacciones concurrentes, en la que una recibe 409.

### P1-07. Configuración de seguridad (S cada una). Varios servicios

**a) Shipments responde 403 a todo si se desactiva la seguridad con el perfil `prod`.**
- `MethodSecurityConfig.kt:6-7` activa la seguridad por método salvo que `app.method-security.enabled=false`, y esa propiedad solo está en `application-local.yml:18-19`.
- **Solución:** atarla a `SECURITY_ENABLED`, como ya hace el BFF.

**b) `/actuator/prometheus` es público en catalog** (`SecurityConfig.kt:53-57`).
- **Solución:** restringirlo al rol ADMIN, como en el resto de servicios.

**c) `show-details: always` en shipments, catalog, BFF, audit y report.**
- **Solución:** usar `when-authorized`. Solo las *probes* de liveness y readiness quedan públicas, y solo dentro de la red de Docker.

**d) Los errores 500 devuelven `ex.message`** (`ManejadorGlobalExcepciones.kt:59-61`).
- **Solución:** mensaje genérico más un id de correlación; el detalle va solo al log.

**e) Los logs de las DLQ guardan el cuerpo completo** (hasta 2000 caracteres), con correos y direcciones.
- **Solución:** registrar ids, routing key, motivo (`x-death`) y un extracto enmascarado.

**f) `/api/bff/status` expone URLs internas** (`BffGatewayService.kt:260-263`).
- **Solución:** devolver solo nombre y estado (UP/DOWN) según el health real, y solo para Admin.

**Criterio de aceptación:**
- Pruebas de seguridad por endpoint.
- Ninguna respuesta pública trae detalles internos.

### P1-08. Un cliente ve los envíos de todos (M). shipments y BFF

**Dónde:** `EnvioControlador.kt` y `Envio.kt:76` (`customerEmail` nunca se asigna).

**Solución:**
1. Al crear, guardar como dueño el `oid` del token (el identificador estable del usuario en Entra ID) en una columna nueva. Llenar `customerEmail` con su correo (`email` o `preferred_username`) solo para mostrarlo: Microsoft advierte que `preferred_username` puede cambiar y no debe usarse para autorizar.
2. Si el usuario solo tiene el rol CLIENTE, filtrar el listado y el detalle por ese `oid`. Si el envío no es suyo, responder 404. Los envíos antiguos, sin dueño, solo los ven los roles internos.
3. Agregar un índice por dueño (migración V4, 11.5).

**Criterio de aceptación:** dos clientes no ven los envíos del otro.

### P1-09. Validación y códigos HTTP (M). shipments y catalog

**Dónde:** `DTOs.kt:11-45`, `ManejadorGlobalExcepciones.kt`, `CatalogoCliente.kt:88-94` y el `GlobalExceptionHandler` de catalog.

**Problema:**
- **Faltan límites** que la tabla sí tiene:
  - largo de texto: nombre 120, direcciones 250, email 120, teléfono 30;
  - `packagesCount`: máximo 99.999;
  - precisión: peso (10,2), volumen (10,3), precio (12,2) y no negativo.

  Un valor fuera de rango termina en un 500 de Oracle.
- **`zone` es texto libre**, aunque solo hay cuatro valores válidos.
- **`@Email` acepta la cadena vacía.**
- **Excepciones del cliente que terminan en 500:** JSON mal formado, tipo de parámetro inválido, parámetro faltante, ruta inexistente, método no permitido, violación de integridad.
- **`CatalogoCliente` convierte en 503** cualquier 400 que devuelve catalog.

**Solución:**
1. Agregar las restricciones de tamaño, rango y dígitos, y una enumeración para la zona.
2. Convertir las cadenas vacías en nulo.
3. Usar ProblemDetail para todas las excepciones estándar de Spring MVC.
4. Traducir el 400 de catalog a un 400 con su mensaje.

**Criterio de aceptación:** tests de controlador para cada caso; ninguno responde 500.

### P1-10. El BFF pierde el mensaje de error (S). BFF

**Dónde:** `BffGatewayService.kt:249-257`.

**Problema:** la razón de `ResponseStatusException` no llega al cliente, porque Spring Boot no incluye el mensaje por defecto y el BFF no tiene `@RestControllerAdvice`.

**Solución:**
1. Reenviar el cuerpo y el código del servicio tal cual, como ya hace `MqAdminCliente`.
2. Unificar el formato de error en ProblemDetail (11.4).

**Criterio de aceptación:** una transición inválida muestra en la UI el mensaje exacto del backend.

### P1-11. Notificaciones perdidas si el consumidor se cae a mitad del proceso (S). notify

**Dónde:** `RedisIdempotencyStore.kt:13-14` y `:28-30`, y `ValidatingNotificationProcessor.kt:17`.

**Problema:** si el contenedor muere entre la marca "processing" y el `complete`, la reentrega se toma como duplicado, se confirma con ACK y se pierde.

**Solución:** cuando `claim` falla, leer el valor guardado.
- Si es "completed", es un duplicado real: ACK.
- Si es "processing", es un error recuperable: reintentar con espera.

**Criterio de aceptación:** una prueba con una marca "processing" previa, en la que el mensaje no se confirma como duplicado.

### P1-12. La DLQ siempre marca 0 (M). Administrador y consumidores

**Problema:** los listeners de DLQ confirman al instante, así que la profundidad es casi siempre 0. La métrica "Mensajes en DLQ" y las alertas no muestran los fallos.

**Solución:**
1. **Administrador:** agregar "llegados a la DLQ" (acumulado y tasa reciente) junto a "en la DLQ ahora". Se obtiene de las estadísticas de mensajes de cada DLQ en la API de gestión (los confirmados por su consumidor). Esas estadísticas se reinician con el nodo.
2. **Consumidores:** exponer en Prometheus el contador que ya incrementa `DeadLetterRegistro` (por ejemplo, `rutaexpress.shipments.dlq.messages`). Es la fuente más fiable del acumulado.
3. **Demo:**
   1. Desactivar el consumo de las DLQ con `RABBIT_DLQ_CONSUMER_ENABLED=false` (lo soportan shipments, catalog y notify).
   2. Mostrar cómo se acumulan los mensajes.
   3. Reactivarlo y mostrar el log con `x-death`.
4. **Opcional:** una cola de estacionamiento (`.parking`) para reprocesar a mano.

**Criterio de aceptación:** al provocar un error no recuperable, el acumulado sube aunque la DLQ quede vacía.

### P1-13. Kafka bloquea las peticiones y deja eventos fantasma (M). shipments y catalog

**Dónde:**
- `EnvioEventPublisher.kt:37-48`.
- `application.yml` de catalog: usa `spring.kafka.enabled`, que no lo lee nadie.

**Problema:**
- Con Kafka caído, cada envío espera hasta 60 s **por tópico** (son dos), así que el Gateway corta y la transacción queda abierta.
- El evento se envía antes del commit, así que si la transacción se revierte quedan eventos de cambios que nunca ocurrieron.

**Solución:**
1. Unificar la bandera en `app.kafka.enabled` en todos los servicios.
2. Bajar los tiempos de espera. **Ojo con esta regla del productor de Kafka:** `delivery.timeout.ms` debe ser mayor o igual que `linger.ms` más `request.timeout.ms`; si no, el productor no arranca. Valores coherentes:
   - `max.block.ms`: 3 s;
   - `request.timeout.ms`: 5 s;
   - `delivery.timeout.ms`: 10 s.
3. Publicar **después del commit**, con un evento transaccional o con outbox.
4. Decidir si Kafka sigue encendido en la demo (17.2, D1).

**Criterio de aceptación:** con Kafka apagado, crear y aceptar un envío responde en menos de 1 s.

### P1-14. Validaciones del administrador (S/M). Administrador RabbitMQ

**Lo que ya está bien:** nombre obligatorio, de hasta 255 caracteres, con formato válido y sin el prefijo `amq.`; rangos de TTL, máximo y expiración; valores de `overflow`; routing keys de hasta 255 caracteres; 409 si el recurso ya existe o es parte de la topología protegida; 404 si no existe.

**Problema:**
- Acepta una cola con un DLX que no existe: `RabbitAdminService.crearCola` solo valida el formato del nombre. RabbitMQ lo permite y los mensajes muertos se pierden en silencio.
- Un JSON mal formado, un parámetro de tipo incorrecto o uno obligatorio que falta (por ejemplo, `source` al borrar un binding) no tienen manejador en `AdminErrors`, así que responden un 400 genérico, sin mensaje claro.
- `reconciliar` (POST `/topology`) declara sin pasar por `enRabbit`. Si una cola base ya existe con otros argumentos, el 406 PRECONDITION_FAILED termina como 503 "Broker no disponible".
- Al publicar, una confirmación negativa del broker (por ejemplo, cola llena con `reject-publish`) también se informa como 503 "Broker no disponible", lo que confunde en la prueba de retención.

**Solución:**
1. Verificar que el DLX exista; si no, 400 con un mensaje claro.
2. Agregar a `AdminErrors` manejadores ProblemDetail para cuerpo ilegible, tipo inválido y parámetro faltante.
3. Pasar las declaraciones de `reconciliar` por `enRabbit`, para que el 406 se traduzca a 409 con una explicación.
4. Informar la confirmación negativa como 409 "rechazado por la política de la cola", con el motivo que da el broker.

**Criterio de aceptación:** un test por cada caso.

### P1-15. El actor aparece como un identificador ilegible (S). shipments

**Dónde:** `SeguridadConfig.kt:133`: el nombre del usuario autenticado es el `sub` del token.

**Problema:** el `sub` es un identificador opaco, así que los eventos y la auditoría muestran un GUID en vez de un usuario.

**Solución:** usar `preferred_username` (o `name`) como nombre, y guardar el `oid` aparte para la propiedad de los envíos (P1-08).

**Criterio de aceptación:** la línea de tiempo muestra el correo o el nombre del actor.

### P1-16. API del backend para el frontend completo (M). BFF, shipments, catalog y audit

Las rutas nuevas están en 11.2. En resumen:
- paginación de envíos;
- resumen de conteos;
- detalle con línea de tiempo unificada;
- reintentar reserva;
- consulta y edición de capacidades;
- auditoría paginada;
- alias `/api/admin/**` en el BFF, solo si no se reescribe la ruta en el Gateway (P0-05).

Cada ruta nueva lleva su prueba de seguridad por rol.

**Criterio de aceptación:** el frontend (sección 10) no necesita ninguna llamada fuera de 11.2.

---

## 8. Backlog P2: rendimiento y resiliencia

| ID | Dónde | Problema | Solución | Esfuerzo |
|---|---|---|---|---|
| **P2-01** | BFF `config/RestClientConfig.kt:18-30`; shipments `CatalogoConfig` y `CatalogoCliente` | Clientes HTTP sin timeouts | Conexión de 2 s y lectura de 5 a 10 s, siempre por debajo de los 30 s del Gateway. Reintentar solo los GET | S |
| **P2-02** | `EnvioServicio.kt:40-59` | Llamada HTTP con la transacción abierta | Consultar la capacidad antes de abrir la transacción | S |
| **P2-03** | shipments `GET /api/shipments`; audit `GET /api/audit` (`AuditoriaControlador.kt:17-20`); BFF `BffGatewayService.kt:177-180`; dashboard del frontend | Listados sin paginar | Paginación (`page` y `size`, máximo 100) y conteos agregados (P1-16) | M |
| **P2-04** | `MensajeriaPublicador.kt` | Hasta 3 publicaciones por transición, cada una esperando su confirmación (hasta 10 s) dentro de la transacción | Bajar la espera a 2–3 s y esperar las confirmaciones juntas. Después, outbox | M |
| **P2-05** | `AuditoriaListener.kt:79` y sus equivalentes | Los reintentos con `Thread.sleep` retienen los mensajes del prefetch | Prefetch de 1 a 5 en la cola de comandos y más consumidores. Opcional: reintento diferido con una cola de espera con TTL | S |
| **P2-06** | catalog `CapacidadComandoProcesador.kt:157-160` | La caché se invalida antes del commit | Invalidar después del commit | S |
| **P2-07** | `CapacidadComandoProcesador.kt:141-148` | Bloqueos uno a uno y sin orden fijo | Una sola consulta que bloquee las candidatas ordenadas por id | S |
| **P2-08** | catalog `CatalogServiceRepository` | El *fetch join* de dos colecciones genera un producto cartesiano | Consultas separadas o carga por lotes | S |
| **P2-09** | `infra/mq/rabbitmq.conf` | Las colas clásicas no se replican, así que si cae su nodo dejan de estar disponibles | `queue_leader_locator` balanceado, ambas direcciones en `RABBITMQ_ADDRESSES` y explicarlo (15.2). Las colas quorum necesitan 3 nodos para tolerar una caída | S |
| **P2-10** | `infra/mq/compose.yml` | Cookie de Erlang por variable de entorno (obsoleto en RabbitMQ 4) | Archivo o secreto de Docker, sin versionarlo | S |
| **P2-11** | report `ReporteControlador.kt:26` | La caché usa `range` libre como clave | Validar contra un conjunto fijo; lo demás responde 400 | S |
| **P2-12** | `KafkaErrorConfig` de audit y report; kafka-admin | Si el tópico de origen gana particiones y el DLT no, la publicación falla. La compactación de `audit.timeline` no sirve porque sus claves son únicas | Ampliar los DLT junto con el origen y revisar la limpieza. Prioridad baja | S |
| **P2-13** | Logs de Docker en el EC2 | Sin rotación llenan el disco | Rotación por contenedor (5.7) y alerta de disco | S |

---

## 9. Backlog P3: calidad y orden

- **P3-01.** `.gitignore` de cada repositorio según 14.3. Confirmar que no se versiona `build/`, `.gradle/`, `.kotlin/`, `.env` ni `*.log`.
- **P3-02.** Mover `application-test.yml` de catalog a `src/test/resources`.
- **P3-03.** BFF:
  - usar o quitar las cachés `dashboard` y `catalog-services`;
  - la regla `/api/bff/catalog/manage/**` gana sus rutas en P1-16.
- **P3-04.** Quitar la propiedad obsoleta `management.metrics.export.prometheus.enabled`. Si hace falta, usar la actual (`management.prometheus.metrics.export.enabled`).
- **P3-05.** notify:
  - renombrar el paquete `com.rutaexpress.demo` a `com.rutaexpress.notify`;
  - alinear las propiedades de reintento con `app.rabbit.consumer.*`.
- **P3-06.** Compose: quitar las variables sobrantes, como `CATALOGO_ENABLED` y `RABBIT_ENABLED` en audit y report, y las de Kafka en el administrador RabbitMQ.
- **P3-07.** La capacidad diaria nunca se reinicia. Definir la regla: un reinicio diario programado, o capacidad por fecha.
- **P3-08.** Renombrar el administrador a `ms-rutaexpress-rabbit-admin` (servicio, imagen y repositorio), según la guía.
- **P3-09.** Resumir la respuesta `/cluster` del administrador: nombre, en ejecución, memoria, disco, alarmas y tiempo activo.
- **P3-10.** Pruebas que faltan:
  - **administrador:** eliminar un exchange, crear un binding, codificar `#` y `*`, publicar sin ruta, métricas;
  - **catalog:** concurrencia;
  - **shipments:** compensación completa, idempotencia y migraciones sobre Oracle.
- **P3-11.** Archivar `testFrontend` y las ramas por servicio.
- **P3-12.** Cada README según la plantilla de 14.4.

---

## 10. Frontend completo

### 10.1 Principios

- **Lo que se ve depende del rol.** Menú, rutas y botones usan **una sola** definición de roles, la misma que aplica el backend, con la misma normalización.
- **El trabajo asíncrono es visible.** Cada envío muestra el estado de su capacidad y la UI se actualiza sola mientras hay operaciones pendientes.
- **Los errores son claros.** El mensaje del backend llega al usuario en lenguaje simple, nunca como un texto técnico genérico.
- **Hay un estado para cada situación:** carga, vacío, error y éxito, en cada vista.
- **Accesible y adaptable:** etiquetas, foco, contraste, regiones `aria-live` para los resultados asíncronos y tablas que funcionan en el teléfono.
- **Configuración por entorno sin recompilar** (FE-19).

### 10.2 Matriz de rol por pantalla

| Pantalla o acción | Admin | Despachador | Cliente | Auditor | Sin rol |
|---|---|---|---|---|---|
| Login y sesión | ✅ | ✅ | ✅ | ✅ | ✅ (llega a "sin permisos") |
| Dashboard | Completo: KPIs, broker, servicios | Operación | Sus envíos | Conteos y auditoría reciente | ❌ |
| Envíos: ver | Todos | Todos | **Solo propios** | Todos, solo lectura | ❌ |
| Envíos: crear | ✅ | ✅ | ✅ (propio) | ❌ | ❌ |
| Envíos: cambiar estado y cancelar | ✅ | ✅ | ❌ | ❌ | ❌ |
| Envíos: reintentar reserva | ✅ | ✅ | ❌ | ❌ | ❌ |
| Envío: detalle | ✅ | ✅ | Solo propio | ✅ | ❌ |
| Envío: línea de tiempo (auditoría) | ✅ | ✅ | ❌ | ✅ | ❌ |
| Catálogo: servicios | ✅ | ✅ | ✅ | ❌ | ❌ |
| Catálogo: capacidades | Ver y editar | Ver | ❌ | ❌ | ❌ |
| Reportes | ✅ | ❌ | ❌ | ❌ | ❌ |
| Auditoría | ✅ | ❌ | ❌ | ✅ | ❌ |
| Administración RabbitMQ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Kafka (EP1, opcional) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Diagnóstico de sesión | ✅ | ❌ | ❌ | ❌ | ❌ |

Esta matriz reemplaza las reglas actuales, que no coinciden:
- el BFF permite el catálogo a CLIENTE y la ruta del frontend no;
- el menú muestra opciones a quien no las puede usar.

Hay que aplicarla igual en las reglas del BFF, en las rutas y en el menú.

### 10.3 Pantallas

**FE-01. Login y sesión**
- Botón de inicio con redirección de MSAL.
- Errores claros cuando falta consentimiento o el usuario cancela.
- Cierre de sesión.
- Si no se puede renovar el token, mostrar "sesión expirada" y volver al login.
- En el menú: nombre del usuario y sus roles.

**FE-02. Diseño general y navegación por rol**
- El menú se arma a partir de los roles declarados en cada ruta.
- Menú adaptable al teléfono.
- Distintivo de entorno (desarrollo o producción).
- Página **403 "sin permisos"** en vez de redirigir al dashboard en silencio.
- Página 404.

**FE-03. Dashboard por rol**
- Usa el resumen agregado del BFF (11.2), no la lista completa de envíos.
- Muestra:
  - conteos por estado y por capacidad;
  - los últimos 5 envíos;
  - para Admin: KPIs, resumen del broker (colas con listos, sin confirmar, consumidores y DLQ ahora y acumulado) y estado de los servicios (solo nombre y UP/DOWN).
- Se refresca cada 30 s, con opción de pausa.
- Cada bloque muestra su propio error. Si un servicio no responde (por ejemplo, report con el perfil `ep1` apagado), el BFF devuelve ese bloque como "no disponible" en vez de fallar entero.

**FE-04. Envíos: lista**
- Paginación en el servidor, de 20 en 20.
- Filtros: estado, capacidad, rango de fechas y código de seguimiento.
- Columnas: seguimiento, servicio, zona, **estado**, **capacidad** (insignia), destinatario, destino, carga y última actualización.
- Acciones según el rol (10.2).
- Si hay envíos en PENDIENTE, se refresca solo cada 3 s hasta que se resuelvan, por un máximo de 30 s.
- Al cambiar un filtro se cancela la petición anterior.
- Estados de carga, vacío y error.

**FE-05. Envíos: crear**
- Formulario con validaciones iguales a las del backend (P1-09):
  - obligatorios;
  - formato del email;
  - números positivos;
  - largos máximos;
  - hasta 99.999 paquetes.
- El servicio se elige de una lista con los servicios activos del catálogo.
- La zona se elige entre los cuatro valores válidos.
- Teléfono opcional. Los campos vacíos se envían como nulos.
- Si no hay capacidad, se muestra el mensaje 409 del backend.
- Al crear: aviso de éxito y la fila resaltada.

**FE-06. Envíos: detalle** (ruta propia o panel lateral)
- Todos los datos del envío.
- Estado de la capacidad con su explicación.
- Transiciones permitidas, que vienen del backend en `allowedTransitions`.
- Cancelar, con confirmación.
- Reintentar reserva cuando está RECHAZADA.
- Línea de tiempo unificada, solo para los roles de 10.2, con la auditoría de RabbitMQ y la de Kafka (FE-10). Si audit está apagado, muestra solo la de RabbitMQ.

**FE-07. Envíos: cambiar estado**
- Solo se ofrecen las transiciones permitidas.
- El botón se deshabilita mientras la petición está en curso.
- Cancelar pide confirmación.
- Un 409 muestra el mensaje del backend.
- Después de aceptar se muestra "Reserva en proceso" y se consulta hasta tener RESERVADA o RECHAZADA, con aviso por `aria-live`.

**FE-08. Catálogo**
- Tabla de servicios: código, nombre, categoría, estado, precio base y horas estimadas.
- Detalle por servicio con capacidades (zona × vehículo: máximo diario, reservados, disponibles, peso y volumen máximos, activo) y tarifas.
- **Admin** edita el máximo diario y el estado activo, con la validación "máximo ≥ reservados". **Despachador** solo ve.
- Botón "Actualizar" para ver cómo bajan los cupos disponibles después de una reserva. Es la evidencia visible de que el consumidor de catalog funciona.
- Referencia visual: `FleetCapacityView` y `TariffMatrixView` de `testFrontend`.

**FE-09. Reportes (Admin)**
- Selector de rango con valores fijos.
- Tarjetas de KPIs: activos por estado, tiempo promedio de entrega y entregados.
- Servicios más usados.
- Estados de carga y error.

**FE-10. Auditoría (Admin y Auditor)**
- Búsqueda por código de seguimiento o por id de envío.
- Línea de tiempo unificada con las dos fuentes (auditoría de Kafka de la EP1 y registro de RabbitMQ de shipments). Cada evento muestra fuente, tipo, routing key, actor legible y fecha.
- Validar que el campo no esté vacío.
- Corregir el pie, que hoy nombra tópicos inexistentes. Los reales son `shipments.events` y `audit.timeline`.

**FE-11. Administración RabbitMQ: resumen**
- Nodos del clúster: nombre, en ejecución, memoria y alarmas.
- Tarjetas de métricas:
  - colas activas;
  - mensajes listos;
  - sin confirmar;
  - consumidores;
  - colas DLQ;
  - en la DLQ ahora;
  - **llegados a la DLQ** (acumulado y última hora).
- Refresco automático cada 10 s, que se puede desactivar, con la hora de la última consulta.
- Una ayuda junto a cada métrica con su interpretación (11.7). Sirve para la defensa.

**FE-12. Administración RabbitMQ: colas**
- Tabla:
  - nombre;
  - durable;
  - listos y sin confirmar;
  - consumidores;
  - política;
  - DLX;
  - marca de "protegida" en las colas base.
- **Crear** con todas las opciones que el backend ya acepta:
  - nombre;
  - durable;
  - TTL;
  - máximo de mensajes;
  - comportamiento al desbordar (`reject-publish` o `drop-head`);
  - expiración;
  - DLX (elegido de los exchanges existentes);
  - routing key del DLX.
- La validación en el cliente replica la del backend:
  - nombre obligatorio, de hasta 255 caracteres, que empiece con letra o número y use solo letras, números, `.`, `-` y `_`, sin el prefijo `amq.`;
  - TTL y expiración entre 1 s y 30 días; máximo de mensajes entre 1 y 1.000.000;
  - la routing key del DLX exige elegir un DLX.
- **Eliminar** por fila, con confirmación. "Forzar" aparece solo en las colas protegidas, con una advertencia fuerte.

**FE-13. Administración RabbitMQ: exchanges**
- Tabla: nombre, tipo, durable y protegido.
- Crear con nombre, tipo (direct, topic, fanout o headers) y durable.
- Eliminar, con "forzar" solo en los protegidos.

**FE-14. Administración RabbitMQ: bindings**
- Tabla: origen, destino, tipo y routing key.
- Crear eligiendo de exchanges y colas existentes.
- Eliminar por fila con la routing key exacta, incluidos `#` y `*`, bien codificados.

**FE-15. Administración RabbitMQ: retención y topología**
- Políticas vigentes: nombre, patrón y definición.
- Formulario con TTL de comandos y TTL de DLQ (1 s a 30 días) y máximo de mensajes (1 a 1.000.000).
- Botón "Aplicar topología y retención" (idempotente) y el resultado.

**FE-16. Administración RabbitMQ: publicar mensaje de prueba**
- Campos: exchange, routing key y cuerpo JSON.
- Plantillas:
  - "notificación válida";
  - "mensaje inválido sin eventId (debe ir a la DLQ)";
  - "comando de capacidad válido".
- Opción "repetir N veces", para llenar una cola en la prueba 14.
- Muestra si el broker confirmó el mensaje y si quedó enrutado (`routed`).
- Se usa en las pruebas 9 y 14.

**FE-17. Kafka (Admin, opcional, EP1)**
- Tópicos y retraso de consumo por grupo.

**FE-18. Diagnóstico de sesión (Admin)**
- Muestra audiencia, emisor, vencimiento, roles y scopes del token, **sin mostrar el token**.
- Sirve para explicar la validación del JWT en la defensa.

### 10.4 Núcleo técnico

**FE-19. Configuración en tiempo de ejecución**
- La app lee `config.json` antes de arrancar.
- Contiene: URL del API Gateway, `clientId`, *authority*, URI de redirección y scope.
- El contenedor lo genera desde variables de entorno.
- Las pantallas de administración (FE-11 a FE-17) llaman con el prefijo `/api/admin/` (P0-05); el resto, con `/api/bff/`.
- Quitar la bandera `demo` (la que se salta el login) de cualquier build de producción.

**FE-20. MSAL**
- Usar **solo** `MsalInterceptor`, con el `protectedResourceMap` apuntando a la URL del Gateway (`/api/*`) con el scope `access_as_user`.
- Eliminar el `authTokenInterceptor` duplicado (`app.config.ts:14` y `:28`). Hoy, si falla, la petición sale sin token.
- Si el token pide interacción, redirigir para obtenerlo.
- Decidir dónde guardar la caché (17.2, D8).

**FE-21. Guards por rol**
- `roleGuard` debe usar la misma normalización que `hasRole` y que el backend ("Operador" equivale a Despachador, "Cliente del dominio" a Cliente). Hoy compara el texto tal cual.
- Los roles se declaran una sola vez en los datos de cada ruta.

**FE-22. Manejo central de errores HTTP**
- Interpreta ProblemDetail (`detail`, `errors`) y el formato actual de shipments (`message`, `validationErrors`).
- Mensajes por código:

| Código | Mensaje |
|---|---|
| 401 | "Vuelva a iniciar sesión" |
| 403 | "Sin permiso" |
| 404 | "No encontrado" |
| 409 | El mensaje del backend |
| 429 | "Demasiadas solicitudes" |
| 503 | "Servicio o mensajería no disponible, reintente" |
| 504 o sin red | "Tiempo agotado" |

- Los mensajes se muestran como avisos emergentes (*toasts*).

**FE-23. Utilidades comunes**
- Consulta periódica con límite y cancelación al salir de la vista.
- Formatos de fecha y número es-CL.
- Insignias de estado del envío y de la capacidad en `estado-ui.ts`.

### 10.5 Calidad y pruebas

**FE-24. Calidad**
- Corregir las 11 advertencias de CSS de Tailwind (selectores `&` vacíos).
- Respetar los presupuestos de tamaño del build.
- Accesibilidad básica.
- Tablas con desplazamiento horizontal en pantallas chicas.

**FE-25. Pruebas** (dentro de la imagen, 5.3) para:
- `AuthService` (normalización de roles);
- `roleGuard`;
- el manejo de errores;
- `BffService` (con `HttpTestingController`);
- envíos: acciones por rol, insignia de capacidad y fin de la consulta periódica;
- formularios del administrador: validaciones.

### 10.6 Imagen y despliegue

**FE-26. Despliegue del frontend**
- Imagen según 5.3, servida por Caddy en `app.<dominio>`.
- Registrar esa URI en Entra ID (AWS-01).
- `vercel.json` queda solo si se elige Vercel (17.2, D2).

**Criterio de aceptación del frontend completo:**
- Recorrido con los 4 roles y el usuario sin rol: cada uno ve exactamente la matriz 10.2.
- Las pruebas 1 a 14 se pueden ejecutar desde la UI cuando corresponde.
- Build sin advertencias y pruebas en verde dentro de Docker.

---

## 11. Contratos del sistema

### 11.1 Roles

- **Roles de aplicación en Entra ID:** Admin, Despachador, Cliente y Auditor.
- **En el backend** se normalizan a mayúsculas con el prefijo `ROLE_`. Además "Operador" y "Operador del dominio" pasan a DESPACHADOR, y "Cliente del dominio" a CLIENTE.
- **El frontend** aplica la misma normalización (FE-21).
- **Recomendación:** usar en Entra ID exactamente los cuatro nombres, para no depender de los sinónimos.

### 11.2 API pública del BFF

✅ ya existe · 🆕 hay que crearla (P1-16). Todas las rutas exigen un JWT válido.

| Método y ruta | Roles | Servicio interno | Estado |
|---|---|---|---|
| GET `/api/bff/auth/me` | Cualquiera autenticado | — | ✅ |
| GET `/api/bff/dashboard` | Los cuatro (contenido según rol) | shipments, report, audit, administrador | ✅, rehacer como agregado que tolera servicios caídos |
| GET `/api/bff/catalog/services` | Admin, Despachador, Cliente | catalog | ✅ |
| GET `/api/bff/catalog/services/{id}` (con capacidades y tarifas) | Admin, Despachador (Cliente sin capacidades) | catalog | 🆕 |
| PUT `/api/bff/catalog/manage/services/{id}/capacities/{capacityId}` | Admin | catalog | 🆕 |
| GET `/api/bff/report/kpis?range=` | Admin | report | ✅ |
| GET `/api/bff/audit/timeline/{id}` | Admin, Auditor | audit | ✅ |
| GET `/api/bff/shipments?page&size&status&capacity&from&to&q` | Los cuatro (Cliente solo propios) | shipments | ✅, agregar paginación y filtros |
| GET `/api/bff/shipments/summary` | Los cuatro | shipments | 🆕 |
| GET `/api/bff/shipments/{id}` (con `allowedTransitions`) | Los cuatro (Cliente solo propios) | shipments | ✅, agregar campos |
| GET `/api/bff/shipments/{id}/timeline` | Admin, Despachador, Auditor | shipments y audit | 🆕 |
| POST `/api/bff/shipments` | Admin, Despachador, Cliente | shipments | ✅ |
| PUT `/api/bff/shipments/{id}/status` | Admin, Despachador | shipments | ✅ |
| POST `/api/bff/shipments/{id}/capacity/retry` | Admin, Despachador | shipments | 🆕 |
| GET `/api/bff/status` | Admin | Health de cada servicio | ✅, reducir lo que expone |
| GET `/api/bff/admin/mq/{cluster, queues, alerts, metrics, exchanges, bindings, policies}` | Admin | administrador | ✅ (`policies` es 🆕) |
| POST `/api/bff/admin/mq/queues` y DELETE `/api/bff/admin/mq/queues/{name}?force=` | Admin | administrador | ✅ |
| POST `/api/bff/admin/mq/exchanges` y DELETE `/api/bff/admin/mq/exchanges/{name}?force=` | Admin | administrador | ✅ |
| POST `/api/bff/admin/mq/bindings` y DELETE `/api/bff/admin/mq/bindings?source&destination&destinationType&routingKey&force` | Admin | administrador | ✅ |
| POST `/api/bff/admin/mq/topology` y PUT `/api/bff/admin/mq/retention` | Admin | administrador | ✅ |
| POST `/api/bff/admin/mq/exchanges/{name}/messages` | Admin | administrador | ✅ |
| GET `/api/bff/admin/kafka/…` | Admin | kafka-admin | ✅ |
| `/api/admin/**` (alias de `/api/bff/admin/**`) | Admin | igual | 🆕 opcional: solo si el Gateway no reescribe la ruta (P0-05) |

A través del Gateway, las rutas `/api/bff/admin/…` se publican como `/api/admin/…` (P0-05 y AWS-05).

**Rutas nuevas en los servicios internos:**
- **shipments:** listado paginado con filtros, resumen de conteos, registros de auditoría por envío y reintentar reserva.
- **audit:** listado paginado.
- **administrador:** políticas vigentes y métricas de DLQ acumuladas.
- **catalog:** las rutas de capacidades ya existen y solo faltan en el BFF.

### 11.3 Mensajería

**Sobre común de los mensajes:** `type`, `eventId`, `timestamp`, `traceId`, `correlationId` y `payload`.

| Mensaje | Exchange (tipo) | Routing key | Cola | Productor | Consumidor | Clave de idempotencia | Errores |
|---|---|---|---|---|---|---|---|
| Reservar capacidad | `ex.rutaexpress.cmd` (direct) | `capacity.reserve` | `q.rutaexpress.command` | shipments | catalog | `commandId` y la reserva por envío (P1-03) | Inválido: NACK a `.dlq`. Transitorio: 3 intentos y luego `.dlq` |
| Liberar capacidad | `ex.rutaexpress.cmd` (direct) | `capacity.release` | `q.rutaexpress.command` | shipments | catalog | `commandId` determinista y la reserva por envío | Igual |
| Resultado de capacidad | `ex.rutaexpress.events` (topic) | `catalog.capacity.reserved`, `rejected` o `released` | `q.rutaexpress.audit` (`catalog.#`) | catalog | shipments | `eventId` (P1-02) | Igual |
| Cambio de estado | `ex.rutaexpress.events` (topic) | `shipment.status.*` | `q.rutaexpress.audit` (`shipment.#`) | shipments | shipments (auditoría) | `eventId` | Igual |
| Correo | `ex.rutaexpress.events` (topic) | `notification.email.send` | `q.rutaexpress.notification` (`notification.#`) | shipments | notify | `eventId` en Redis (P1-11) | Igual |
| Ticket de bodega (PDF) | `ex.rutaexpress.events` (topic) | `notification.warehouse.ticket` | `q.rutaexpress.notification` | shipments | notify | `eventId` | Igual |
| Etiqueta (PDF) | `ex.rutaexpress.events` (topic) | `notification.label.generate` | `q.rutaexpress.notification` | shipments | notify | `eventId` | Igual |
| Mensaje fallido | `ex.rutaexpress.dlx` (direct) | `command`, `notification` o `audit` | `q.rutaexpress.*.dlq` | RabbitMQ (DLX) | `DeadLetterRegistro` | — | Log con `x-death` y contador |

**Políticas:**

| Política | Aplica a | Definición |
|---|---|---|
| `rutaexpress.trabajo` | Las 3 colas de trabajo | TTL 24 h, máximo 10.000 mensajes, `reject-publish`. Lo vencido pasa a la DLQ; lo que no cabe se rechaza y el productor responde 503 |
| `rutaexpress.dlq` | Las 3 DLQ | TTL 7 días, máximo 10.000 mensajes, `drop-head` (descarta el más antiguo) |

**Consumidores:**
- ACK manual.
- Prefetch configurable: 10 hoy; se recomienda de 1 a 5 en la cola de comandos.
- Reintentos con espera exponencial (500 ms × 2^n).

**Productores:**
- *Publisher confirms* y `mandatory`.
- Mensajes persistentes.

### 11.4 Formato de error único

Todos los servicios y el BFF responden **ProblemDetail** con estos campos:

| Campo | Contenido |
|---|---|
| `type` | Tipo de error |
| `title` | Título corto |
| `status` | Código HTTP |
| `detail` | Mensaje para el usuario |
| `instance` | Ruta que falló |
| `errors` | Errores por campo |
| `correlationId` | Identificador para buscar en los logs |

| Situación | Código |
|---|---|
| Validación, JSON mal formado, parámetro inválido o faltante | 400 |
| Sin token o token inválido | 401 |
| Sin rol | 403 |
| No existe, o no es del cliente | 404 |
| Transición inválida, sin capacidad, conflicto de versión o recurso existente | 409 |
| Exceso de solicitudes (Gateway) | 429 |
| Error inesperado | 500, con mensaje genérico |
| Broker no confirma o servicio interno caído | 503 |
| Tiempo agotado | 504 |

### 11.5 Datos y migraciones nuevas

| Servicio | Migración | Contenido |
|---|---|---|
| shipments | V3 | Ampliar `event_id` a 100 (P0-03) |
| shipments | V4 | Columna `version`; columna con el `commandId` de la última reserva; columna del dueño (`oid`) con índice (P1-02, P1-06, P1-08) |
| shipments | V5 (mediano plazo) | Tabla outbox (P1-05) |
| catalog | V3 | Tabla de reservas por envío con `shipment_id` único (P1-03) |
| catalog | V4 | Columna `version` en capacidades (P1-06) |
| catalog | V5 (si se elige) | Capacidad por fecha, o registro del último reinicio diario (P3-07) |

**Regla:** nunca editar una migración ya publicada. Todas se prueban sobre Oracle en el CI (5.6).

### 11.6 Variables de entorno

| Grupo | Variables | Servicios |
|---|---|---|
| Perfil y seguridad | `SPRING_PROFILES_ACTIVE`, `SECURITY_ENABLED`, `AZURE_AD_ISSUER_URI`, `AZURE_AD_AUDIENCE` | Todos los de Spring |
| RabbitMQ | `RABBITMQ_ADDRESSES` (ambos nodos), `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBIT_PREFETCH`, `RABBIT_RETRY_MAX_ATTEMPTS`, `RABBIT_RETRY_INITIAL_MS`, `RABBIT_DLQ_CONSUMER_ENABLED` | shipments, catalog, notify, administrador |
| Administrador | `RABBITMQ_MANAGEMENT_URLS`, `RABBITMQ_VHOST`, `MQ_COMMAND_TTL_MS`, `MQ_DLQ_TTL_MS`, `MQ_MAX_LENGTH`, `MQ_POLL_INTERVAL_MS`, `MQ_ALERT_THRESHOLD` | Administrador RabbitMQ |
| Base de datos | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (una por servicio) | shipments, catalog, audit, report |
| Oracle (infra) | Contraseña de administración y contraseñas de aplicación, separadas (P0-02) | oracle |
| Kafka | `KAFKA_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS` | shipments, catalog, audit, report, kafka-admin |
| shipments | `CATALOGO_ENABLED`, `CATALOG_SERVICE_URL`, `NOTIFY_WAREHOUSE_EMAIL` | shipments |
| notify | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `NOTIFY_FROM`, `REDIS_HOST`, `REDIS_PORT`, `NOTIFY_IDEMPOTENCY_TTL`, `NOTIFY_RETRY_*` | notify |
| BFF | `CATALOG_SERVICE_URL`, `SHIPMENTS_SERVICE_URL`, `AUDIT_SERVICE_URL`, `REPORT_SERVICE_URL`, `NOTIFY_SERVICE_URL`, `MQ_ADMIN_SERVICE_URL`, `KAFKA_ADMIN_SERVICE_URL` | BFF |
| JVM | `JAVA_TOOL_OPTIONS` | Todos los de Spring |
| Frontend (FE-19) | URL del API, `clientId`, *authority*, URI de redirección, scope | frontend |
| Entrada | Dominio de la app, dominio de origen, cabecera secreta del origen, correo de ACME | caddy |
| Imágenes | Etiqueta de versión de cada imagen | compose |

---

## 12. Nube: Entra ID, AWS y seguridad

### AWS-01. Entra ID

1. **Registro de aplicación** (`f3136620-…`):
   - Plataforma **SPA** con la URI de redirección `https://app.<dominio>`. Agregar `http://localhost:4200` solo si se usa Codespaces o Docker Desktop.
2. **Exponer una API:**
   - URI `api://f3136620-…`;
   - scope `access_as_user`;
   - consentimiento de administrador otorgado.
3. **Roles de aplicación:** Admin, Despachador, Cliente y Auditor, habilitados para usuarios y grupos.
4. **Usuarios de prueba:**
   - uno por rol, asignados en la aplicación empresarial;
   - uno **sin rol** (para la prueba 4 y la pantalla "sin permisos").
5. **Tokens:**
   - versión 2 en el manifiesto (`requestedAccessTokenVersion = 2`, o `accessTokenAcceptedVersion = 2` en el formato clásico);
   - *claims* opcionales `email` y `preferred_username`.
6. **Comprobar** con FE-18 los dos valores que valida el Gateway:
   - `iss` debe ser `https://login.microsoftonline.com/<tenant>/v2.0`. Si sale `https://sts.windows.net/<tenant>/`, el token es v1: revisar el paso 5.
   - `aud` debe ser el client ID (`f3136620-…`). En los tokens v2 el `aud` es siempre el client ID, no `api://…`. Los servicios ya aceptan las dos formas; el Gateway debe listar ambas (AWS-05).

### AWS-02. EC2

| Parámetro | Valor recomendado |
|---|---|
| Sistema | Ubuntu 24.04 LTS o Amazon Linux 2023 |
| Tipo | **t3.xlarge (16 GiB)** con todo encendido. Si la cuenta no lo permite (por ejemplo, AWS Academy): t3.large (8 GiB) sin Kafka ni el perfil `ep1` y con *swap*, o dos instancias (17.3) |
| Disco | gp3 de 40 GB |
| IP | Elastic IP |
| Acceso | SSH solo desde la IP de quien administra, o Session Manager de SSM sin puerto 22 |
| *User data* | Instala Docker Engine y el plugin Compose, agrega el usuario al grupo `docker`, crea 4 GB de *swap*, configura la rotación de logs de Docker y la sincronización de hora |

### AWS-03. Red y Security Groups

| Puerto | Origen | Para qué |
|---|---|---|
| 443 y 80 | 0.0.0.0/0 | Caddy (el 80 solo redirige y permite validar los certificados) |
| 22 | Solo tu IP, o cerrado si se usa SSM | Administración y túneles |
| 5672, 15672, 1521, 6379, 8025, 8090, 8080–8087, 9092 | **Cerrados** | Solo red interna de Docker, o túnel SSH |

### AWS-04. DNS y TLS

| Registro | Apunta a | Certificado |
|---|---|---|
| `app.<dominio>` | Elastic IP | Caddy (Let's Encrypt automático) |
| `origen.<dominio>` | Elastic IP | Caddy |
| `api.<dominio>` | Dominio personalizado del API Gateway | ACM, en la misma región del HTTP API |

### AWS-05. API Gateway (HTTP API)

1. **Integraciones HTTP (proxy)**, una por prefijo:
   - `https://origen.<dominio>/api/bff/{proxy}` para las rutas `/api/bff/{proxy+}`;
   - `https://origen.<dominio>/api/bff/admin/{proxy}` para las rutas `/api/admin/{proxy+}` (la reescritura de P0-05).

   El mapeo de parámetros de cada integración **agrega la cabecera secreta del origen**, que Caddy exige. Una integración hacia `https://origen.<dominio>` sin `{proxy}` perdería la ruta.
2. **Autorizador JWT:**
   - origen del token: la cabecera `Authorization`;
   - emisor: `https://login.microsoftonline.com/<tenant>/v2.0`, el `iss` exacto (AWS-01, paso 6);
   - audiencias: `f3136620-…` (el `aud` de los tokens v2) y `api://f3136620-…`;
   - opcional: exigir el scope `access_as_user` en cada ruta.
3. **Rutas** con el autorizador: GET, POST, PUT y DELETE para `/api/bff/{proxy+}` y para `/api/admin/{proxy+}`.

   No crear rutas `ANY`, `OPTIONS` ni `$default`: así el propio Gateway responde los *preflight* CORS. Una ruta `ANY` o `$default` también recibe el `OPTIONS`, que llega sin token, y el autorizador responde 401. Si igual se usa `ANY`, agregar rutas `OPTIONS` sin autorizador.
4. **CORS** en el Gateway:
   - origen `https://app.<dominio>`;
   - métodos GET, POST, PUT, DELETE y OPTIONS;
   - cabeceras `Authorization` y `Content-Type`;
   - `max-age` de 600 s.

   Con CORS configurado, el HTTP API ignora los encabezados CORS del backend, así que se configura en un solo lugar.
5. **Etapa** `$default` con despliegue automático, *throttling* (por ejemplo, 50 de tasa y 100 de ráfaga) y **logs de acceso** en CloudWatch.
6. **Dominio personalizado** `api.<dominio>` con certificado de ACM y el mapeo de la API.
7. **Timeout:** el límite de la integración es de 30 s. Los timeouts del BFF quedan por debajo (P2-01).

### AWS-06. Secretos

| Secreto | Dónde vive | Quién lo usa |
|---|---|---|
| Contraseñas de Oracle (administración y aplicaciones) | `.env` del EC2 (permisos 600) | oracle y los servicios con base de datos |
| Usuario y contraseña de RabbitMQ | `.env` del EC2 y el archivo de definiciones | RabbitMQ y los servicios |
| Cookie de Erlang | Archivo o secreto de Docker en el EC2 | `rabbit1` y `rabbit2` |
| Cabecera secreta del origen | `.env` del EC2 y el mapeo de la integración del Gateway | Caddy y el API Gateway |
| Token de lectura de GHCR (si las imágenes son privadas) | Login de Docker en el EC2 | Docker |
| Llave SSH del despliegue (si hay workflow) | Secretos de GitHub | CI |

Ningún secreto va en Git. No se usan las credenciales por defecto (`guest`, `rutaexpress-local`, `Rutaexpress123`) en la nube.

### AWS-07. Primer despliegue y verificación

1. Crear el EC2 (AWS-02), el Security Group (AWS-03) y el DNS (AWS-04).
2. Clonar el repositorio de infraestructura en el EC2, crear el `.env` a partir de `.env.example` y los archivos de secretos.
3. Levantar en el orden de 5.4 y esperar el estado *healthy*.
4. Comprobar los dos nodos con `cluster_status` y, por túnel, en el Management UI.
5. Comprobar las políticas: en Policies deben estar `rutaexpress.trabajo` y `rutaexpress.dlq`.
6. Configurar el API Gateway (AWS-05) y Entra ID (AWS-01).
7. Abrir `https://app.<dominio>` e iniciar sesión con cada rol.
8. Ejecutar las pruebas de aceptación (13.2) y guardar las evidencias (13.4).

### AWS-08. Costos y apagado

- Detener el EC2 cuando no se use. La Elastic IP y el disco siguen cobrando: AWS cobra por cada IPv4 pública.
- El API Gateway cobra por solicitud, así que su costo es mínimo.
- Después de la nota final, borrar el Gateway, el certificado, los registros DNS y la instancia.

---

## 13. Pruebas y evidencias

### 13.1 Pruebas automatizadas nuevas

| Servicio | Pruebas |
|---|---|
| shipments | Regla de capacidad en las transiciones (P1-01); resultado duplicado y fuera de orden (P1-02); orden de publicación al cancelar (P1-04); reconciliación (P1-05); conflicto de versión (P1-06); propiedad de los envíos (P1-08); validaciones y códigos (P1-09); Kafka apagado (P1-13); migraciones V3 y V4 sobre Oracle |
| catalog | Reserva concurrente y doble liberación (P1-03); edición de capacidad con conflicto (P1-06); migraciones V3 y V4 sobre Oracle |
| notify | Marca "processing" previa (P1-11); integración con RabbitMQ y Redis reales (los 3 tests de Testcontainers que hoy se omiten) |
| Administrador | DLX inexistente, JSON mal formado, 409 por PRECONDITION_FAILED (P1-14); arranque que aplica las políticas (P0-07); métricas de DLQ acumuladas (P1-12); casos de P3-10 |
| BFF | Propagación de errores (P1-10); rutas nuevas por rol (P1-16); alias `/api/admin/**`, si se implementa |
| Frontend | FE-25 |

### 13.2 Pruebas de aceptación (las 14 de la guía)

Se ejecutan contra la nube, a través de `api.<dominio>`.
- Las pruebas HTTP se automatizan con el contenedor `newman` y una colección versionada. Los tokens de cada rol se pasan como variables al ejecutarla.
- La mensajería se verifica en el Management UI por túnel y en los logs del compose.

| # | Prueba | Cómo hacerla | Resultado esperado |
|---|---|---|---|
| 1 | Sin token | `GET /api/bff/shipments` sin `Authorization` | 401 **del Gateway**, visible en su log de acceso |
| 2 | Token inválido o expirado | El mismo GET con un token alterado o vencido | 401 del Gateway |
| 3 | Token válido con rol | Despachador crea un envío; Admin consulta los KPIs | 201 con el JSON; 200 |
| 4 | Sin permiso | El usuario sin rol, o un Cliente, crea una cola por `/api/admin/mq/queues` | 403 del BFF; la UI no ofrece la opción |
| 5 | Regla de negocio inválida | Capacidad agotada y se crea un envío; transición CREADO → EN_RUTA | 409 con mensaje; el Management UI no muestra publicaciones nuevas |
| 6 | Regla de negocio válida | Aceptar un envío | 200 con PENDIENTE; segundos después la UI muestra RESERVADA; catalog consume el comando; llega `catalog.capacity.reserved`; el correo aparece en Mailpit; la capacidad disponible baja en FE-08 |
| 7 | Estado del clúster | `cluster_status` en `rabbit1` y Overview → Nodes | `rabbit1` y `rabbit2` en ejecución |
| 8 | Consumo en las 3 colas | Recorrer la prueba 6 | command: log de catalog. notification: Mailpit. audit: registro en la línea de tiempo. Las tres colas sin pendientes |
| 9 | Error no recuperable | FE-16: plantilla "mensaje inválido sin eventId" hacia `ex.rutaexpress.events` con `notification.email.send` | NACK; el mensaje llega a `q.rutaexpress.notification.dlq`; log con `x-death` y motivo `rejected`; sube el acumulado de DLQ |
| 10 | Error recuperable | Detener Mailpit y aceptar un envío | 3 intentos con espera en los logs de notify, luego DLQ y log |
| 11 | Admin crea | FE-12 a FE-14: exchange `ex.demo` (topic), cola `q.demo` y el binding `ex.demo` → `q.demo` con `demo.#` | 201 en cada uno; visibles en la UI y en el Management UI |
| 12 | Admin elimina | Eliminar `q.demo` y `ex.demo` | 200 con `deleted: true`; desaparecen de la UI y del Management UI |
| 13 | Admin con datos inválidos | Nombre vacío, `amq.x`, TTL fuera de rango, DLX inexistente (hoy se acepta: P1-14); borrar `q.rutaexpress.command` sin forzar | 400 con mensaje claro (409 en el último caso); nada se crea ni se borra |
| 14 | Retención | Crear `ex.retencion` y `ex.retencion.dlx` (direct), la cola `q.retencion.dlq` y la cola `q.retencion` (máximo 5, `reject-publish`, TTL 10 s, DLX `ex.retencion.dlx`), con sus bindings. Publicar 6 mensajes (FE-16, repetir 6). Repetir con otra cola en `drop-head` y sin TTL | Con `reject-publish`, la cola queda en 5 y el 6.º recibe confirmación negativa (409 con P1-14; hoy 503). A los 10 s, los 5 pasan a `q.retencion.dlq` con `x-death` motivo `expired`. Con `drop-head`, el más antiguo pasa a la DLQ con motivo `maxlen`. Además, Policies muestra las dos políticas base aplicadas |

### 13.3 Regresión de los bugs

| Caso | Resultado esperado |
|---|---|
| Avanzar con capacidad RECHAZADA | 409 |
| Resultado duplicado o fuera de orden | Sin cambios |
| Dos reservas concurrentes | Una sola |
| Dos liberaciones | Un solo descuento |
| Cancelar con un fallo de publicación | Sin liberación huérfana |
| Compensación con el broker caído | Se reconcilia |
| Doble clic en "Aplicar" | Una sola transición |
| Cliente A frente a Cliente B | Cada uno ve solo lo suyo |
| Kafka apagado | Respuesta en menos de 1 s |
| notify cae a mitad de proceso | La notificación no se pierde |
| JSON mal formado en cualquier servicio | 400, nunca 500 |

### 13.4 Evidencias

**Para la entrega (AVA y correo):**
- enlaces a los repositorios;
- captura del CI en verde de cada uno;
- README con el diagrama de arquitectura.

**Para la presentación** (capturas o pantallas en vivo):
- Entra ID: tenant, usuarios y roles asignados.
- API Gateway: rutas, autorizador, CORS, dominio y logs de las pruebas 1 y 2.
- EC2: la instancia y el estado del compose con todo *healthy*.
- RabbitMQ: nodos, colas con sus DLQ, exchanges y bindings, y políticas.
- La creación y eliminación desde el administrador.
- Las métricas en FE-11 y en el Management UI.
- Los logs de DLQ con `x-death`.
- El flujo completo de la prueba 6.
- Los datos en Oracle: el envío con su capacidad y la línea de tiempo.

---

## 14. Repositorios separados

### 14.1 Repositorios

| Repositorio | Contenido | Imagen | Exigido por la guía |
|---|---|---|---|
| `frontend-rutaexpress` | `frontend/` | `frontend-rutaexpress` | ✅ |
| `ms-rutaexpress-bff` | `ms-rutaexpress-bff/` | `ms-rutaexpress-bff` | ✅ |
| `ms-rutaexpress-shipments` | `ms-rutaexpress-shipments/` (principal) | `ms-rutaexpress-shipments` | ✅ |
| `ms-rutaexpress-catalog` | `ms-rutaexpress-catalog/` | `ms-rutaexpress-catalog` | ✅ |
| `ms-rutaexpress-rabbit-admin` | `ms-rutaexpress-mq-admin/`, renombrado | `ms-rutaexpress-rabbit-admin` | ✅ |
| `ms-rutaexpress-notify` | `ms-rutaexpress-notify/` | `ms-rutaexpress-notify` | Recomendado: consume una de las tres colas |
| `rutaexpress-infra` | `infra/` reorganizado (5.4), colección de aceptación y documentación | — | Recomendado: la demo depende de él |
| `ms-rutaexpress-audit`, `ms-rutaexpress-report`, `ms-rutaexpress-kafka-admin` | EP1 | Sus imágenes | Opcional (solo si Kafka sigue encendido) |

### 14.2 Pasos

1. Con `main` terminado, extraer cada carpeta **con su historial** (división por subárbol de git) hacia su repositorio.
2. Agregar a cada repositorio su workflow de CI (5.5), su `.gitignore` (14.3) y su README (14.4).
3. Revisar el historial de cada uno en busca de secretos antes de hacerlo público. Si aparece alguno, rotarlo y limpiar el historial.
4. En el repositorio de infraestructura, apuntar los compose a las imágenes publicadas.
5. Proteger `main` en cada repositorio.
6. Enviar los enlaces a AVA y al docente.
7. Dejar el monorepo archivado, con un README que apunte a los repositorios nuevos.

### 14.3 `.gitignore`

| Tecnología | Entradas |
|---|---|
| Kotlin y Gradle | `build/`, `.gradle/`, `.kotlin/`, `out/`, `.idea/`, `*.iml`, `.vscode/` (salvo la configuración compartida), `*.log`, `HELP.md`, `.env`, `*.class` |
| Angular | `/dist`, `/node_modules`, `/.angular/cache`, `/coverage`, `/out-tsc`, `.env*`, `npm-debug.log`, `.vercel` |
| Infraestructura | `.env` (se versiona solo `.env.example`), archivos de secretos y cookie, volúmenes locales |

### 14.4 Plantilla de README

Secciones, en este orden:

1. Propósito y lugar en la arquitectura, con un diagrama.
2. Endpoints, con sus roles.
3. Mensajes que produce y consume: exchange, routing key, cola, idempotencia y DLQ.
4. Variables de entorno.
5. Cómo construir y probar con Docker.
6. Cómo ejecutarlo con el compose de infraestructura.
7. Decisiones de diseño.
8. Autores.

---

## 15. Demo y defensa

### 15.1 Guion (8 a 10 minutos)

La presentación es **individual** (de 5 a 10 minutos): cada integrante debe poder hacer la demo completa y responder las preguntas solo.

| Minuto | Qué mostrar |
|---|---|
| 0:00 | Arquitectura (diagrama de 4.1) y qué valida cada capa |
| 0:45 | Entra ID: usuarios y roles. Login como Despachador |
| 1:30 | API Gateway: rutas, autorizador, CORS. Pruebas 1 y 2 en vivo |
| 2:30 | EC2: el compose con todo *healthy*, `cluster_status` y el Management UI con 2 nodos |
| 3:30 | Flujo de negocio: crear y aceptar. El JSON inmediato con PENDIENTE; la cola de comandos se consume; RESERVADA en la UI; la capacidad baja en el catálogo; el correo en Mailpit; la línea de tiempo |
| 5:00 | Error no recuperable (FE-16): DLQ y log con `x-death`. Error recuperable: reintentos y DLQ |
| 6:00 | Login como Admin: crear y eliminar cola, exchange y binding, verlo en el Management UI, y una validación con 400 |
| 7:00 | Métricas (FE-11 y Management UI) y políticas de retención, con su efecto |
| 8:00 | Usuario sin permiso: 403. Cierre con el checklist de la guía |

### 15.2 Conceptos que hay que poder explicar

- **Direct frente a topic.**
  - **Direct** entrega por coincidencia exacta de la routing key. Se usa para comandos con un destino claro: `capacity.reserve` hacia la cola de comandos de catalog.
  - **Topic** entrega por patrones (`*` es una palabra, `#` son cero o más). Se usa para eventos con varios interesados: `shipment.#` y `catalog.#` hacia auditoría, `notification.#` hacia notify.
- **DLX y DLQ.** Aíslan los mensajes que no se pueden procesar (*mensajes venenosos*) sin frenar la cola principal y permiten analizarlos. Un mensaje llega a la DLQ por tres motivos:
  - rechazo sin reencolar (`rejected`);
  - TTL vencido (`expired`);
  - cola llena con `drop-head` (`maxlen`).

  El encabezado `x-death` dice el motivo, la cola de origen y cuántas veces pasó.
- **ACK y NACK.** El ACK se envía solo cuando el efecto ya quedó guardado. El NACK sin reencolar manda a la DLQ. No se reencola sin límite porque genera bucles. Los errores transitorios se reintentan de forma acotada. Esto asegura que ningún mensaje se pierda ni quede girando para siempre.
- **Prefetch (QoS).** Es el máximo de mensajes sin confirmar por consumidor.
  - Alto: más rendimiento, pero el reparto entre consumidores es desigual y un mensaje en reintento retiene a los que esperan detrás.
  - Bajo: reparto parejo y latencia predecible.
  - Hoy se usa 10; para comandos conviene de 1 a 5.
- **Separar el negocio de la mensajería.** La configuración (`RabbitMqConfig`, `RabbitNombres`) declara la topología; los servicios de dominio no conocen nombres de colas. Así se puede cambiar el broker o la topología sin tocar las reglas, y probar cada parte por separado.
- **Confirmaciones del publicador.** *Publisher confirms* y `mandatory` aseguran que el broker recibió y enrutó el mensaje. Si no, la operación se revierte y responde 503.
- **Idempotencia.** La entrega es "al menos una vez", así que los duplicados se descartan por `eventId`, por `commandId` o por la reserva por envío.
- **Métricas.**
  - Colas activas.
  - Mensajes listos (backlog) frente a sin confirmar (en proceso).
  - Consumidores: si una cola tiene 0, crece sin control.
  - DLQ: el acumulado de llegados y su tasa, no solo la profundidad, porque la DLQ consumida queda en 0.
  - Alarmas de memoria y disco del nodo.
- **Retención.**
  - El TTL evita que los mensajes viejos se acumulen.
  - El máximo de mensajes limita el tamaño de la cola.
  - `reject-publish` frena al productor (presión hacia atrás).
  - `drop-head` descarta lo más antiguo.
  - Las políticas se aplican sin redeclarar la cola; los argumentos `x-` quedan fijos al declararla.
- **Clúster.**
  - Los dos nodos se descubren entre sí y comparten la cookie de Erlang.
  - `autoheal` resuelve las particiones de red.
  - Las colas clásicas viven en un solo nodo: si ese nodo cae, sus colas no están disponibles, aunque los clientes se reconectan al otro.
  - Las colas quorum necesitan mayoría, así que con 3 nodos toleran la caída de 1.

### 15.3 Cómo mejora la escalabilidad y la resiliencia

- **El usuario no espera.** El API responde sin esperar a catalog ni a notify.
- **Se absorben los picos.** Las colas reciben los picos de carga y los consumidores los procesan a su ritmo.
- **Se escala por separado.** Se agregan consumidores (consumidores en competencia) sin tocar a los productores.
- **Las fallas quedan aisladas.** Si catalog o notify caen, los mensajes esperan en la cola; los reintentos y la DLQ evitan pérdidas y bucles.
- **Hay control del crecimiento.** Las políticas de retención y `reject-publish` impiden el crecimiento descontrolado.
- **Los datos son durables.** Colas durables, mensajes persistentes y *publisher confirms*.

### 15.4 Preguntas probables

| Pregunta | Respuesta corta |
|---|---|
| ¿Qué pasa si catalog está caído al aceptar un envío? | El comando queda en `q.rutaexpress.command` y el envío en PENDIENTE. Cuando catalog vuelve, lo procesa. No se puede despachar hasta tener RESERVADA |
| ¿Y si RabbitMQ no confirma la publicación? | La transacción se revierte y el API responde 503; no quedan cambios a medias |
| ¿Cómo evitan procesar dos veces? | Con el `eventId` o el `commandId` registrado, y con la reserva única por envío |
| ¿Por qué no reencolan los errores? | Para evitar bucles infinitos. Los errores transitorios se reintentan de forma acotada y luego van a la DLQ |
| ¿Qué pasa si cae `rabbit1`? | Los clientes se reconectan a `rabbit2`, pero las colas clásicas que vivían en `rabbit1` no están disponibles hasta que vuelva. La alta disponibilidad real requiere colas quorum con 3 nodos |
| ¿Por qué la DLQ aparece en 0? | Porque se consume y se registra en log, como pide la guía. Por eso se mide el acumulado de llegados |
| ¿Qué valida el Gateway y qué el BFF? | El Gateway valida firma, emisor, audiencia y vigencia. El BFF vuelve a validar y además aplica los roles |
| ¿Cómo aplican las políticas? | Con el administrador al arrancar y con el archivo de definiciones del clúster. Se ven en Policies |

---

## 16. Orden de ejecución

| Fase | Contenido | Termina cuando | Estimación (pareja) |
|---|---|---|---|
| **0. Preparación** | Cuentas, dominio, decisiones de la sección 17 | Las decisiones D1 a D8 están tomadas | 0,5 día |
| **1. Base en Docker** | P0-01, P0-03, P0-08, P0-10 | `main` = base + V3; CI en verde con imágenes que ejecutan las pruebas | 1 día |
| **2. Consistencia** | P1-01 a P1-06 y sus migraciones | Las pruebas de 13.1 y la regresión de 13.3 relacionadas pasan | 2 días |
| **3. Seguridad, errores y mensajería** | P1-07 a P1-16 | ProblemDetail en todo; notify y DLQ corregidos; Kafka no bloquea; API para el frontend | 1,5 días |
| **4. Frontend completo** | FE-01 a FE-26 | La matriz 10.2 se cumple con los 5 usuarios | 2,5 días |
| **5. Nube** | P0-02, P0-04, P0-05, P0-07, P0-09, P0-11, AWS-01 a AWS-07, P2 | Todo *healthy* en el EC2 y la cadena completa funcionando por `app.` y `api.` | 1,5 días |
| **6. Repositorios separados** | P0-06, P3 | Los repositorios clonan en limpio con su CI en verde; enlaces enviados | 0,5 día |
| **7. Aceptación y ensayo** | 13.2 a 13.4 y 15 | Las 14 pruebas con evidencia y dos ensayos cronometrados por integrante | 1 día |

**Ruta mínima, si falta tiempo** (alcanza para cumplir la guía, aunque deja deuda técnica):
- todos los P0;
- P1-01;
- P1-02;
- P1-03 en versión mínima: solo comprobar al liberar que existe una reserva activa;
- P1-04;
- P1-10;
- P1-12 (al menos la demo con el consumo desactivado);
- P1-14;
- FE-04, FE-07, FE-11 a FE-16, FE-19 a FE-22.

El resto queda documentado en los README como mejoras conocidas.

---

## 17. Decisiones y riesgos

### 17.1 Supuestos

- El tenant y el registro de aplicación de la EP1 se mantienen.
- Hay acceso a un dominio propio para `app.`, `api.` y `origen.`.
- La cuenta de AWS permite EC2, API Gateway, ACM y DNS.

### 17.2 Decisiones que hay que tomar (fase 0)

| ID | Decisión | Opciones | Recomendación |
|---|---|---|---|
| D1 | Kafka en la demo | Encendido (EP1 completa) o apagado | **Encendido** si el EC2 lo permite, para mostrar la EP1 completa (auditoría y reportes). La guía solo exige que sigan operativos el login, las rutas protegidas y los roles, así que apagarlo junto con el perfil `ep1` no incumple la EP2. Con P1-13, su caída no bloquea nada |
| D2 | Dónde se sirve el frontend | Contenedor en el EC2 con Caddy, o Vercel | **Contenedor**: todo en Docker, y CORS real entre `app.` y `api.` |
| D3 | Base de datos | Oracle en contenedor en el EC2, o gestionada (Autonomous o RDS) | **Contenedor**, explicado como "base en la nube dentro de la instancia". Si el docente exige un servicio gestionado, cambiar solo la URL (P0-02) |
| D4 | Tamaño del EC2 | t3.xlarge, o t3.large sin Kafka, o dos instancias | **t3.xlarge**. Ver el riesgo 17.3 |
| D5 | Del Gateway al BFF | Cabecera secreta con Caddy, o VPC Link con balanceador | **Cabecera secreta**: más simple y barata. Con VPC Link se gana aislamiento a cambio de costo |
| D6 | Registro de imágenes | GHCR público, GHCR privado o ECR | **GHCR público** (las imágenes no contienen secretos) |
| D7 | Outbox | Ahora o después | **Después**. Primero la reconciliación (P1-05) |
| D8 | Caché de MSAL | `localStorage` o `sessionStorage` | **`sessionStorage`**: menos exposición a XSS, a cambio de perder el SSO entre pestañas |

### 17.3 Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La cuenta de AWS limita el tamaño de la instancia o corta las sesiones (por ejemplo, AWS Academy) | Falta memoria y las IP cambian | Kafka apagado (D1), dos instancias, Elastic IP o DNS actualizado y `restart: unless-stopped` |
| Límite de emisión de certificados de Let's Encrypt | Sin HTTPS en el frontend | Probar primero con el entorno de pruebas de ACME y no recrear los volúmenes de Caddy |
| El emisor o la audiencia del token no coinciden con el autorizador | Todo responde 401 | Revisar `iss` y `aud` con FE-18 antes de configurar el Gateway (AWS-01, paso 6) |
| Falta de memoria en el EC2 | Servicios que se reinician | Límites de 5.4, *swap* y monitoreo con `docker stats` |
| Falta de tiempo | Entrega incompleta | Ruta mínima de la sección 16 |
| Una migración editada por error | Flyway no arranca | Regla de 11.5 y el job de migraciones del CI |

---

## 18. Anexos

### A. Verificación realizada (19-10-2026)

| Servicio | `main` | Rama base: pruebas | Fallos | Omitidas | Jar |
|---|---|---|---|---|---|
| BFF | ❌ No compila (3 errores de inferencia de tipos) | 19 | 0 | 0 | ✅ |
| shipments | ❌ El test no compila (inferencia en `EnvioServicioTests.kt:32`) | 22 | 0 | 0 | ✅ |
| catalog | (sin cambios) | 26 | 0 | 0 | ✅ |
| Administrador RabbitMQ | (sin cambios) | 26 | 0 | 0 | ✅ |
| notify | (sin cambios) | 23 | 0 | 3 (necesitan Docker) | ✅ |
| audit | (sin cambios) | 8 | 0 | 0 | ✅ |
| report | (sin cambios) | 5 | 0 | 0 | ✅ |
| kafka-admin | (sin cambios) | 4 | 0 | 0 | ✅ |
| Frontend | Compila | Build de producción OK, 11 advertencias de CSS | — | Karma no se ejecutó (no había navegador) | — |

**No se verificó:** RabbitMQ, Oracle, Redis y Kafka reales; el flujo completo; las pruebas del frontend. Todo eso queda cubierto por 5.6 y 13.2, dentro de Docker.

### B. Trazabilidad con ANALISIS_EP2.md

| Pendiente | Dónde se resuelve |
|---|---|
| R1: el BFF no compila | P0-01 (resuelto en la rama base) |
| R2: los tests no compilan | P0-01 (resuelto en la rama base) |
| R3: init de Oracle y contraseñas | P0-02 (el cambio al PDB ya está en la base; falta desacoplar las contraseñas) |
| R4: `eventId` de 69 caracteres | P0-03 (con V3, sin editar V2) |
| R5: reentrega y doble liberación | P1-02, P1-03, P1-04 |
| R6: idempotencia por envío ante concurrencia | P1-03, P1-06 |
| T1 y T3: `deploy-aws.bat` (externo) | Lo reemplaza el despliegue por compose (5.7 y AWS-07) |
| T5: API Gateway | P0-05 y AWS-05 |
| T9: repositorios separados | P0-06 y la sección 14 |
| T13d: advertencias de CSS | FE-24 |
| T15: demo | Secciones 13 y 15 |

### C. Cambios respecto a la versión 1

- Rama base definida y verificada: compila y pasan 130 pruebas.
- Nueva regla "nada en el PC": imágenes que ejecutan las pruebas, CI, compose completo con frontend y Caddy, y Codespaces opcional (sección 5, P0-08 a P0-10).
- Nuevos P0-08 a P0-11, P1-15 y P1-16, y P2-13.
- Corrección de P1-13: los tiempos de Kafka deben cumplir delivery ≥ linger + request, o el productor no arranca.
- Frontend especificado completo (FE-01 a FE-26) con su matriz por rol, y el problema de normalización de roles en `roleGuard` (FE-21).
- Nuevas secciones: contratos (API, mensajería, errores, migraciones, variables), nube paso a paso (AWS-01 a AWS-08), estrategia de pruebas y evidencias, repositorios separados, guion con tiempos, preguntas probables, ruta mínima, decisiones y riesgos.
- API Gateway corregido: la audiencia de los tokens v2 es el client ID, las rutas van por método para que el *preflight* CORS no pase por el autorizador y las integraciones conservan la ruta con `{proxy}`.
- P1-14 ajustado al código real: el largo de las routing keys ya se valida; se suman `reconciliar` y la confirmación negativa al publicar, que hoy responden 503.
