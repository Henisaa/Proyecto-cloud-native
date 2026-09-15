# RutaExpress — Consola Web Frontend (React JS)

Frontend unificado para la plataforma **RutaExpress**, desarrollado en **React JS + Vite** con un sistema de diseño industrial en **tonalidades amarillo y gris**, enfocado en la visibilidad y operación en pestañas de los microservicios de **Catálogo** (`ms-rutaexpress-catalog`), **Notificaciones** (`ms-rutaexpress-notify`), **Auditoría** (`ms-rutaexpress-audit`) y **Reportes & KPIs** (`ms-rutaexpress-report`).

---

## Paleta & Estilo Visual

- **Acentos Industriales / Señalética:** Amarillo de seguridad (`#F59E0B`, `#FBBF24`, `#FCD34D`).
- **Superficies y Estructura:** Escalas de grises grafito, antracita y pizarra (`#090D16`, `#0F172A`, `#1E293B`, `#334155`, `#94A3B8`).
- **Detalles Operativos:** Tipografía monoespaciada para códigos y datos tabulares, iconografía SVG precisa (sin emojis), barras de progreso de capacidad calibradas y contraste accesible según WCAG.

---

## Módulos y Pestañas Implementadas

### 1. Dominio de Catálogo (`ms-rutaexpress-catalog`)
* **Servicios y Categorías:**
  - Visualización tabular de servicios activos y suspendidos.
  - Filtros por categoría (`SAME_DAY`, `STANDARD`, `FRAGILE`, `COLD_CHAIN`) y estado.
  - Formulario modal para crear y editar servicios con tarifas base, por km, por kg y SLA en horas.
* **Capacidad de Flota & Control de Sobrecupo:**
  - Monitoreo porcentual de cupos de vehículos por zona geográfica (`Radio Urbano`, `Periferia`, `Interurbano`, `Rural`).
  - Identificación visual de vehículos (motos, furgones, camiones, flota eléctrica).
  - **Simulador de Disponibilidad:** Comprobación en vivo de peso (kg), volumen (m³) y cupos antes de reservar o liberar capacidad.
* **Matriz de Tarifas & Cotizador en Tiempo Real:**
  - Matriz de tarifas zonales origen-destino.
  - **Cotizador dinámico:** Cálculo instantáneo de flete considerando kilometraje, peso y tarifa mínima garantizada.

### 2. Dominio de Notificaciones (`ms-rutaexpress-notify`)
* **Monitor de Colas RabbitMQ & DLQs:**
  - Estado de las colas de mensajería: `q.cmd.email`, `q.cmd.warehouse`, `q.cmd.label`.
  - Métricas de mensajes listos, consumidores activos, total procesado y monitoreo de Dead Letter Queues (`.dlq`).
  - Registro de eventos en tiempo real con clave de idempotencia Redis (`idemp:...`).
* **Visor de Documentos y Comprobantes:**
  - **Etiqueta Térmica de Despacho (PDF):** Formato tipo Zebra con código de barras SVG, código de seguimiento, datos de remitente/destinatario y peso.
  - **Ticket de Preparación de Bodega:** Albarán de picking con pasillo, rack y descripción de empaque.
  - **Plantilla de Correo al Cliente:** Vista previa del correo de confirmación con tracking y entrega estimada.
* **Simulador de Despacho AMQP:**
  - Formulario para publicar comandos de prueba hacia `cmd.direct`.
  - Inspección del sobre JSON `EventEnvelope<T>` serializado con `eventId`, `timestamp` y `correlationId`.

### 3. Dominio de Auditoría & Trazabilidad (`ms-rutaexpress-audit` :8083)
* **Línea de Tiempo por Entidad (`/api/audit/entidad/{idEntidad}`):**
  - Trazabilidad cronológica interactiva con estados (`CREADO` → `ACEPTADO` → `EN_BODEGA` → `EN_RUTA` → `ENTREGADO` / `CANCELADO`).
  - Inspección detallada de payloads JSON serializados en Oracle DB con botón de copiado rápido al portapapeles.
  - Acceso directo mediante chips de muestra (`ENV-2026-98124`, `ENV-2026-98125`, `ENV-2026-98126`, `ENV-2026-98127`, `SRV-EXP-01`).
* **Registro General de Auditoría (`/api/audit`):**
  - Bitácora inmutable de eventos consumidos desde Kafka (`audit.timeline`).
  - Búsqueda en vivo por ID de entidad, evento o usuario responsable, con modal de inspección JSON.
* **Clasificación por Tipo de Entidad (`/api/audit/tipo/{tipoEntidad}`):**
  - Segmentación de eventos por dominio (`ENVIO`, `SERVICIO`, `FLOTA`, `NOTIFICACION`).
* **Exportación Nativa:**
  - Descarga del registro completo de auditoría en formato JSON.

### 4. Dominio de Reportes, KPIs & Analítica (`ms-rutaexpress-report` :8084)
* **Panel de KPIs Operacionales (`/api/report/kpis`):**
  - Métricas clave en tiempo real: Envíos totales, Throughput horario (`shipmentsPerHour`), Lead Time promedio en minutos y tasa de cumplimiento SLA (98.4%).
  - Filtro por ventanas temporales: `last1h`, `last24h`, `last7d`, `last30d`.
* **Embudo de Estados Activos:**
  - Desglose del mapa `activeShipmentsByStatus` con conteo tabular y barras proporcionales en paleta industrial.
* **Top Servicios Más Demandados (`/api/report/top-services`):**
  - Ranking de servicios por volumen de solicitudes y porcentaje de participación de mercado.
* **Caché Caffeine & Exportación de Datos:**
  - Monitoreo del estado de la caché Caffeine (TTL 60s) con botón de refresco y descarga directa a formatos **CSV** y **JSON**.

---

## Estructura del Proyecto

```text
frontend/
├── index.html                    # Documento HTML principal
├── package.json                  # Dependencias y scripts Vite + React
├── vite.config.js                # Configuración Vite (puerto 3000 y proxies hacia :8083 y :8084)
├── .env.example                  # Plantilla de variables de entorno
├── .env                          # Configuración local de endpoints y Azure AD
├── PRODUCT.md                    # Definición y requerimientos de producto
├── DESIGN.md                     # Sistema de diseño, tokens y principios
├── README.md                     # Documentación de uso
└── src/
    ├── main.jsx                  # Punto de entrada React
    ├── App.jsx                   # Orquestador de pestañas y estado global
    ├── index.css                 # Tokens de diseño, estilos y componentes
    ├── config/
    │   └── authConfig.js         # Configuración MSAL (Azure AD) y rutas del BFF
    ├── data/
    │   ├── mockData.js           # Datos semilla para Catálogo y Notificaciones
    │   └── auditReportData.js    # Datos semilla y contratos para Auditoría y Reportes
    └── components/
        ├── common/
        │   ├── Navbar.jsx        # Barra superior con selector de pestañas
        │   ├── Sidebar.jsx       # Menú lateral por dominios y sub-apartados
        │   ├── Icons.jsx         # Biblioteca de iconos SVG vectoriales
        │   └── MicroservicePlaceholder.jsx # Vista y diagrama para módulos en arquitectura
        ├── catalog/
        │   ├── ServicesView.jsx      # Gestión de servicios
        │   ├── FleetCapacityView.jsx # Capacidad y simulador de cupos
        │   └── TariffMatrixView.jsx  # Tarifas y cotizador en vivo
        ├── notifications/
        │   ├── NotificationMonitorView.jsx # Monitor de colas y DLQ
        │   ├── DocumentViewer.jsx          # Render de etiquetas y tickets
        │   └── DispatcherSimulatorView.jsx # Despachador de eventos AMQP
        ├── audit/
        │   └── AuditTimelineView.jsx       # Trazabilidad, bitácora y payloads JSON
        └── report/
            └── ReportsDashboard.jsx        # KPIs, embudo logístico y top servicios
```

---

## Configuración de Azure AD (Microsoft Entra ID) & BFF

El frontend está configurado para autenticarse contra el registro de aplicación corporativo en Microsoft Entra ID y consumir las APIs a través del microservicio BFF (`ms-rutaexpress-bff`):

| Variable de Entorno | Valor Configurado | Descripción |
|---|---|---|
| `VITE_AZURE_CLIENT_ID` | `f3136620-804c-4b15-b22d-b6fad957937e` | Client ID de la App Registration en Azure AD |
| `VITE_AZURE_TENANT_ID` | `bc307149-9a0a-45b8-9f7d-2dfc104f9a09` | Tenant ID del directorio institucional |
| `VITE_AZURE_AUTHORITY` | `https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09` | Endpoint de emisión de tokens OAuth2 |
| `VITE_AZURE_SCOPE` | `api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user` | Scope OAuth2 para acceso a la API del BFF |
| `VITE_AZURE_REDIRECT_URI` | `http://localhost:3000` | URI de redirección local (puerto configurado en `vite.config.js`) |
| `VITE_BFF_API_BASE_URL` | `http://localhost:8080` | URL base del Backend For Frontend |

> [!IMPORTANT]
> **Aviso sobre variables de entorno (`.env` y `.env.example`):**
> 1. **Puerto de redirección Azure AD (`VITE_AZURE_REDIRECT_URI`):** `vite.config.js` está configurado para ejecutar el servidor local en el **puerto 3000** (`http://localhost:3000`). Si en el portal de Microsoft Entra ID (Azure AD) la *Redirect URI* de la App Registration fue dada de alta con el puerto por defecto de Vite (`http://localhost:5173`), asegúrate de que ambos valores coincidan (ajustando la variable a `:5173` o actualizando la URI permitida en Azure a `:3000`) para prevenir errores de tipo `AADSTS50011 (Redirect URI mismatch)`.
> 2. **Emisor OAuth2 (`VITE_AZURE_AUTHORITY`):** Se incorporó explícitamente la URL completa de autoridad con el Tenant ID para asegurar que MSAL resuelva el endpoint OpenID Connect correcto sin depender de fallbacks.

---

## Puesta en Marcha (Instrucciones)

```bash
# 1. Instalar dependencias
npm install

# 2. Configurar variables de entorno locales
# Linux / macOS / Git Bash:
cp .env.example .env
# Windows (PowerShell):
Copy-Item .env.example .env
# Windows (CMD):
copy .env.example .env

# 3. Iniciar servidor de desarrollo local (disponible en http://localhost:3000)
npm run dev

# 4. Construir para producción (opcional)
npm run build
```

> **Conectividad Backend & Modo Híbrido:** La consola consulta automáticamente los microservicios backend si están levantados en sus respectivos puertos (`ms-rutaexpress-audit` en `:8083` y `ms-rutaexpress-report` en `:8084`) a través del proxy inverso de Vite (`/api/audit` y `/api/report`). Si los microservicios no están encendidos o no responden, conmuta de forma transparente y sin caídas a los datos semilla locales, permitiendo la operación y evaluación completa de la interfaz en cualquier momento.
