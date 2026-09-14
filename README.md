# RutaExpress — Monorepo de Microservicios Backend

Repositorio centralizado con los microservicios backend de la plataforma **RutaExpress**, implementados bajo una arquitectura orientada a eventos (*Event-Driven Architecture*) y microservicios con **Kotlin** y **Spring Boot**.

---

## Estructura del Repositorio

Cada microservicio se encuentra aislado en su respectiva carpeta, con su propio ciclo de compilación Gradle, suite de pruebas automatizadas, configuración y documentación específica:

```text
Proyecto-cloud-native/
├── ms-rutaexpress-audit/       # Microservicio de Auditoría y Timeline
├── ms-rutaexpress-catalog/     # Microservicio de Catálogo y Capacidad de Flota
├── ms-rutaexpress-notify/      # Microservicio de Notificaciones y Tickets
├── ms-rutaexpress-report/      # Microservicio de Reportes y KPIs
└── docs/                       # Documentación arquitectónica y pautas
```

---

## Resumen de Microservicios

| Microservicio | Carpeta | Dominio & Responsabilidad | Persistencia / Broker | Tecnologías Principales |
|---|---|---|---|---|
| **Audit** | [`ms-rutaexpress-audit`](./ms-rutaexpress-audit) | Ingesta y registro inmutable de eventos logísticos en línea de tiempo (`/api/audit/*`). | Oracle Database / Kafka Consumer | Spring Boot, Spring Data JPA, Flyway, Kafka |
| **Catalog** | [`ms-rutaexpress-catalog`](./ms-rutaexpress-catalog) | Gestión de servicios, tarifas zonales y reserva/liberación de capacidad de flota (`/api/catalog/*`). | Oracle Database / Kafka Producer | Spring Boot, Spring Data JPA, Caffeine Cache, Kafka, Flyway |
| **Notify** | [`ms-rutaexpress-notify`](./ms-rutaexpress-notify) | Consumo de comandos AMQP, envío de correos, generación de etiquetas/tickets PDF e idempotencia. | Redis / RabbitMQ Consumer | Spring Boot, Spring AMQP, Redis, Mailpit, OpenPDF |
| **Report** | [`ms-rutaexpress-report`](./ms-rutaexpress-report) | Agregación de métricas de desempeño, tiempos de entrega y generación de KPIs (`/api/report/*`). | Oracle Database / Kafka Consumer | Spring Boot, Spring Data JPA, Caffeine Cache, Kafka, Flyway |

---

## Requisitos de Entorno Comunes

- **Java Development Kit (JDK):** Versión 21 o superior instalada.
- **Gradle:** Incluido en cada microservicio mediante Gradle Wrapper (`./gradlew` o `gradlew.bat`).
- **Infraestructura de soporte:**
  - **Oracle Database:** Instancia Oracle Free / XE para persistencia relacional.
  - **Apache Kafka:** Broker de mensajería para eventos de dominio y analítica.
  - **RabbitMQ:** Broker AMQP para comandos operativos y notificaciones.
  - **Redis:** Servidor clave-valor para almacén de idempotencia.
  - **Mail Server (Mailpit):** Servidor SMTP para captura y pruebas de correos.

---

## Compilación y Ejecución

Cada microservicio puede compilarse y ejecutarse de forma independiente desde su respectiva carpeta.

### Ejecutar Pruebas Automatizadas
```bash
# Auditoría
cd ms-rutaexpress-audit && ./gradlew test

# Catálogo
cd ../ms-rutaexpress-catalog && ./gradlew test

# Notificaciones
cd ../ms-rutaexpress-notify && ./gradlew test

# Reportes
cd ../ms-rutaexpress-report && ./gradlew test
```

### Ejecutar Localmente un Microservicio
```bash
cd <nombre-microservicio>
./gradlew bootRun
```

---

## Documentación Adicional

- [`docs/dependencias-microservicios.md`](./docs/dependencias-microservicios.md): Matriz de dependencias y versiones transversales de la arquitectura.
- [`docs/EP1_DSY1107_Estudiante_encargo.pdf`](./docs/EP1_DSY1107_Estudiante_encargo.pdf): Pauta y requerimientos de la evaluación.
- Revise el archivo `README.md` dentro de cada directorio de microservicio para conocer endpoints, esquemas Flyway, variables de entorno y ejemplos de peticiones.
