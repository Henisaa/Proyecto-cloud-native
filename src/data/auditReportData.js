// Datos semilla y contratos para ms-rutaexpress-audit y ms-rutaexpress-report
// Modelos alineados con EventoAuditoria.kt, ReporteControlador.kt y DTOs.kt

export const initialAuditEvents = [
  {
    id: 101,
    idEntidad: 'ENV-2026-98124',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'CREADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'cliente.carlos@empresa.cl',
    fechaEvento: '2026-09-14T10:15:20Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98124',
      serviceType: 'SAME_DAY',
      serviceCode: 'SRV-EXP-01',
      client: 'Carlos Mendoza Ríos',
      origin: 'Av. Américo Vespucio 1300, Bodega 4, Pudahuel',
      destination: 'Av. Providencia 1240, Of. 802, Providencia',
      package: { weightKg: 4.5, volumeM3: 0.025, fragile: false },
      declaredValue: 85000,
      baseFare: 4500.0,
      trackingCode: 'RTX-EXP-8849124-CL'
    }, null, 2)
  },
  {
    id: 102,
    idEntidad: 'ENV-2026-98124',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'ACEPTADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'despachador.central@rutaexpress.cl',
    fechaEvento: '2026-09-14T10:22:45Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98124',
      status: 'ACEPTADO',
      assignedHub: 'HUB_SANTIAGO_CENTRO',
      capacitySlotReserved: true,
      vehicleAssigned: 'VAN-METRO-04',
      estimatedPickingTime: '2026-09-14T11:00:00Z'
    }, null, 2)
  },
  {
    id: 103,
    idEntidad: 'ENV-2026-98124',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'EN_BODEGA',
    origen: 'ms-rutaexpress-notify',
    usuarioResponsable: 'operador.bodega@rutaexpress.cl',
    fechaEvento: '2026-09-14T11:05:10Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98124',
      status: 'EN_BODEGA',
      aisle: 'Pasillo 04',
      rack: 'Rack 12 - Nivel 2',
      barcodePrinted: true,
      ticketNumber: 'TK-WH-2026-0941',
      warehouseCheckIn: '2026-09-14T11:04:55Z'
    }, null, 2)
  },
  {
    id: 104,
    idEntidad: 'ENV-2026-98124',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'EN_RUTA',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'chofer.valenzuela@rutaexpress.cl',
    fechaEvento: '2026-09-14T12:30:00Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98124',
      status: 'EN_RUTA',
      vehiclePlate: 'JJ-9812',
      driverName: 'Jorge Valenzuela',
      driverPhone: '+56 9 7766 5544',
      routeId: 'RUTA-STGO-PROV-02',
      stopNumber: 4,
      gpsCoordinates: { lat: -33.4285, lng: -70.6124 }
    }, null, 2)
  },
  {
    id: 105,
    idEntidad: 'ENV-2026-98124',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'ENTREGADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'chofer.valenzuela@rutaexpress.cl',
    fechaEvento: '2026-09-14T13:42:18Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98124',
      status: 'ENTREGADO',
      receivedBy: 'Carlos Mendoza Ríos',
      receiverRut: '15.842.190-3',
      signatureBlobUrl: 's3://rutaexpress-signatures/2026/09/ENV-2026-98124.png',
      deliveryNotes: 'Entregado en recepción piso 8 conforme',
      totalDurationMinutes: 207
    }, null, 2)
  },
  {
    id: 106,
    idEntidad: 'ENV-2026-98125',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'CREADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'ventas.farmasur@farmasur.cl',
    fechaEvento: '2026-09-14T11:30:00Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98125',
      serviceType: 'COLD_CHAIN',
      serviceCode: 'SRV-COLD-01',
      client: 'Laboratorios Farmasur S.A.',
      origin: 'Av. Américo Vespucio 1300, Pudahuel',
      destination: 'Av. Vicuña Mackenna 4500, Macul',
      package: { weightKg: 12.0, tempRange: '2°C - 8°C' }
    }, null, 2)
  },
  {
    id: 107,
    idEntidad: 'ENV-2026-98125',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'ACEPTADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'despachador.central@rutaexpress.cl',
    fechaEvento: '2026-09-14T11:45:12Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98125',
      status: 'ACEPTADO',
      refrigeratedCapacityReserved: true,
      assignedVehicle: 'VAN-COLD-02'
    }, null, 2)
  },
  {
    id: 108,
    idEntidad: 'ENV-2026-98125',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'EN_BODEGA',
    origen: 'ms-rutaexpress-notify',
    usuarioResponsable: 'operador.frio@rutaexpress.cl',
    fechaEvento: '2026-09-14T12:10:05Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98125',
      status: 'EN_BODEGA',
      coldChamberId: 'CAMARA-FRIO-03',
      tempLoggedCelsius: 3.8
    }, null, 2)
  },
  {
    id: 109,
    idEntidad: 'ENV-2026-98126',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'CREADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'patricia.valenzuela@retail.cl',
    fechaEvento: '2026-09-14T13:00:10Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98126',
      serviceType: 'STANDARD',
      serviceCode: 'SRV-STD-01',
      client: 'Patricia Valenzuela',
      destination: 'Vitacura 3560, Depto 402'
    }, null, 2)
  },
  {
    id: 110,
    idEntidad: 'ENV-2026-98126',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'ACEPTADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'despachador.central@rutaexpress.cl',
    fechaEvento: '2026-09-14T13:12:00Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98126',
      status: 'ACEPTADO'
    }, null, 2)
  },
  {
    id: 111,
    idEntidad: 'ENV-2026-98126',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'EN_BODEGA',
    origen: 'ms-rutaexpress-notify',
    usuarioResponsable: 'operador.bodega@rutaexpress.cl',
    fechaEvento: '2026-09-14T13:40:00Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98126',
      status: 'EN_BODEGA',
      aisle: 'Pasillo 02',
      rack: 'Rack 08'
    }, null, 2)
  },
  {
    id: 112,
    idEntidad: 'ENV-2026-98126',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'EN_RUTA',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'chofer.gonzalez@rutaexpress.cl',
    fechaEvento: '2026-09-14T14:15:30Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98126',
      status: 'EN_RUTA',
      vehiclePlate: 'KW-4412',
      driverName: 'Matías González'
    }, null, 2)
  },
  {
    id: 113,
    idEntidad: 'ENV-2026-98127',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'CREADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'compras.online@pyme.cl',
    fechaEvento: '2026-09-14T09:10:00Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98127',
      serviceType: 'SAME_DAY',
      serviceCode: 'SRV-EXP-01',
      client: 'Pyme Comercializadora SpA'
    }, null, 2)
  },
  {
    id: 114,
    idEntidad: 'ENV-2026-98127',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'ACEPTADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'despachador.central@rutaexpress.cl',
    fechaEvento: '2026-09-14T09:18:22Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98127',
      status: 'ACEPTADO'
    }, null, 2)
  },
  {
    id: 115,
    idEntidad: 'ENV-2026-98127',
    tipoEntidad: 'ENVIO',
    tipoEvento: 'CANCELADO',
    origen: 'ms-rutaexpress-shipments',
    usuarioResponsable: 'cliente.carlos@empresa.cl',
    fechaEvento: '2026-09-14T09:45:10Z',
    datosPayload: JSON.stringify({
      shipmentId: 'ENV-2026-98127',
      status: 'CANCELADO',
      cancelReason: 'SOLICITUD_EXPRESA_CLIENTE',
      refundIssued: true,
      releasedCapacityId: 'CAP-URBAN-VAN-01'
    }, null, 2)
  },
  {
    id: 116,
    idEntidad: 'SRV-EXP-01',
    tipoEntidad: 'SERVICIO',
    tipoEvento: 'TARIFA_MODIFICADA',
    origen: 'ms-rutaexpress-catalog',
    usuarioResponsable: 'admin.operaciones@rutaexpress.cl',
    fechaEvento: '2026-09-13T16:00:00Z',
    datosPayload: JSON.stringify({
      serviceCode: 'SRV-EXP-01',
      fieldChanged: 'basePrice',
      previousValue: 4200.0,
      newValue: 4500.0,
      authorizedBy: 'Gerencia de Finanzas'
    }, null, 2)
  },
  {
    id: 117,
    idEntidad: 'CAP-URBAN-VAN-01',
    tipoEntidad: 'FLOTA',
    tipoEvento: 'CAPACIDAD_REDUCIDA',
    origen: 'ms-rutaexpress-catalog',
    usuarioResponsable: 'sistema.shipments@kafka',
    fechaEvento: '2026-09-14T10:22:46Z',
    datosPayload: JSON.stringify({
      capacityId: 'CAP-URBAN-VAN-01',
      zone: 'URBAN_CENTER',
      vehicleType: 'VAN',
      remainingSlots: 22,
      bookedSlots: 98,
      triggerShipment: 'ENV-2026-98124'
    }, null, 2)
  },
  {
    id: 118,
    idEntidad: 'NOTIF-EMAIL-98124',
    tipoEntidad: 'NOTIFICACION',
    tipoEvento: 'EMAIL_DESPACHADO',
    origen: 'ms-rutaexpress-notify',
    usuarioResponsable: 'consumer.rabbitmq@q.cmd.email',
    fechaEvento: '2026-09-14T10:23:01Z',
    datosPayload: JSON.stringify({
      recipient: 'carlos.mendoza@empresa.cl',
      template: 'CONFIRMACION_DESPACHO',
      queue: 'q.cmd.email',
      correlationId: 'corr-98124-abc',
      retryCount: 0
    }, null, 2)
  }
];

// Datos de KPIs por Rango de tiempo para ms-rutaexpress-report
// Coinciden exactamente con KpisRespuesta(range, totalShipments, shipmentsPerHour, avgLeadTimeMinutes, activeShipmentsByStatus)
export const reportKpisByRange = {
  last1h: {
    range: 'last1h',
    totalShipments: 68,
    shipmentsPerHour: 68.0,
    avgLeadTimeMinutes: 38.5,
    activeShipmentsByStatus: {
      CREADO: 14,
      ACEPTADO: 19,
      EN_BODEGA: 16,
      EN_RUTA: 15,
      ENTREGADO: 4,
      CANCELADO: 0
    },
    slaComplianceRate: 99.1,
    topHub: 'Hub Central Pudahuel',
    bottleneckStage: 'EN_BODEGA (Pick & Pack)'
  },
  last24h: {
    range: 'last24h',
    totalShipments: 1420,
    shipmentsPerHour: 59.17,
    avgLeadTimeMinutes: 44.2,
    activeShipmentsByStatus: {
      CREADO: 120,
      ACEPTADO: 240,
      EN_BODEGA: 310,
      EN_RUTA: 450,
      ENTREGADO: 280,
      CANCELADO: 20
    },
    slaComplianceRate: 98.4,
    topHub: 'Hub Central Pudahuel',
    bottleneckStage: 'EN_RUTA (Tráfico Urbano 12:00-14:00)'
  },
  last7d: {
    range: 'last7d',
    totalShipments: 9850,
    shipmentsPerHour: 58.63,
    avgLeadTimeMinutes: 46.8,
    activeShipmentsByStatus: {
      CREADO: 750,
      ACEPTADO: 1620,
      EN_BODEGA: 2180,
      EN_RUTA: 3200,
      ENTREGADO: 1950,
      CANCELADO: 150
    },
    slaComplianceRate: 97.8,
    topHub: 'Hub Metropolitano Sur',
    bottleneckStage: 'Consolidación Interurbana'
  },
  last30d: {
    range: 'last30d',
    totalShipments: 42300,
    shipmentsPerHour: 58.75,
    avgLeadTimeMinutes: 45.1,
    activeShipmentsByStatus: {
      CREADO: 3200,
      ACEPTADO: 6800,
      EN_BODEGA: 9400,
      EN_RUTA: 13800,
      ENTREGADO: 8400,
      CANCELADO: 700
    },
    slaComplianceRate: 98.2,
    topHub: 'Hub Central Pudahuel',
    bottleneckStage: 'Picos de CiberMonday / Fin de Mes'
  }
};

// Datos de Top Servicios por Rango para ms-rutaexpress-report
// Coinciden con List<TopServicioRespuesta(serviceType, totalShipments, percentage)>
export const reportTopServicesByRange = {
  last1h: [
    { serviceType: 'SAME_DAY', totalShipments: 32, percentage: 47.06, label: 'RutaExpress Mismo Día' },
    { serviceType: 'STANDARD', totalShipments: 22, percentage: 32.35, label: 'RutaExpress Estándar 24h' },
    { serviceType: 'COLD_CHAIN', totalShipments: 9, percentage: 13.24, label: 'Cadena de Frío Farmacéutica' },
    { serviceType: 'FRAGILE', totalShipments: 5, percentage: 7.35, label: 'Cuidado Especial Frágil' }
  ],
  last24h: [
    { serviceType: 'SAME_DAY', totalShipments: 604, percentage: 42.54, label: 'RutaExpress Mismo Día' },
    { serviceType: 'STANDARD', totalShipments: 486, percentage: 34.23, label: 'RutaExpress Estándar 24h' },
    { serviceType: 'COLD_CHAIN', totalShipments: 210, percentage: 14.79, label: 'Cadena de Frío Farmacéutica' },
    { serviceType: 'FRAGILE', totalShipments: 120, percentage: 8.45, label: 'Cuidado Especial Frágil' }
  ],
  last7d: [
    { serviceType: 'SAME_DAY', totalShipments: 4120, percentage: 41.83, label: 'RutaExpress Mismo Día' },
    { serviceType: 'STANDARD', totalShipments: 3480, percentage: 35.33, label: 'RutaExpress Estándar 24h' },
    { serviceType: 'COLD_CHAIN', totalShipments: 1450, percentage: 14.72, label: 'Cadena de Frío Farmacéutica' },
    { serviceType: 'FRAGILE', totalShipments: 800, percentage: 8.12, label: 'Cuidado Especial Frágil' }
  ],
  last30d: [
    { serviceType: 'SAME_DAY', totalShipments: 17850, percentage: 42.20, label: 'RutaExpress Mismo Día' },
    { serviceType: 'STANDARD', totalShipments: 14920, percentage: 35.27, label: 'RutaExpress Estándar 24h' },
    { serviceType: 'COLD_CHAIN', totalShipments: 6180, percentage: 14.61, label: 'Cadena de Frío Farmacéutica' },
    { serviceType: 'FRAGILE', totalShipments: 3350, percentage: 7.92, label: 'Cuidado Especial Frágil' }
  ]
};
