import React from 'react';
import { 
  IconCode, 
  IconTruck, 
  IconClock, 
  IconChartBar, 
  IconServer, 
  IconCheckCircle, 
  IconAlertCircle,
  IconArrowRight
} from './Icons';

const microservicesMetadata = {
  shipments: {
    name: 'ms-rutaexpress-shipments',
    title: 'Gestión de Envíos & Despachos',
    branch: 'origin/ms-rutaexpress-shipments',
    database: 'Oracle Database',
    messaging: 'Publica en RabbitMQ (cmd.direct) y Kafka (shipments.events)',
    endpoints: ['/api/shipments/create', '/api/shipments/{id}/status', '/api/shipments/track/{trackingCode}'],
    description: 'Dominio central de pedidos logísticos. Valida disponibilidad y cupos con catálogo, emite tickets operativos a RabbitMQ y publica eventos de estado a Kafka.',
    icon: <IconTruck width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/shipments/ShipmentsDashboard.jsx',
  },
  audit: {
    name: 'ms-rutaexpress-audit',
    title: 'Auditoría & Timeline de Eventos',
    branch: 'origin/ms-rutaexpress-audit',
    database: 'Oracle Database (tabla auditoria)',
    messaging: 'Consumidor Kafka (shipments.events, catalog.events)',
    endpoints: ['/api/audit/shipments/{id}/timeline', '/api/audit/events'],
    description: 'Servicio de persistencia inmutable de eventos. Registra la cronología exacta de cambios de estado, reservas de capacidad y alertas para trazabilidad legal y operativa.',
    icon: <IconClock width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/audit/AuditTimelineView.jsx',
  },
  report: {
    name: 'ms-rutaexpress-report',
    title: 'Reportes, KPIs & Analítica',
    branch: 'origin/ms-rutaexpress-report',
    database: 'Oracle Database (tablas reportes / snapshots)',
    messaging: 'Consumidor Kafka & Kafka Streams',
    endpoints: ['/api/report/kpis/daily', '/api/report/lead-time', '/api/report/fleet-performance'],
    description: 'Generación de métricas de rendimiento, tiempos de entrega en ruta, porcentaje de cumplimiento de SLA y reportes ejecutivos consolidados.',
    icon: <IconChartBar width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/report/ReportsDashboard.jsx',
  },
  bff: {
    name: 'ms-rutaexpress-bff',
    title: 'Backend for Frontend (BFF) & Gateway',
    branch: 'origin/ms-rutaexpress-bff',
    database: 'Sin base de datos relacional (Stateless + Caffeine Cache)',
    messaging: 'HTTP REST Client hacia microservicios de dominio',
    endpoints: ['/bff/portal/dashboard', '/bff/portal/orders/summary'],
    description: 'Fachada agregadora situada tras el AWS API Gateway. Valida tokens JWT OAuth2 de Azure AD, orquesta peticiones paralelas y agrega respuestas para la interfaz.',
    icon: <IconServer width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/bff/GatewayConsoleView.jsx',
  },
  messaging: {
    name: 'ms-rutaexpress-mq-admin / kafka-admin',
    title: 'Administración de Mensajería & Brokers',
    branch: 'origin/ms-rutaexpress-admin',
    database: 'N/A (APIs de administración AMQP y Kafka)',
    messaging: 'RabbitMQ Management HTTP API & Kafka AdminClient',
    endpoints: ['/api/admin/queues', '/api/admin/topics', '/api/admin/dlq/retry'],
    description: 'Panel de control de infraestructura: provisión de colas, configuración de Dead Letter Exchanges (DLX) y monitoreo de particiones en brokers.',
    icon: <IconServer width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/messaging/BrokerTopologyView.jsx',
  },
};

export function MicroservicePlaceholder({ microserviceId, onSwitchToDemoDomain }) {
  const meta = microservicesMetadata[microserviceId] || {
    name: 'ms-rutaexpress',
    title: 'Módulo en Construcción',
    branch: 'origin/main',
    database: 'Configurable',
    messaging: 'Configurable',
    endpoints: ['/api/...'],
    description: 'Espacio de trabajo reservado para la integración del equipo.',
    icon: <IconCode width={28} height={28} style={{ color: 'var(--yellow-400)' }} />,
    suggestedComponentPath: 'src/components/...',
  };

  return (
    <div>
      {/* Encabezado del Módulo */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            width: '52px',
            height: '52px',
            borderRadius: '8px',
            background: 'var(--gray-900)',
            border: '1px solid var(--gray-750)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            {meta.icon}
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)' }}>
                {meta.title}
              </h1>
              <span className="badge badge-yellow">
                Slot de UI Reservado
              </span>
            </div>
            <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
              Microservicio <code>{meta.name}</code> — Rama vinculada: <code>{meta.branch}</code>
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button 
            onClick={() => onSwitchToDemoDomain('catalog')}
            className="btn btn-secondary btn-sm"
          >
            <span>Ver Catálogo</span>
          </button>
          <button 
            onClick={() => onSwitchToDemoDomain('notify')}
            className="btn btn-secondary btn-sm"
          >
            <span>Ver Notificaciones</span>
          </button>
        </div>
      </div>

      {/* Contenedor Informativo y Guía para los demás integrantes */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: '24px' }}>
        {/* Guía de Integración Técnica */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">
                <IconCode width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                Espacio Preparado para Implementación
              </div>
              <div className="card-subtitle">
                Los demás integrantes del equipo pueden montar sus vistas directamente aquí sin alterar la barra de navegación
              </div>
            </div>
          </div>

          <p style={{ color: 'var(--gray-300)', fontSize: '13.5px', lineHeight: '1.6', marginBottom: '16px' }}>
            {meta.description}
          </p>

          <div style={{ background: 'var(--gray-950)', border: '1px solid var(--gray-800)', borderRadius: '6px', padding: '16px', marginBottom: '16px' }}>
            <div style={{ fontSize: '11px', fontWeight: '700', color: 'var(--yellow-400)', textTransform: 'uppercase', marginBottom: '8px' }}>
              Instrucciones para el Integrante Asignado:
            </div>
            <ol style={{ paddingLeft: '20px', fontSize: '13px', color: 'var(--gray-200)', lineHeight: '1.7' }}>
              <li>
                Crea tus componentes en la ruta sugerida: <code className="code-cell" style={{ color: 'var(--yellow-300)' }}>{meta.suggestedComponentPath}</code>.
              </li>
              <li>
                Importa tu componente principal en <code className="code-cell">src/App.jsx</code> y réndizalo cuando <code className="code-cell">currentDomain === '{microserviceId}'</code>.
              </li>
              <li>
                <strong>¡La navegación en la Navbar ya está cableada!</strong> No necesitas modificar <code className="code-cell">Navbar.jsx</code> ni ajustar estilos de layout globales.
              </li>
            </ol>
          </div>

          <div style={{ fontSize: '12px', color: 'var(--gray-400)', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <IconCheckCircle width={14} height={14} style={{ color: 'var(--color-success)' }} />
            <span>Los tokens de color en amarillo y gris, tipografía tabular y componentes base están listos para ser consumidos.</span>
          </div>
        </div>

        {/* Ficha Técnica del Microservicio */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Contratos & Arquitectura</div>
              <div className="card-subtitle">Especificaciones acordadas en <code>docs/dependencias-microservicios.md</code></div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13px' }}>
            <div>
              <span style={{ fontSize: '11px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                Base de Datos
              </span>
              <div style={{ fontWeight: '600', color: 'var(--gray-100)', marginTop: '2px' }}>
                {meta.database}
              </div>
            </div>

            <div>
              <span style={{ fontSize: '11px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                Mensajería Asíncrona
              </span>
              <div style={{ fontWeight: '600', color: 'var(--gray-100)', marginTop: '2px' }}>
                {meta.messaging}
              </div>
            </div>

            <div>
              <span style={{ fontSize: '11px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                Endpoints REST Previstos
              </span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '4px' }}>
                {meta.endpoints.map((ep) => (
                  <code key={ep} className="code-cell" style={{ fontSize: '12px', color: 'var(--yellow-300)' }}>
                    {ep}
                  </code>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
