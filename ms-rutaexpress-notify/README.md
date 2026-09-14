# ms-rutaexpress-notify

Microservicio de notificaciones y generación de comprobantes para la plataforma **RutaExpress**, desarrollado en **Kotlin 2.3.21** y **Spring Boot 4.1.1** (JVM 21).

Es un servicio sin base de datos relacional que consume comandos asíncronos desde **RabbitMQ**, gestiona idempotencia en **Redis**, genera comprobantes PDF y emite notificaciones por correo (SMTP / Mailpit) y push.

---

## Características Principales

- **Consumo Asíncrono AMQP:** Escucha en colas dedicadas con confirmación manual (`AcknowledgeMode.MANUAL`):
  - `q.cmd.email`: Envío de correos de confirmación y estado de envíos.
  - `q.cmd.warehouse`: Generación de tickets para operarios de bodega.
  - `q.cmd.label`: Generación y despacho de etiquetas de envío.
  - DLQs asociadas: `q.cmd.email.dlq`, `q.cmd.warehouse.dlq`, `q.cmd.label.dlq` con reintentos y política de backoff.
- **Idempotencia con Redis:** Almacén de control de duplicados (`processing` con bloqueo temporal → `completed` con TTL configurable).
- **Generación de Documentos:** Creación de etiquetas y comprobantes PDF mediante OpenPDF.
- **Seguridad OAuth2:** Resource Server con soporte JWT (Azure AD / IDaaS) para endpoints de Actuator y métricas Prometheus.
- **Entorno Contenerizado:** Incluye `Dockerfile` multistage y `compose.yml` local con RabbitMQ (Management), Redis y Mailpit.

---

## Requisitos Previos

- **Java:** JDK 21 instalado (Gradle toolchain configurado).
- **RabbitMQ:** Broker AMQP (puertos `5672` y `15672`).
- **Redis:** Servidor Redis (puerto `6379`).
- **Mail Server:** Servidor SMTP (ej. Mailpit en puertos `1025` SMTP y `8025` Web UI).
- *(Opcional)* **Docker & Docker Compose** para levantar la infraestructura de soporte.

---

## Variables de Entorno y Configuración

El servicio se configura a través de `src/main/resources/application.properties` y soporta las siguientes variables de entorno:

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP del servicio | `8080` |
| `RABBITMQ_HOST` | Host del broker RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto de conexión AMQP | `5672` |
| `RABBITMQ_USERNAME` | Usuario de RabbitMQ | `guest` |
| `RABBITMQ_PASSWORD` | Contraseña de RabbitMQ | `guest` |
| `REDIS_HOST` | Host de Redis | `localhost` |
| `REDIS_PORT` | Puerto de Redis | `6379` |
| `MAIL_HOST` | Host de servidor SMTP | `localhost` |
| `MAIL_PORT` | Puerto de servidor SMTP | `1025` |
| `SECURITY_ENABLED` | Activa validación OAuth2/JWT | `false` (dev) / `true` (prod) |
| `NOTIFY_FROM` | Dirección de remitente para correos | `no-reply@rutaexpress.local` |

---

## Puesta en Marcha con Docker Compose

El directorio incluye un `compose.yml` preconfigurado con RabbitMQ, Redis y Mailpit con sus respectivos healthchecks:

```bash
# Iniciar dependencias locales
docker compose up -d rabbitmq redis mailpit

# O iniciar todo el stack incluyendo el microservicio
docker compose up -d --build
```

- **RabbitMQ Management:** http://localhost:15672 (guest / guest)
- **Mailpit Web UI:** http://localhost:8025
- **Métricas Actuator / Prometheus:** http://localhost:8080/actuator/prometheus

---

## Ejecución con Gradle

### 1. Ejecutar Pruebas Automatizadas
```bash
./gradlew test
```
> Para ejecutar pruebas de integración que requieren broker real en Docker:
> ```bash
> RUN_DOCKER_TESTS=true ./gradlew test
> ```

### 2. Iniciar el Microservicio
```bash
./gradlew bootRun
```

### 3. Construir JAR Ejecutable
```bash
./gradlew bootJar
```

---

## Documentación Detallada

Para consultar detalles de arquitectura interna, topología de colas, converter de mensajes, manejo de fallos y diseño de idempotencia, revise el archivo [`AGENTS.md`](./AGENTS.md).
