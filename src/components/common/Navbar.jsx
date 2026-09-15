import React from 'react';
import { 
  IconPackage, 
  IconRefresh, 
  IconLayers, 
  IconBell, 
  IconTruck, 
  IconClock, 
  IconChartBar, 
  IconServer 
} from './Icons';

export function Navbar({ 
  activeDomain, 
  onSelectDomain, 
  onResetData, 
  totalServices, 
  avgCapacity 
}) {
  const microservices = [
    { id: 'catalog', label: 'Catálogo', icon: <IconLayers width={15} height={15} />, isLive: true, tooltip: 'ms-rutaexpress-catalog (Activo)' },
    { id: 'notify', label: 'Notificaciones', icon: <IconBell width={15} height={15} />, isLive: true, tooltip: 'ms-rutaexpress-notify (Activo)' },
    { id: 'audit', label: 'Auditoría', icon: <IconClock width={15} height={15} />, isLive: true, tooltip: 'ms-rutaexpress-audit (Activo)' },
    { id: 'report', label: 'Reportes & KPIs', icon: <IconChartBar width={15} height={15} />, isLive: true, tooltip: 'ms-rutaexpress-report (Activo)' },
    { id: 'shipments', label: 'Envíos', icon: <IconTruck width={15} height={15} />, isLive: false, tooltip: 'ms-rutaexpress-shipments (En desarrollo)' },
    { id: 'bff', label: 'BFF Gateway', icon: <IconServer width={15} height={15} />, isLive: false, tooltip: 'ms-rutaexpress-bff (En desarrollo)' },
    { id: 'messaging', label: 'Admin Brokers', icon: <IconServer width={15} height={15} />, isLive: false, tooltip: 'ms-rutaexpress-mq/kafka-admin (En desarrollo)' },
  ];

  return (
    <header className="topbar">
      <div className="brand-section">
        <div className="brand-logo-icon">
          <IconPackage width={22} height={22} strokeWidth={2.2} />
        </div>
        <div>
          <div className="brand-title">RutaExpress</div>
          <div className="brand-subtitle">Consola Cloud-Native</div>
        </div>
      </div>

      {/* Selector Horizontal de Microservicios en la Barra de Navegación */}
      <nav className="topbar-nav" aria-label="Navegación por microservicios">
        {microservices.map((ms) => {
          const isActive = activeDomain === ms.id;
          return (
            <button
              key={ms.id}
              onClick={() => onSelectDomain(ms.id)}
              className={`topbar-nav-item ${isActive ? 'active' : ''}`}
              title={ms.tooltip}
            >
              {ms.icon}
              <span>{ms.label}</span>
              <span 
                className={`nav-status-dot ${ms.isLive ? 'active-live' : ''}`} 
                title={ms.isLive ? 'Módulo Funcional' : 'Slot de Integración Reservado'}
              />
            </button>
          );
        })}
      </nav>

      <div className="topbar-telemetry">
        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-gray" title="Servicios registrados en catálogo">
            Servicios: <strong style={{ color: 'var(--yellow-400)', marginLeft: '4px' }}>{totalServices}</strong>
          </span>
          <span className="badge badge-yellow" title="Capacidad media de flota">
            Flota: <strong style={{ marginLeft: '4px' }}>{avgCapacity}%</strong>
          </span>
        </div>

        <button 
          onClick={onResetData}
          className="btn btn-secondary btn-sm"
          title="Restablecer datos de simulación"
        >
          <IconRefresh width={14} height={14} />
          <span>Restablecer</span>
        </button>
      </div>
    </header>
  );
}
