# RutaExpress — Dependencias por microservicio

Derivado del caso **"Caso 3 - RutaExpress"**, apartado **5. Microservicios de dominio** (5 servicios) más los 3 servicios adicionales que la pauta exige incluir (BFF, administrador RabbitMQ, administrador Kafka).

Solo se listan **nombres de dependencias** (`groupId:artifactId`). Las versiones van en el apartado 1 y en las notas finales.

---

## 1. Versiones de referencia (línea base común)

| Componente | Versión |
|---|---|
| Spring Boot (`spring-boot-starter-parent`) | 4.1.1 |
| Java | 21 LTS (Boot 4.1 admite 17–26) |
| Spring Framework | 7.0.9 |
| Spring Security | 7.1.1 |
| Spring AMQP / `amqp-client` | 4.1.1 / 5.30.0 |
| Spring Kafka / `kafka-clients` | 4.1.1 / 4.2.1 |
| Hibernate ORM / HikariCP | 7.4.5 / 7.0.2 |
| `com.oracle.database.jdbc:ojdbc11` | 23.26.3.0.0 |
| Flyway | 12.4.0 |
| Jackson | 3.1.5 |
| Micrometer | 1.17.1 |
| Lombok | 1.18.46 |
| Testcontainers | 2.0.5 |
| `org.mapstruct:mapstruct` (+ `mapstruct-processor`, `lombok-mapstruct-binding`) | 1.6.3 / 1.6.3 / 0.2.0 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.1.1 |
| `io.github.resilience4j:resilience4j-spring-boot4` | 2.4.0 |
| `org.springframework.retry:spring-retry` | 2.0.13 |
| `com.google.firebase:firebase-admin` | 9.10.0 |
| `com.github.librepdf:openpdf` / `org.apache.pdfbox:pdfbox` | 3.0.5 / 3.0.8 |
| `nl.martijndwars:web-push` | 5.1.2 |
| `io.micrometer:micrometer-registry-prometheus` | 1.17.1 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.4 |
| `org.apache.kafka:kafka-streams` | 4.2.1 |

Las versiones que no aparecen arriba las gestiona el BOM de Spring Boot 4.1.1 y no se declaran.

---

## 2. Dependencias transversales (servicios REST)

Aplican a `ms-rutaexpress-shipments`, `ms-rutaexpress-catalog`, `ms-rutaexpress-audit`, `ms-rutaexpress-report`, `ms-rutaexpress-bff`, `ms-rutaexpress-mq-admin`, `ms-rutaexpress-kafka-admin`:

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`
- `org.mapstruct:mapstruct`
- `org.mapstruct:mapstruct-processor`
- `org.projectlombok:lombok-mapstruct-binding`

Pruebas comunes:

- `org.springframework.boot:spring-boot-starter-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`

---

## 3. `ms-rutaexpress-shipments`

Dominio envíos · Oracle · `/api/shipments/*` · publica RabbitMQ y Kafka, consulta capacidad a catalog.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `com.oracle.database.jdbc:ojdbc11`
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-oracle`
- `org.springframework.boot:spring-boot-starter-amqp`
- `org.springframework.boot:spring-boot-starter-kafka`
- `org.springframework.boot:spring-boot-starter-restclient`
- `io.github.resilience4j:resilience4j-spring-boot4`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`
- `org.mapstruct:mapstruct`
- `org.mapstruct:mapstruct-processor`
- `org.projectlombok:lombok-mapstruct-binding`
- `org.springframework.boot:spring-boot-devtools`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.springframework.boot:spring-boot-starter-security-test`
- `org.springframework.boot:spring-boot-starter-data-jpa-test`
- `org.springframework.boot:spring-boot-starter-kafka-test`
- `org.springframework.boot:spring-boot-starter-amqp-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-oracle-free`
- `org.testcontainers:testcontainers-kafka`
- `org.testcontainers:testcontainers-rabbitmq`

---

## 4. `ms-rutaexpress-catalog`

Dominio servicios/flota · Oracle · `/api/catalog/*` · CRUD de servicios, tarifas y capacidad.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `com.oracle.database.jdbc:ojdbc11`
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-oracle`
- `org.springframework.boot:spring-boot-starter-cache`
- `com.github.ben-manes.caffeine:caffeine`
- `org.springframework.boot:spring-boot-starter-kafka`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`
- `org.mapstruct:mapstruct`
- `org.mapstruct:mapstruct-processor`
- `org.projectlombok:lombok-mapstruct-binding`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.springframework.boot:spring-boot-starter-security-test`
- `org.springframework.boot:spring-boot-starter-data-jpa-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-oracle-free`

---

## 5. `ms-rutaexpress-notify`

Dominio notificaciones · sin DB · no público · consumidor RabbitMQ (`q.cmd.email`, `q.cmd.warehouse`, `q.cmd.label` + DLQ).

- `org.springframework.boot:spring-boot-starter-amqp`
- `org.springframework.retry:spring-retry`
- `org.springframework.boot:spring-boot-starter-mail`
- `com.google.firebase:firebase-admin`
- `com.github.librepdf:openpdf`
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.projectlombok:lombok`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-amqp-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-rabbitmq`

Alternativas: `org.apache.pdfbox:pdfbox` en lugar de OpenPDF; `nl.martijndwars:web-push` en lugar de Firebase Admin.

---

## 6. `ms-rutaexpress-audit`

Dominio auditoría/timeline · Oracle · `/api/audit/*` (read-only) · consumidor Kafka.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `com.oracle.database.jdbc:ojdbc11`
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-oracle`
- `org.springframework.boot:spring-boot-starter-kafka`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`
- `org.mapstruct:mapstruct`
- `org.mapstruct:mapstruct-processor`
- `org.projectlombok:lombok-mapstruct-binding`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.springframework.boot:spring-boot-starter-security-test`
- `org.springframework.boot:spring-boot-starter-data-jpa-test`
- `org.springframework.boot:spring-boot-starter-kafka-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-oracle-free`
- `org.testcontainers:testcontainers-kafka`

---

## 7. `ms-rutaexpress-report`

Dominio KPIs/analytics · Oracle · `/api/report/*` (read-only) · consumidor Kafka.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-jdbc`
- `com.oracle.database.jdbc:ojdbc11`
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-oracle`
- `org.springframework.boot:spring-boot-starter-kafka`
- `org.apache.kafka:kafka-streams`
- `org.springframework.boot:spring-boot-starter-cache`
- `com.github.ben-manes.caffeine:caffeine`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.springframework.boot:spring-boot-starter-security-test`
- `org.springframework.boot:spring-boot-starter-jdbc-test`
- `org.springframework.boot:spring-boot-starter-kafka-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-oracle-free`
- `org.testcontainers:testcontainers-kafka`

Alternativa: `org.springframework.boot:spring-boot-starter-data-jpa` en lugar de `spring-boot-starter-jdbc` para igualar a los demás servicios.

---

## 8. Servicios adicionales exigidos por la pauta

### 8.1 `ms-rutaexpress-bff`

BFF detrás del API Gateway (JWT → Gateway → BFF → dominio), sin base de datos.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-security-oauth2-client`
- `org.springframework.boot:spring-boot-starter-restclient`
- `org.springframework.boot:spring-boot-starter-cache`
- `com.github.ben-manes.caffeine:caffeine`
- `io.github.resilience4j:resilience4j-spring-boot4`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.springframework.boot:spring-boot-starter-security-test`
- `org.springframework.security:spring-security-test`

Sin JPA, sin `ojdbc11`, sin Flyway y sin Spring Cloud Gateway (el gateway real es AWS API Gateway).

### 8.2 `ms-rutaexpress-mq-admin`

Administra exchanges/colas/DLQ y el estado del clúster RabbitMQ.

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-amqp`
- `org.springframework.boot:spring-boot-starter-restclient`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-amqp-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-rabbitmq`

### 8.3 `ms-rutaexpress-kafka-admin`

Administra tópicos y particiones (`shipments.events`, `audit.timeline`, `*.DLT`).

- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`
- `org.springframework.boot:spring-boot-starter-kafka`
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.projectlombok:lombok`

Pruebas:

- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-kafka-test`
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-kafka`

---

## 9. Imágenes para los compose (EC2)

| Compose | Imagen |
|---|---|
| `apps` | `eclipse-temurin:21-jre` |
| `mq` | `rabbitmq:4-management` |
| `kafka` (KRaft) | `apache/kafka:4.2.1` |
| `kafka` (con ZooKeeper) | `apache/kafka:3.9.2` + `zookeeper:3.9` + `provectuslabs/kafka-ui` |
| DB local | `gvenzl/oracle-free:23-slim` |

---

## 10. Notas

1. **Kafka con ZooKeeper vs Kafka 4.x.** Kafka 4.0 eliminó ZooKeeper (KRaft por defecto). Si la pauta exige "Zookeeper (3 nodos) + Kafka (3 brokers)", fijar Kafka 3.9.x + ZooKeeper 3.9.x; si se acepta KRaft, usar Kafka 4.x. El código Java no cambia en ninguno de los dos casos (kafka-clients 4.2.1 es compatible con brokers ≥ 2.1).
2. **Renombres en Spring Boot 4.** `spring-boot-starter-web` → `spring-boot-starter-webmvc`; `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`; `spring-boot-starter-oauth2-client` → `spring-boot-starter-security-oauth2-client`; los starters de test se dividen por tecnología (`-webmvc-test`, `-security-test`, `-data-jpa-test`, `-jdbc-test`, `-kafka-test`, `-amqp-test`).
3. **`spring-retry` ya no lo gestiona el BOM de Boot 4.1** → versión explícita 2.0.13 en los servicios con reintentos AMQP (`notify`, `mq-admin`). Los reintentos Kafka no la necesitan.
4. **Testcontainers 2.0.5** renombró los módulos a `testcontainers-*` (los nombres antiguos `oracle-free`, `kafka`, `rabbitmq` ya no existen).
5. **springdoc 3.x** es la línea para Boot 4 (la 2.x es para Boot 3).
6. **Jackson 3** es el gestionado por Boot 4.1; `firebase-admin` depende de Jackson 2 — evitar mezclar anotaciones en los DTOs propios.
7. **Propiedad del issuer:** el caso escribe `security.oauth2.resourceserver.jwt.issuer-uri`; la correcta es `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
8. **Roles de Azure AD:** el claim `roles` requiere un `JwtAuthenticationConverter` propio y `@EnableMethodSecurity`; no añade dependencias.
9. **Errata del documento:** el apartado 6 menciona `ms-campuslab-bff`; los apartados 5 y 7 definen `ms-rutaexpress-bff`.
10. **Padre común recomendado:** un `pom.xml` `rutaexpress-parent` con `dependencyManagement` y `pluginManagement` heredado por los 8 repos, para no desincronizar versiones.

---

### Fuentes verificadas (2026-09-10)

- Las 35 coordenadas Maven listadas respondieron `200` en Maven Central.
- Tags Docker comprobados en Docker Hub: `eclipse-temurin:21-jre`, `rabbitmq:4-management`, `gvenzl/oracle-free:23-slim`, `provectuslabs/kafka-ui:latest`, `apache/kafka` (4.2.1, 4.3.1, 3.9.2), `zookeeper` (3.9, 3.9.5).
- Spring Boot: [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) y [endoflife.date/api/spring-boot.json](https://endoflife.date/api/spring-boot.json).
- Apache Kafka 4.0: [anuncio oficial](https://kafka.apache.org/blog/2025/03/18/apache-kafka-4.0.0-release-announcement/) (eliminación de ZooKeeper, KIP-896, mínimos de Java).
