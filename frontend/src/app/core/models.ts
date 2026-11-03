/** Modelos de los contratos del BFF (sección 11.2 del plan). */

export type EstadoEnvio = 'CREADO' | 'ACEPTADO' | 'EN_BODEGA' | 'EN_RUTA' | 'ENTREGADO' | 'CANCELADO';

export type EstadoCapacidad = 'NO_APLICA' | 'PENDIENTE' | 'RESERVADA' | 'RECHAZADA' | 'LIBERACION_PENDIENTE' | 'LIBERADA';

export const ESTADOS_ENVIO: readonly EstadoEnvio[] = ['CREADO', 'ACEPTADO', 'EN_BODEGA', 'EN_RUTA', 'ENTREGADO', 'CANCELADO'];

export const ESTADOS_CAPACIDAD: readonly EstadoCapacidad[] = [
  'NO_APLICA',
  'PENDIENTE',
  'RESERVADA',
  'RECHAZADA',
  'LIBERACION_PENDIENTE',
  'LIBERADA',
];

/** Las cuatro zonas que acepta el backend (catalog). */
export const ZONAS = ['URBAN_CENTER', 'URBAN_PERIPHERY', 'RURAL', 'INTERURBAN'] as const;
export type Zona = (typeof ZONAS)[number];

export interface UserProfile {
  userId: string;
  username: string;
  name: string | null;
  email: string | null;
  roles: string[];
  scopes: string[];
  tenantId: string | null;
}

export interface Envio {
  id: string;
  trackingCode: string;
  serviceId: number;
  serviceCode?: string | null;
  serviceType?: string | null;
  status: EstadoEnvio;
  zone?: string | null;
  vehicleType?: string | null;
  recipientName: string;
  recipientEmail?: string | null;
  recipientPhone?: string | null;
  originAddress: string;
  destinationAddress: string;
  weightKg: number;
  volumeM3: number;
  packagesCount: number;
  price?: number | null;
  customerEmail?: string | null;
  capacityReserved: boolean;
  capacityStatus: EstadoCapacidad;
  /** Transiciones válidas hoy, calculadas por el backend con la regla de capacidad. */
  allowedTransitions: EstadoEnvio[];
  canRetryCapacity: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CrearEnvio {
  serviceId: number;
  zone: string;
  packagesCount: number;
  weightKg: number;
  volumeM3: number;
  recipientName: string;
  recipientEmail: string | null;
  recipientPhone: string | null;
  originAddress: string;
  destinationAddress: string;
  price?: number | null;
}

export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FiltrosEnvios {
  page?: number;
  size?: number;
  status?: string;
  capacity?: string;
  from?: string;
  to?: string;
  q?: string;
}

export interface ResumenEnvios {
  total: number;
  byStatus: Record<string, number>;
  byCapacity: Record<string, number>;
  recent: Envio[];
}

export interface EventoLinea {
  source: 'RABBITMQ' | 'KAFKA';
  eventType: string | null;
  routingKey: string | null;
  detail: string | null;
  actor: string | null;
  occurredAt: string | null;
}

export interface LineaTiempo {
  shipmentId: string;
  kafkaAvailable: boolean;
  events: EventoLinea[];
}

export interface ServicioResumen {
  id: number;
  code: string;
  name: string;
  category: string;
  status: string;
  estimatedDeliveryHours: number;
  basePrice: number;
  active: boolean;
  availableCapacitiesCount: number;
}

export interface Capacidad {
  id: number;
  zone: string;
  vehicleType: string;
  maxDailyPackages: number;
  currentBookedPackages: number;
  availablePackages: number;
  utilizationPercentage: number;
  isFull: boolean;
  maxWeightKg: number;
  maxVolumeM3: number;
  active: boolean;
  updatedAt: string;
}

export interface Tarifa {
  id: number;
  originZone: string;
  destinationZone: string;
  baseFare: number;
  perKmRate: number;
  perKgRate: number;
  minFare: number;
  active: boolean;
}

export interface ServicioDetalle extends ServicioResumen {
  description?: string | null;
  pricePerKm: number;
  pricePerKg: number;
  isAvailable: boolean;
  totalDailyCapacity: number;
  totalBookedPackages: number;
  overallUtilizationPercentage: number;
  /** Solo para Admin y Despachador; el BFF lo quita para un cliente. */
  capacities?: Capacidad[];
  tariffs?: Tarifa[];
}

export interface EditarCapacidad {
  maxDailyPackages?: number;
  active?: boolean;
}

export interface BloqueDashboard<T = unknown> {
  available: boolean;
  data?: T | null;
  message?: string | null;
}

export interface Dashboard {
  role: string;
  greeting: string;
  generatedAt: string;
  shipmentsOverview?: BloqueDashboard<ResumenEnvios> | null;
  catalogOverview?: BloqueDashboard<ServicioResumen[]> | null;
  reportOverview?: BloqueDashboard<Kpis> | null;
  auditOverview?: BloqueDashboard<EventoAuditoriaKafka[]> | null;
  notices: string[];
}

export interface Kpis {
  range: string;
  totalShipments: number;
  shipmentsPerHour: number;
  avgLeadTimeMinutes: number;
  activeShipmentsByStatus: Record<string, number>;
}

export interface TopServicio {
  serviceType: string;
  totalShipments: number;
  percentage: number;
}

export interface EventoAuditoriaKafka {
  id?: number;
  idEntidad: string;
  tipoEntidad: string;
  tipoEvento: string;
  origen?: string | null;
  usuarioResponsable?: string | null;
  fechaEvento: string;
}

export interface EstadoServicios {
  service: string;
  status: string;
  services: Record<string, string>;
}

/* ------------------------------------------------------------------ Administración RabbitMQ */

export interface MqNodo {
  name: string;
  running: boolean;
  memUsed?: number | null;
  memLimit?: number | null;
  memAlarm: boolean;
  diskFree?: number | null;
  diskFreeLimit?: number | null;
  diskFreeAlarm: boolean;
  uptimeMs?: number | null;
}

export interface MqMetricas {
  activeQueues: number;
  messagesReady: number;
  messagesUnacknowledged: number;
  consumers: number;
  dlqQueues: number;
  dlqMessages: number;
  dlqArrivedTotal: number;
  dlqArrivedRate: number;
  queues: {
    name: string;
    messages: number;
    messagesReady?: number;
    messagesUnacknowledged?: number;
    consumers: number;
    policy?: string | null;
    durable?: boolean | null;
    deadLetterExchange?: string | null;
  }[];
}

export interface MqCola {
  name: string;
  declared: boolean;
  messages: number;
  messagesReady: number;
  unacknowledged: number;
  consumers: number;
  alert: boolean;
  arrivedTotal?: number;
}

export interface MqExchange {
  name: string;
  type: string;
  durable: boolean;
}

export interface MqBinding {
  source: string;
  destination: string;
  destinationType: string;
  routingKey: string;
}

export interface MqPolitica {
  name: string;
  pattern: string;
  applyTo: string;
  priority: number;
  definition: Record<string, unknown>;
}

export interface MqColaRequest {
  name: string;
  durable: boolean;
  ttlMs?: number | null;
  maxLength?: number | null;
  expiresMs?: number | null;
  overflow?: 'reject-publish' | 'drop-head' | null;
  deadLetterExchange?: string | null;
  deadLetterRoutingKey?: string | null;
}

export interface MqExchangeRequest {
  name: string;
  type: 'direct' | 'topic' | 'fanout' | 'headers';
  durable: boolean;
}

export interface MqBindingRequest {
  source: string;
  destination: string;
  destinationType: 'queue' | 'exchange';
  routingKey: string;
}

export interface MqRetencionRequest {
  commandTtlMs: number;
  dlqTtlMs: number;
  maxLength: number;
}

export interface MqPublicarRequest {
  routingKey: string;
  body: string;
}

export interface MqPublicarResultado {
  exchange: string;
  routingKey: string;
  confirmed: boolean;
  routed: boolean;
}
