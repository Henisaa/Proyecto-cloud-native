# ms-rutaexpress-bff

Microservicio **Backend For Frontend (BFF)** y API Gateway interno para la plataforma **RutaExpress**, desarrollado en **Kotlin 2.1** y **Spring Boot 3.4**.

Encargado de actuar como fachada segura detrás del **AWS API Gateway**, validando los tokens JWT corporativos emitidos por **Azure AD (Microsoft Entra ID)**, aplicando autorización estricta por roles de negocio (`ADMIN`, `DESPACHADOR`, `CLIENTE`, `AUDITOR`) y agregando o retransmitiendo peticiones hacia los microservicios de dominio.

---

## 1. Responsabilidades del Microservicio

- **Validación Criptográfica de JWT:** Verifica firma digital, periodo de vigencia (expiración/not before), `issuer` (`https://login.microsoftonline.com/<TENANT_ID>/v2.0`) y `audience` (`api://<CLIENT_ID>`).
- **Mapeo de Roles de Azure AD:** Convierte el claim `roles` (`["Admin", "Despachador", "Cliente", "Auditor"]`) en `GrantedAuthority` de Spring Security con prefijo `ROLE_` (`ROLE_ADMIN`, `ROLE_DESPACHADOR`, `ROLE_CLIENTE`, `ROLE_AUDITOR`).
- **Control de Acceso Basado en Roles (RBAC):** Protege endpoints mediante anotaciones `@PreAuthorize` y reglas HTTP, retornando errores `401 Unauthorized` y `403 Forbidden` estructurados en formato JSON.
- **Agregación de Dashboard:** Expone `/api/bff/dashboard` consolidando respuestas de catálogo, KPIs y auditoría según el rol del usuario autenticado.
- **Fachada para el Frontend:** Evita que el frontend deba lidiar con múltiples URLs internas de microservicios.

---

## 2. Variables de Entorno y Configuración de Azure AD

| Variable | Descripción | Valor por Defecto / Configurado |
|---|---|---|
| `SERVER_PORT` | Puerto del servicio BFF | `8080` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Issuer de Azure AD / Entra ID | `https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09/v2.0` |
| `AZURE_TENANT_ID` | Tenant ID (Directorio corporativo) | `bc307149-9a0a-45b8-9f7d-2dfc104f9a09` |
| `AZURE_CLIENT_ID` | Client ID / Application ID | `f3136620-804c-4b15-b22d-b6fad957937e` |
| `RUTAEXPRESS_SECURITY_AUDIENCE` | Audience de validación JWT | `api://f3136620-804c-4b15-b22d-b6fad957937e` |
| `AZURE_SCOPE` | Scope expuesto para usuarios | `api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user` |
| `CATALOG_SERVICE_URL` | URL de `ms-rutaexpress-catalog` | `http://localhost:8081` |
| `NOTIFY_SERVICE_URL` | URL de `ms-rutaexpress-notify` | `http://localhost:8082` |
| `AUDIT_SERVICE_URL` | URL de `ms-rutaexpress-audit` | `http://localhost:8083` |
| `REPORT_SERVICE_URL` | URL de `ms-rutaexpress-report` | `http://localhost:8084` |

---

## 3. Endpoints Expuestos

### Públicos (Diagnóstico y Swagger)
- `GET /swagger-ui.html` — Interfaz interactiva de Swagger UI.
- `GET /v3/api-docs` — Definición OpenAPI en formato JSON.
- `GET /actuator/health` — Health check para balanceadores de carga / API Gateway.
- `GET /actuator/prometheus` — Métricas para Prometheus / Grafana.

### Protegidos por JWT y Roles
| Método | Endpoint | Roles Permitidos | Descripción |
|---|---|---|---|
| `GET` | `/api/bff/auth/me` | Autenticado | Devuelve perfil, email, tenant y roles del usuario extraídos del JWT. |
| `GET` | `/api/bff/status` | Autenticado | Estado de conexión del BFF y URLs de downstream. |
| `GET` | `/api/bff/dashboard` | Autenticado | Panel consolidado (KPIs si Admin, Flota si Despachador, Tarifas si Cliente). |
| `GET` | `/api/bff/catalog/services` | `ADMIN`, `DESPACHADOR`, `CLIENTE` | Catálogo de servicios de despacho activos. |
| `GET` | `/api/bff/report/kpis` | `ADMIN` | Reporte consolidado de KPIs y tiempos de entrega. |
| `GET` | `/api/bff/audit/timeline/{id}` | `ADMIN`, `AUDITOR` | Línea de tiempo y trazabilidad de eventos por envío. |

---

## 4. Compilación y Pruebas Automatizadas

```bash
# Ejecutar suite de pruebas unitarias y de seguridad
./gradlew test

# Iniciar localmente
./gradlew bootRun
```
