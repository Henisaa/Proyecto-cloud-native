# ms-rutaexpress-audit

Microservicio de auditoría y línea de tiempo (*timeline*) para la plataforma RutaExpress, desarrollado en **Kotlin 2.1** y **Spring Boot 3.4**.  
Encargado de ingestar eventos desde Kafka, persistirlos en Oracle y exponer endpoints de solo lectura (`/api/audit/*`).

---

## Requisitos Previos

- **Java:** JDK 21 o 22 instalado.
- **Base de Datos:** Oracle Database (ej. `gvenzl/oracle-free:23-slim`).
- **Broker Kafka:** Apache Kafka (puerto por defecto `localhost:9092`).

---

## Variables de Entorno y Configuración

El servicio utiliza las siguientes variables (con valores por defecto para desarrollo local en `application.yml`):

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de Oracle | `jdbc:oracle:thin:@localhost:1521/FREEPDB1` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `SYSTEM` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos | `password` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Servidores bootstrap de Kafka | `localhost:9092` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Issuer JWT (Keycloak / Azure AD) | `http://localhost:8080/realms/rutaexpress` |

---

## Ejecución

### 1. Compilar y ejecutar pruebas
```bash
./gradlew test
```

### 2. Iniciar el servicio localmente
```bash
./gradlew bootRun
```
El servicio iniciará en el puerto **8083**.

---

## Endpoints Principales

### Endpoints de Negocio (Protegidos con JWT)
- `GET /api/audit`: Lista todos los eventos registrados.
- `GET /api/audit/entidad/{idEntidad}`: Historial y línea de tiempo por identificador de entidad (ej: ID de envío).
- `GET /api/audit/tipo/{tipoEntidad}`: Eventos filtrados por tipo de entidad.

### Endpoints Públicos (Documentación y Salud)
- **Swagger UI:** `http://localhost:8083/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8083/v3/api-docs`
- **Health Check:** `http://localhost:8083/actuator/health`
- **Métricas Prometheus:** `http://localhost:8083/actuator/prometheus`

---

## Tópicos Kafka Consumidos

- `envios-events`: Ingesta cambios de estado y acciones sobre los envíos.
- `auditoria-topic`: Tópico genérico para eventos directos de auditoría.
- **Consumer Group:** `ms-rutaexpress-audit-group`

## La anterior carpeta (demo) fue renombrada

ahora la carpeta base de el proyecto es ms-rutaexpress-audit y tiene la estructura correcta de un proyecto Kotlin Spring Boot.
