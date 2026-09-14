# RutaExpress — Consola Web Frontend (React JS)

Frontend unificado para la plataforma **RutaExpress**, desarrollado en **React JS** con un sistema de diseño industrial en **tonalidades amarillo y gris**, enfocado en la visibilidad y operación de los microservicios de **Catálogo** (`ms-rutaexpress-catalog`) y **Notificaciones** (`ms-rutaexpress-notify`).

---

## Paleta & Estilo Visual

- **Acentos Industriales / Señalética:** Amarillo de seguridad (`#F59E0B`, `#FBBF24`, `#FCD34D`).
- **Superficies y Estructura:** Escalas de grises grafito, antracita y pizarra (`#090D16`, `#0F172A`, `#1E293B`, `#334155`, `#94A3B8`).
- **Detalles Operativos:** Tipografía monoespaciada para códigos y datos tabulares, iconografía SVG precisa (sin emojis), barras de progreso de capacidad calibradas y contraste accesible según WCAG.

---

## Módulos y Vistas Implementadas

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

---

## Estructura del Proyecto

```text
frontend/
├── index.html                    # Documento HTML principal
├── package.json                  # Dependencias y scripts Vite + React
├── vite.config.js                # Configuración de empaquetador Vite
├── PRODUCT.md                    # Definición y requerimientos de producto
├── DESIGN.md                     # Sistema de diseño, tokens y principios
├── README.md                     # Documentación de uso
└── src/
    ├── main.jsx                  # Punto de entrada React
    ├── App.jsx                   # Componente raíz y estado global
    ├── index.css                 # Tokens de diseño, estilos y componentes
    ├── data/
    │   └── mockData.js           # Datos semilla alineados con Flyway SQL y AMQP
    └── components/
        ├── common/
        │   ├── Navbar.jsx        # Barra superior con telemetría de brokers
        │   ├── Sidebar.jsx       # Menú lateral por dominios
        │   └── Icons.jsx         # Biblioteca de iconos SVG vectoriales
        ├── catalog/
        │   ├── ServicesView.jsx      # Gestión de servicios
        │   ├── FleetCapacityView.jsx # Capacidad y simulador de cupos
        │   └── TariffMatrixView.jsx  # Tarifas y cotizador en vivo
        └── notifications/
            ├── NotificationMonitorView.jsx # Monitor de colas y DLQ
            ├── DocumentViewer.jsx          # Render de etiquetas y tickets
            └── DispatcherSimulatorView.jsx # Despachador de eventos AMQP
```

---

## Puesta en Marcha (Instrucciones)

```bash
# 1. Instalar dependencias
npm install

# 2. Iniciar servidor de desarrollo local
npm run dev

# 3. Construir para producción
npm run build
```
