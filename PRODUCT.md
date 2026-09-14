# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack
React JS (Single Page Application con arquitectura modular de vistas, componentes accesibles y diseño sin compilación en la máquina del usuario).

## Users
1. **Despachadores y Operadores Logísticos:** Supervisan la capacidad de vehículos en tiempo real por zona geográfica, consultan servicios activos y reservan cupos de carga.
2. **Administradores de Catálogo y Tarifas:** Gestionan el portafolio de servicios logísticos, tiempos estimados de entrega (SLA) y la matriz tarifaria (base, por km, por kg).
3. **Supervisores de Notificaciones y Bodega:** Monitorean el procesamiento asíncrono de comandos en colas RabbitMQ, visualizan tickets de preparación de pedidos, etiquetas de rotulado con código de barras y correos enviados a clientes.

## Product Purpose
Consola web operativa unificada para la plataforma logística **RutaExpress**, permitiendo gobernar el catálogo comercial, auditar la capacidad de la flota y supervisar el pipeline de notificaciones y generación de documentos en un solo centro de control.

## Positioning
Centro de mando logístico enfocado en la visibilidad operativa inmediata: control granular de cupos por zona/vehículo y trazabilidad completa de órdenes y notificaciones en tiempo real, evitando silos entre ventas y bodega.

## Operating Context
Centros de control y estaciones de despacho de paquetería. Interfaces de alta densidad informativa donde la legibilidad de códigos de seguimiento, estados de capacidad y alertas operativas es prioritaria.

## Capabilities and Constraints
- **Gestión de Catálogo:** Consulta, filtrado por categoría (`SAME_DAY`, `STANDARD`, `EXPRESS`, `COLD_CHAIN`, `FRAGILE`), alta y actualización de estado operativo de servicios.
- **Capacidad de Flota:** Monitoreo porcentual de cupos diarios por zona (`URBAN_CENTER`, `URBAN_PERIPHERY`, etc.) y tipo de vehículo (`MOTORCYCLE`, `VAN`, `TRUCK`), límites de peso y volumen, más simulador de disponibilidad.
- **Cotizador y Matriz Tarifaria:** Cálculo dinámico de costo de flete según origen, destino, kilometraje y peso del bulto.
- **Monitor de Notificaciones:** Panel de telemetría de eventos AMQP (`q.cmd.email`, `q.cmd.warehouse`, `q.cmd.label`), tasa de éxito, reintentos y alertas de Dead Letter Queue (DLQ).
- **Visor de Documentos Operativos:** Renderizado interactivo de etiquetas de envío con código de barras, albaranes de bodega con ubicación de pasillo y plantillas de correo de seguimiento.
- **Simulador de Despacho:** Envío de mensajes de prueba para validar la integración de eventos.
- **Restricciones del entorno:** No compilar en la máquina local del usuario.
- **Restricción estética:** Paleta corporativa basada en tonalidades de amarillo industrial y gris.

## Brand Commitments
- Identidad: **RutaExpress Logística Inteligente**.
- Colores: Amarillo de señalización industrial (`#F59E0B` / `#FACC15`) contrastado sobre escalas de grises antracita, pizarra y grafito (`#0F172A`, `#1E293B`, `#334155`, `#94A3B8`, `#F8FAFC`).
- Tono: Profesional, técnico, robusto y eficiente.
