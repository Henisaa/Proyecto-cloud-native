# RutaExpress

Plataforma de envíos de última milla del Caso 3. La EP2 incorpora reservas de capacidad asíncronas, notificaciones y auditoría sobre RabbitMQ, con un administrador REST y frontend Angular por rol.

```mermaid
flowchart LR
  Angular[Angular + MSAL] -->|JWT| Gateway[API Gateway + CORS]
  Gateway --> Caddy
  Caddy --> BFF
  BFF --> Shipments
  BFF --> Catalog
  BFF --> Admin[Rabbit Admin]
  Shipments --> RabbitMQ
  RabbitMQ --> Catalog
  RabbitMQ --> Notify
  RabbitMQ --> Shipments
  Shipments --> Oracle
  Catalog --> Oracle
  Notify --> Redis
  Notify --> Mailpit
```

## Componentes

| Carpeta | Responsabilidad | Puerto interno |
|---|---|---|
| frontend | Angular 19, MSAL, envíos, catálogo y administración de colas | 8080 |
| ms-rutaexpress-bff | JWT, roles, agregación y proxy del dominio | 8080 |
| ms-rutaexpress-catalog | Servicios, tarifas y reservas de capacidad | 8081 |
| ms-rutaexpress-notify | Correos y PDF desde RabbitMQ | 8082 |
| ms-rutaexpress-audit | Auditoría Kafka (EP1 opcional) | 8083 |
| ms-rutaexpress-report | KPIs Kafka (EP1 opcional) | 8084 |
| ms-rutaexpress-shipments | Envíos y coordinación asíncrona | 8085 |
| ms-rutaexpress-rabbit-admin | Clúster, colas, exchanges, bindings, retención y métricas | 8086 |
| ms-rutaexpress-kafka-admin | Administración Kafka (EP1 opcional) | 8087 |
| infra | Compose por capas: mq, apps, kafka y edge | Solo Caddy publica 80/443 |

Kotlin 2.3.21, JVM 21, Spring Boot 4.1.1, Gradle Wrapper 9.7.1, Jackson 3, Oracle/JPA/Flyway, RabbitMQ 4, Kafka 3.9.2/ZooKeeper, Redis y Mailpit.

## Construcción, pruebas y ejecución

GitHub Actions ejecuta la suite completa en cada push y PR: nueve compilaciones Docker, ocho suites backend con Testcontainers y cobertura, Karma/Chromium, smoke nginx y stack integrado con Playwright en cuatro navegadores/dispositivos. Solo publica las imágenes candidatas en GHCR con etiqueta `sha-<commit corto>` después de aprobar el control global. Fallos, omisiones, pruebas inestables y regresiones de cobertura bloquean la publicación. Alcance, artefactos y límites: [ci/README.md](ci/README.md). No instalar JDK, Gradle ni Node en el PC.

En EC2, configurar `infra/.env` a partir de su ejemplo y ejecutar:

```sh
bash infra/scripts/up.sh
bash infra/scripts/status.sh
```

Detalles y variables: [infra/README.md](infra/README.md). Interfaz y roles: [frontend/README.md](frontend/README.md). Aceptación HTTP y evidencia manual: [infra/acceptance/README.md](infra/acceptance/README.md).

## Mensajería y seguridad

Tres colas funcionales: command, notification y audit; cada una tiene su DLQ. Comandos por exchange direct, eventos por topic, ACK manual, reintentos acotados, NACK sin reencolar y registro de DLQ. El frontend muestra PENDIENTE → RESERVADA/RECHAZADA, permite reintento y visualiza la compensación. Catalog deduplica por commandId y reserva por shipmentId, con bloqueo y clave única.

MSAL obtiene el access token. Gateway y BFF validan JWT; el BFF aplica roles y conserva Bearer. Cliente solo consulta envíos de su oid. Solo Admin administra RabbitMQ. No exponer servicios internos ni desactivar seguridad en nube. La coordinación entre base y RabbitMQ usa confirmaciones y reconciliación; outbox sigue como mejora prevista en el plan.

## Estado de la EP2

CI verde certifica solo las comprobaciones automatizadas de su commit: no prueba el tenant, DNS, Gateway ni EC2.

Modo local (Docker Desktop): `bash infra/scripts/up.sh --dev` levanta RabbitMQ (2 nodos), datos, microservicios y el frontend en `http://localhost:4200`, que llama directo al BFF en `http://localhost:8080` (CORS local con `CORS_ALLOWED_ORIGINS`).

Despliegue en una VPS (Azure, AWS EC2 u otra Linux): `bash deploy-vps.sh` instala Docker si falta y levanta el clúster RabbitMQ de dos nodos, Oracle, Redis, Mailpit y los cinco microservicios EP2 (sin frontend) con sus puertos publicados en `http://<ip>:<puerto>`. Configuración en `infra/vps/.env` (se genera la primera vez) y servicios en `infra/vps/compose.yml`.

La migración V2 de shipments conserva su definición original; V3 amplía event_id a 100, V4 agrega versión, dueño e identificador de reserva y V5 amplía service_code a 50 (igual que el código de catalog). No editar migraciones publicadas.

## Autores

Equipo RutaExpress (Henisaa/Proyecto-cloud-native).
