# ms-rutaexpress-report

Microservicio de reportería, KPIs y analítica operacional para la plataforma **RutaExpress**, desarrollado en **Kotlin 2.1** y **Spring Boot 3.4** siguiendo una arquitectura minimalista.

Ingesta eventos del ciclo de vida de envíos vía **Kafka**, consolida métricas agregadas en **Oracle** y expone consultas analíticas optimizadas de solo lectura bajo `/api/report/*` aceleradas con caché en memoria **Caffeine**.

---

## 1. Requisitos Previos

- **Java:** JDK 21 o 22 (probado con Java 22).
- **Base de Datos:** Oracle Database (ej. `gvenzl/oracle-free:23-slim`).
- **Broker Kafka:** Apache Kafka (puerto por defecto `localhost:9092`).

---

## 2. Variables de Entorno y Configuración

El servicio utiliza las siguientes variables de entorno (con valores por defecto para desarrollo local definidos en `application.yml`):

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de conexión Oracle | `jdbc:oracle:thin:@localhost:1521/FREEPDB1` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `SYSTEM` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos | `password` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Servidores bootstrap del clúster Kafka | `localhost:9092` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Issuer URI del proveedor JWT (Azure AD / Keycloak) | `http://localhost:8080/realms/rutaexpress` |

---

## 3. Comandos de Ejecución

Todos los comandos se ejecutan desde el directorio `ms-rutaexpress-report/`:

### Compilar el proyecto
```bash
# Windows
.\gradlew.bat build -x test

# Linux / macOS
./gradlew build -x test
```

### Ejecutar la suite de pruebas unitarias
```bash
# Windows
.\gradlew.bat test

# Linux / macOS
./gradlew test
```

### Iniciar el microservicio en local
```bash
# Windows
.\gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```
El servicio inicia en el puerto **`8084`**.

---

## 4. Endpoints Expuestos

### Endpoints Analíticos (Protegidos con JWT Bearer Token)

Requiere token JWT válido con rol `ADMIN` o autenticado.

#### 1. Resumen de KPIs operacionales
- **Método:** `GET`
- **Ruta:** `/api/report/kpis`
- **Query Params:** `range` (opcional: `last1h`, `last24h`, `last7d`, `last30d`. Por defecto: `last24h`).
- **Respuesta de ejemplo:**
```json
{
  "range": "last24h",
  "totalShipments": 120,
  "shipmentsPerHour": 5.0,
  "avgLeadTimeMinutes": 45.5,
  "activeShipmentsByStatus": {
    "CREADO": 10,
    "ACEPTADO": 15,
    "EN_BODEGA": 25,
    "EN_RUTA": 30,
    "ENTREGADO": 40
  }
}
```

#### 2. Servicios más demandados
- **Método:** `GET`
- **Ruta:** `/api/report/top-services`
- **Query Params:** `range` (opcional: `last7d`, `last30d`. Por defecto: `last7d`).
- **Respuesta de ejemplo:**
```json
[
  {
    "serviceType": "EXPRESS",
    "totalShipments": 60,
    "percentage": 60.0
  },
  {
    "serviceType": "ESTANDAR",
    "totalShipments": 40,
    "percentage": 40.0
  }
]
```

### Endpoints Públicos (Documentación y Observabilidad)

- **Swagger UI:** `http://localhost:8084/swagger-ui.html`
- **OpenAPI v3 JSON:** `http://localhost:8084/v3/api-docs`
- **Health Check:** `http://localhost:8084/actuator/health`
- **Métricas Prometheus:** `http://localhost:8084/actuator/prometheus`

---

## 5. Integración con Kafka

El servicio escucha eventos del ciclo logístico en el tópico:

- **Tópico:** `shipments.events`
- **Consumer Group:** `ms-rutaexpress-report-group`
- **Formato esperado del mensaje:**
```json
{
  "eventId": "evt-1001",
  "shipmentId": "envio-9988",
  "serviceType": "EXPRESS",
  "status": "ENTREGADO",
  "timestamp": "2026-09-13T20:30:00Z"
}
```
*Estados reconocidos:* `CREADO`, `ACEPTADO`, `EN_BODEGA`, `EN_RUTA`, `ENTREGADO`, `CANCELADO`. Al recibir `ENTREGADO`, el consumidor calcula automáticamente el `lead_time_minutes` contra la fecha de creación.

---

## 6. Base de Datos y Caché

- **Migraciones Flyway:** El esquema se crea automáticamente mediante `src/main/resources/db/migration/V1__crear_tablas_reportes.sql` en la tabla `TB_REPORT_SHIPMENTS`.
- **Caché en Memoria:** Las consultas analíticas se almacenan en caché local **Caffeine** con expiración de 60 segundos para evitar saturación de base de datos bajo tráfico concurrente.
