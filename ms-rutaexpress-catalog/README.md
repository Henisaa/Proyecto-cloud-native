# RutaExpress — Microservicio de Catálogo (`ms-rutaexpress-catalog`)
## Documentación Técnica de Arquitectura, Fundamentos, Decisiones y Roadmap

---

## 1. Resumen del Proyecto

El microservicio **`ms-rutaexpress-catalog`** es el componente de dominio encargado de gestionar el **catálogo de servicios logísticos, tarifas zonales y capacidad operativa de la flota de vehículos** para la plataforma de couriers de última milla **RutaExpress**.

Este servicio fue construido íntegramente en **Kotlin con Spring Boot 4.1.1 (Java 25 / Spring Framework 7)** siguiendo el patrón arquitectónico **MVVM (Model-View-ViewModel)**, desacoplando el modelo relacional de base de datos de los contratos expuestos a clientes y otros microservicios mediante ViewModels enriquecidos con lógica de presentación y cálculos de capacidad en tiempo real.

---

## 2. Bases y Fundamentos Documentales

El diseño, dependencias y reglas de negocio del microservicio se fundamentan en tres documentos clave del proyecto:

### 2.1. Caso 3 — RutaExpress: Plataforma para envíos de última milla (`Caso 3 - RutaExpress.docx`)
* **Contexto de Negocio:** Una red de 20 couriers PyME que operaba de manera descentralizada requiere una plataforma unificada para registrar envíos web, controlar la capacidad de flota (motocicletas, camionetas, camiones), calcular tarifas y coordinar despachos.
* **Alcance del Catálogo (Apartado 5):**
  * `GET /api/catalog/services`: Listado de servicios comerciales disponibles.
  * `POST /api/catalog/services`: Registro de nuevos servicios de transporte.
  * `PUT /api/catalog/services/{id}`: Modificación de parámetros de servicio, tarifas y capacidad.
  * **Interacción clave con Envíos:** El microservicio `ms-rutaexpress-shipments` debe consultar la capacidad de flota a `ms-rutaexpress-catalog` antes de admitir un nuevo despacho.
* **Seguridad y Flujo:** Llamadas autenticadas con JWT emitido por Azure AD (IDaaS) a través de AWS API Gateway y el BFF (`ms-rutaexpress-bff`), validando roles y protegiendo los endpoints.
* **Mensajería y Eventos:** Publicación asíncrona de eventos a tópicos de Kafka (`catalog.events`) bajo un envelope estándar (*type*, *eventId*, *timestamp*, *traceId*, *correlationId*).

### 2.2. Pauta de Evaluación Parcial N°1 (`EP1_DSY1107_Estudiante_encargo.pdf`)
* **Criterio Backend y Compilación:** Código compilable sin fallas, modular, siguiendo buenas prácticas y con suite de pruebas automatizadas funcionales.
* **Criterio de Base de Datos Cloud:** Integración con base de datos mediante entidades JPA, repositorios Spring Data y propiedades de conexión externalizadas para despliegues cloud.
* **Criterio de Seguridad (IDaaS & JWT):**
  * Validación estricta del token JWT emitido por el IDaaS (Azure AD).
  * Comprobación del emisor (*issuer*) y audiencia (*audience*).
  * Verificación de vigencia y firma criptográfica.
  * Autorización basada en roles (`ADMIN`, `OPERADOR`, `CLIENTE`) leídos directamente de los claims del token.
  * Códigos de error HTTP consistentes (401 Unauthorized, 403 Forbidden, 400 Bad Request, 404 Not Found).
* **Criterio de Repositorio:** Subir únicamente archivos de código y configuración esenciales, con archivos `.gitignore` que excluyan compilados, cachés y carpetas de IDEs.

### 2.3. Dependencias Oficiales (`dependencias-microservicios.md`)
* Coordenadas exactas definidas para `ms-rutaexpress-catalog` (Apartado 4):
  * `spring-boot-starter-webmvc`
  * `spring-boot-starter-validation`
  * `spring-boot-starter-security-oauth2-resource-server`
  * `spring-boot-starter-data-jpa`
  * `com.oracle.database.jdbc:ojdbc11:23.26.3.0.0`
  * `org.flywaydb:flyway-core` y `org.flywaydb:flyway-database-oracle`
  * `spring-boot-starter-cache` con `com.github.ben-manes.caffeine:caffeine:3.2.4`
  * `spring-boot-starter-kafka`
  * `spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus`
  * `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`

---

## 3. El Porqué y la Justificación de las Decisiones de Diseño

### 3.1. ¿Por qué Kotlin y Spring Boot sin cambiar a Java?
* **Cumplimiento estricto del requerimiento:** El usuario instruyó taxativamente: *"no quiero que cambies a Java, el backend va a ser si o si en kotlin spring boot"*.
* **Beneficios técnicos:**
  * **Null Safety:** Prevención en tiempo de compilación de errores de puntero nulo (`NullPointerException`).
  * **Data Classes:** Creación limpia de DTOs, ViewModels y eventos sin boilerplate.
  * **Extensiones y concisión:** Código más expresivo y mantenible manteniendo 100% de compatibilidad con el ecosistema Spring Boot.

### 3.2. ¿Por qué el patrón MVVM en un microservicio de backend?
Tradicionalmente en backend se utiliza MVC o Arquitectura en Capas. Sin embargo, la instrucción explícita fue implementar **MVVM**:
* **Model (Modelo de Persistencia):**
  * Entidades JPA (`CatalogService`, `FleetCapacity`, `Tariff`) que mapean fielmente las tablas de Oracle Database 23.
  * Mantienen integridad referencial, índices y ciclos de vida JPA.
* **ViewModel (Modelo de Presentación y Estado):**
  * Los ViewModels (`CreateServiceViewModel`, `ServiceDetailViewModel`, `CapacityCheckResultViewModel`, etc.) representan el estado y los contratos de datos de la API.
  * El **`CatalogViewModelService`** actúa como el procesador central del ViewModel:
    * Realiza el binding y las validaciones de negocio.
    * Calcula métricas dinámicas de presentación (tasa de ocupación de flota, porcentaje de disponibilidad, tarifas estimadas).
    * Gestiona la caché en memoria (Caffeine).
    * Publica eventos de negocio hacia Kafka sin que el Controller ni la entidad se acoplen a la mensajería.
* **View (Capa de Exposición REST):**
  * Los controladores (`CatalogViewController`) actúan como la vista receptora y despachadora, aceptando ViewModels de entrada (`RequestViewModel`) y retornando ViewModels de salida (`ResponseViewModel`) serializados a JSON.
  * El `GlobalExceptionHandler` unifica las vistas de error en un estándar corporativo `ErrorResponseViewModel`.

### 3.3. ¿Por qué la estrategia de seguridad con Azure AD (IDaaS)?
* Para dar cumplimiento al 100% de la pauta de evaluación:
  * Se configuró `spring.security.oauth2.resourceserver.jwt.issuer-uri` apuntando a `https://login.microsoftonline.com/<TENANT_ID>/v2.0`.
  * Se implementó un validador de audiencia personalizado (`AudienceValidator`) que comprueba que el claim `aud` del token contenga `api://rutaexpress-catalog`.
  * Se desarrolló `AzureJwtAuthenticationConverter` para transformar el arreglo de roles de Azure AD (`roles: ["Admin", "Operador", "Cliente"]`) en `GrantedAuthority` de Spring Security con prefijo `ROLE_` (`ROLE_ADMIN`, `ROLE_OPERADOR`, `ROLE_CLIENTE`).
  * Se activó `@EnableMethodSecurity` para que cada endpoint valide granularmente los permisos (`hasRole('ADMIN')`, `hasAnyRole('ADMIN', 'OPERADOR')`, etc.).

### 3.4. ¿Por qué Oracle Database 23 con Flyway y perfil H2 para pruebas?
* **Oracle 23ai (FreePDB1):** Exigido por el caso para los servicios de dominio desplegados en AWS EC2 con Docker.
* **Flyway:** Permite control de versiones del esquema DDL (`V1__init_catalog_schema.sql`), garantizando que la creación de tablas, índices y datos semilla se ejecute de forma reproducible.
* **Perfil H2 (`application-test.yml`):** Permite que los desarrolladores y los pipelines de integración continua ejecuten `./gradlew test` en cualquier máquina sin necesidad de levantar previamente contenedores pesados de Oracle o Kafka.

---

## 4. Cómo se Llegó a las Conclusiones y Solución de Desafíos Técnicos

Durante la construcción del microservicio surgieron desafíos de integración específicos de las versiones de última generación (Spring Boot 4.1, Spring Framework 7, JDK 25):

1. **Soporte de Toolchain de Java en la máquina local:**
   * *Problema:* El archivo inicial tenía `JavaLanguageVersion.of(21)`, pero el entorno del sistema tenía instalado JDK 25 (`jdk-25.0.4`).
   * *Solución:* Se actualizó `build.gradle.kts` a `languageVersion = JavaLanguageVersion.of(25)`, compatible nativamente con Spring Boot 4.1.
2. **Solución a `MultipleBagFetchException` en JPA/Hibernate:**
   * *Problema:* `CatalogService` tenía dos colecciones `@OneToMany` tipo `MutableList` (`capacities` y `tariffs`). Al ejecutar un `JOIN FETCH` simultáneo en `findByIdWithDetails`, Hibernate arrojaba `MultipleBagFetchException`.
   * *Solución:* Se transformaron las colecciones a `MutableSet<FleetCapacity>` y `MutableSet<Tariff>`, otorgando semántica de conjunto único que permite múltiples *fetch joins* eficientes sin duplicación cartesiana.
3. **Resolución de tipos genéricos en `Converter` de Spring Security:**
   * *Problema:* Al definir el convertidor de JWT como lambda anónima (`Converter { jwt -> ... }`), la introspección de tipos de Spring 7 arrojaba `IllegalArgumentException: Unable to determine source type <S> and target type <T>`.
   * *Solución:* Se implementó la clase concreta `class AzureJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken>`, preservando los metadatos de tipos en el bytecode para el contenedor de Spring.
4. **Desacoplamiento de Kafka en entornos de prueba:**
   * *Problema:* Al ejecutar pruebas unitarias locales sin un broker de Kafka en `localhost:9092`, el cliente intentaba reconectarse indefinidamente generando lentitud.
   * *Solución:* Se integró una bandera condicional `app.kafka.enabled` y exclusión de `KafkaAutoConfiguration` en `application-test.yml`, haciendo que `CatalogEventPublisher` omita la publicación durante pruebas sin afectar la lógica del servicio.
5. **Endpoints de Capacidad para `ms-rutaexpress-shipments`:**
   * *Conclusión:* El caso especifica que envíos consulta capacidad a catálogo. Por tanto, no bastaba con un simple CRUD; se diseñaron los endpoints especializados:
     * `POST /api/catalog/services/{id}/capacity/check`: Comprueba si hay capacidad disponible según zona, peso (kg) y volumen (m³).
     * `POST /api/catalog/services/{id}/capacity/reserve`: Bloquea e incrementa los paquetes reservados al crear un envío.
     * `POST /api/catalog/services/{id}/capacity/release`: Libera los cupos en caso de anulación de envío.

---

## 5. Lo que se Construyó e Implementó

### 5.1. Entidades del Dominio (Model)
* [`CatalogService`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/model/entity/CatalogService.kt): Código único, nombre, descripción, categoría (`STANDARD`, `EXPRESS`, `SAME_DAY`, `COLD_CHAIN`, `FRAGILE`), estado (`ACTIVE`, `INACTIVE`, `SUSPENDED`), tiempos estimados, precios base y listas asociadas.
* [`FleetCapacity`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/model/entity/FleetCapacity.kt): Cupo diario máximo, paquetes reservados, límites de peso (kg), volumen (m³), tipo de vehículo (`MOTORCYCLE`, `VAN`, `TRUCK`, `ELECTRIC`) y zona geográfica (`URBAN_CENTER`, `URBAN_PERIPHERY`, `RURAL`, `INTERURBAN`).
* [`Tariff`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/model/entity/Tariff.kt): Tarifas zonales con tarifa base, costo por kilómetro, costo por kilogramo y tarifa mínima garantizada.

### 5.2. Lógica y Transformaciones (ViewModel)
* [`RequestViewModels`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/viewmodel/request/RequestViewModels.kt): DTOs de entrada con validaciones Jakarta (`@NotBlank`, `@Size`, `@PositiveOrZero`, `@Min`).
* [`ResponseViewModels`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/viewmodel/response/ResponseViewModels.kt): DTOs de salida con propiedades calculadas (`utilizationPercentage`, `availablePackages`, `isFull`, `isAvailable`).
* [`CatalogViewModelService`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/viewmodel/service/CatalogViewModelService.kt): Orquestación de lógica, caché `@Cacheable`, transacciones `@Transactional`, emisión de eventos Kafka y validación de reglas de capacidad.

### 5.3. Controladores y Manejo de Errores (View)
* [`CatalogViewController`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/view/controller/CatalogViewController.kt): 16 endpoints documentados con Swagger OpenAPI 3 y protegidos por roles.
* [`GlobalExceptionHandler`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/kotlin/com/rutaexpress/catalog/view/controller/GlobalExceptionHandler.kt): Intercepción centralizada de excepciones (`NoSuchElementException` -> 404, `MethodArgumentNotValidException` -> 400, `AccessDeniedException` -> 403, `IllegalStateException` -> 409).

### 5.4. Migración de Base de Datos
* [`V1__init_catalog_schema.sql`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/main/resources/db/migration/V1__init_catalog_schema.sql): DDL completo con índices y datos semilla de servicios (`SRV-EXP-01`, `SRV-STD-01`, `SRV-FRG-01`), capacidades de flota y matrices de tarifas zonales.

### 5.5. Suite de Pruebas Automatizadas (18/18 pruebas exitosas)
* [`CatalogApplicationTests`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/test/kotlin/com/rutaexpress/catalog/CatalogApplicationTests.kt): Verificación de carga de contexto.
* [`SecurityAudienceAndRolesTests`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/test/kotlin/com/rutaexpress/catalog/config/SecurityAudienceAndRolesTests.kt): Prueba de validación de audiencia (`AudienceValidator`) y mapeo de claims `roles` y `scp` de Azure AD.
* [`CatalogViewModelServiceTests`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/test/kotlin/com/rutaexpress/catalog/viewmodel/service/CatalogViewModelServiceTests.kt): Pruebas de reglas de negocio, disponibilidad de cupos, sobrecupos y tarifas.
* [`CatalogViewControllerTests`](file:///C:/Users/Enrique/Documents/GitHub/Proyecto-cloud-native/demo/src/test/kotlin/com/rutaexpress/catalog/view/controller/CatalogViewControllerTests.kt): Pruebas de integración MockMvc con usuarios simulados `@WithMockUser`, validando accesos permitidos y rechazo de accesos no autorizados (403 Forbidden para rol `CLIENTE` intentando crear servicios).

---

## 6. Qué Hace Falta por Implementar (Roadmap y Pendientes del Proyecto)

Para completar la solución global de **RutaExpress** según el Caso 3 y las pautas evaluativas del semestre, resta desarrollar los siguientes componentes:

### 6.1. En el Microservicio de Catálogo (`ms-rutaexpress-catalog`):
1. **Despliegue Contenerizado:**
   * Crear el `Dockerfile` multistage basado en `eclipse-temurin:25-jre` o `eclipse-temurin:21-jre`.
   * Incluir el servicio dentro de `infra/apps/compose.yml` para despliegue en la instancia EC2 (`ec2-apps`).
2. **Pruebas de Integración End-to-End con Azure AD real:**
   * Configurar un App Registration real en Azure Portal y realizar pruebas con tokens emitidos por Microsoft Identity.
3. **Consumo de eventos de actualización externa:**
   * Si otros microservicios (como `shipments`) notifican incidentes de vehículos, habilitar un Kafka Listener en catálogo para marcar capacidades en estado degradado o suspendido.

### 6.2. En los Demás Microservicios del Ecosistema RutaExpress:
1. **Frontend Angular (`/frontend-rutaexpress`):**
   * Integración de `@azure/msal-browser` y `@azure/msal-angular`.
   * Configuración de Guards de ruta basados en roles (`Admin`, `Operador`, `Cliente`).
   * `MsalInterceptor` para adjuntar automáticamente el token `Bearer` a las peticiones hacia el API Gateway.
   * Vistas para creación de envíos, consulta de catálogo y monitoreo de flota.
2. **Backend for Frontend (`ms-rutaexpress-bff`):**
   * Microservicio Spring Boot + Spring Security situado detrás de AWS API Gateway.
   * Valida el token JWT de Azure AD y actúa como fachada agregadora consumiendo `ms-rutaexpress-catalog` y `ms-rutaexpress-shipments`.
3. **Microservicio de Envíos (`ms-rutaexpress-shipments`):**
   * Dominio principal de envíos con base de datos Oracle (`/api/shipments/*`).
   * Integración con `ms-rutaexpress-catalog` mediante `RestClient` con Resilience4j para consultar y reservar capacidad antes de confirmar un despacho.
   * Publicación de comandos a RabbitMQ (`cmd.direct`) para tickets de bodega y notificaciones.
   * Publicación de eventos a Kafka (`shipments.events`) para analítica y auditoría.
4. **Microservicio de Notificaciones (`ms-rutaexpress-notify`):**
   * Microservicio sin base de datos que consume de RabbitMQ (`q.cmd.email`, `q.cmd.warehouse`, `q.cmd.label` + DLQs).
   * Generación de comprobantes PDF y envío de correos/notificaciones push.
5. **Microservicio de Auditoría (`ms-rutaexpress-audit`):**
   * Consumidor Kafka que almacena el timeline inmutable de eventos logísticos en base de datos.
6. **Microservicio de Reportes y KPIs (`ms-rutaexpress-report`):**
   * Consumidor Kafka y Kafka Streams para métricas en tiempo real (lead time, envíos por hora).
7. **Servicios de Administración de Mensajería:**
   * `ms-rutaexpress-mq-admin`: Para provisión y monitoreo de colas y DLQs en RabbitMQ.
   * `ms-rutaexpress-kafka-admin`: Para administración de tópicos y particiones en Kafka.
8. **Infraestructura Cloud (AWS API Gateway + EC2 + Docker Compose):**
   * Configuración de AWS API Gateway (HTTP API) con JWT Authorizer conectado a Azure AD.
   * Configuración de los 3 Docker Compose en EC2:
     * `ec2-apps`: `shipments-svc`, `catalog-svc`, `notify-svc`, `report-svc`, `audit-svc`, `bff-svc`.
     * `ec2-mq`: Clúster RabbitMQ de 2 nodos con Management UI.
     * `ec2-kafka`: Kafka KRaft o Zookeeper + 3 brokers Kafka + Kafka UI.
