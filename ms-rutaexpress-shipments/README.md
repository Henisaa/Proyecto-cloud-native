# ms-rutaexpress-shipments

Microservicio de **envíos** de la plataforma RutaExpress (Kotlin + Spring Boot 3.4.3, JDK 21). Es el servicio core que conecta el ecosistema:

- **CRUD de envíos** con máquina de estados: `CREADO -> ACEPTADO -> EN_BODEGA -> EN_RUTA -> ENTREGADO` (o `CANCELADO`). Regla: no se puede pasar a `EN_RUTA` sin haber pasado por `ACEPTADO`.
- **Capacidad de flota**: consulta a `ms-rutaexpress-catalog` (`capacity/check` al crear, `capacity/reserve` al aceptar, `capacity/release` al cancelar), reenviando el JWT del usuario.
- **Eventos Kafka**: publica JSON plano en `shipments.events` (lo consume `ms-rutaexpress-report`) y `envios-events` (lo consume `ms-rutaexpress-audit`) con `key = shipmentId`.
- **Comandos RabbitMQ**: publica en `cmd.direct` con envelope (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`, `payload`) las routing keys `email.send`, `warehouse.ticket` y `label.gen` para `ms-rutaexpress-notify`.
- Persistencia en **Oracle** con Flyway (`tb_shipments`).

## Endpoints

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/api/shipments` | ADMIN, DESPACHADOR, CLIENTE | Crear envío (verifica capacidad) |
| GET | `/api/shipments/{id}` | ADMIN, DESPACHADOR, CLIENTE, AUDITOR | Obtener envío |
| GET | `/api/shipments?status=&from=&to=` | ADMIN, DESPACHADOR, CLIENTE, AUDITOR | Listar con filtros |
| PUT | `/api/shipments/{id}/status` | ADMIN, DESPACHADOR | Cambiar estado |

Públicos: `/actuator/health`, `/actuator/prometheus`, `/swagger-ui.html`, `/v3/api-docs`.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` = H2 sin integraciones; en cloud usar `prod` (Oracle + Kafka + Rabbit) |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Oracle `localhost:1521/FREEPDB1` / `ruta_shipments` / `Ruta1234` | Base de datos |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `KAFKA_ENABLED` | `true` | Publicar eventos |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | `localhost` / `5672` / `guest` / `guest` | RabbitMQ |
| `RABBIT_ENABLED` | `true` | Publicar comandos |
| `CATALOG_SERVICE_URL` | `http://localhost:8081` | Catálogo |
| `CATALOGO_ENABLED` | `true` | Validar capacidad contra catálogo |
| `AZURE_AD_ISSUER_URI` | `https://login.microsoftonline.com/common/v2.0` | Issuer JWT |
| `AZURE_AD_AUDIENCE` | `api://rutaexpress-shipments` | Audience JWT |
| `SECURITY_ENABLED` | `true` | Validación JWT (los `@PreAuthorize` siguen activos siempre) |
| `NOTIFY_WAREHOUSE_EMAIL` | `bodega@rutaexpress.local` | Correo de bodega para tickets |

## Ejecutar en local

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Pruebas

```bash
./gradlew test
```
