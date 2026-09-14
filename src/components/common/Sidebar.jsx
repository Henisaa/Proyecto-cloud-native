import React from 'react';
import { 
  IconLayers, 
  IconTruck, 
  IconCalculator, 
  IconBell, 
  IconFileText, 
  IconSend,
  IconClock,
  IconChartBar,
  IconServer,
  IconShield
} from './Icons';

export function Sidebar({ activeDomain, currentView, onSelectView, onSelectDomain }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--yellow-400)', marginBottom: '4px' }}>
          <IconShield width={16} height={16} />
          <span style={{ fontSize: '13px', fontWeight: '700', letterSpacing: '0.04em' }}>CENTRO DE CONTROL</span>
        </div>
        <div style={{ fontSize: '12px', color: 'var(--gray-400)' }}>
          Módulo activo: <strong style={{ color: 'var(--gray-200)', textTransform: 'uppercase' }}>{activeDomain}</strong>
        </div>
      </div>

      <div className="sidebar-nav">
        {/* SECCIÓN CATÁLOGO */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <div className="sidebar-domain-label" style={{ marginBottom: 0 }}>
            ms-catalog
          </div>
          <span className="badge badge-success" style={{ fontSize: '9px', padding: '1px 5px' }}>
            Activo
          </span>
        </div>

        <button
          className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'services' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('catalog');
            onSelectView('services');
          }}
        >
          <IconLayers width={18} height={18} />
          <span>Servicios y Categorías</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'capacity' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('catalog');
            onSelectView('capacity');
          }}
        >
          <IconTruck width={18} height={18} />
          <span>Capacidad de Flota</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'tariffs' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('catalog');
            onSelectView('tariffs');
          }}
        >
          <IconCalculator width={18} height={18} />
          <span>Matriz de Tarifas</span>
        </button>

        {/* SECCIÓN NOTIFICACIONES */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '16px', marginBottom: '8px' }}>
          <div className="sidebar-domain-label" style={{ marginBottom: 0 }}>
            ms-notify
          </div>
          <span className="badge badge-success" style={{ fontSize: '9px', padding: '1px 5px' }}>
            Activo
          </span>
        </div>

        <button
          className={`sidebar-link ${activeDomain === 'notify' && currentView === 'queues' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('notify');
            onSelectView('queues');
          }}
        >
          <IconBell width={18} height={18} />
          <span>Monitor de Colas & DLQ</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'notify' && currentView === 'documents' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('notify');
            onSelectView('documents');
          }}
        >
          <IconFileText width={18} height={18} />
          <span>Etiquetas & Comprobantes</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'notify' && currentView === 'dispatch' ? 'active' : ''}`}
          onClick={() => {
            onSelectDomain('notify');
            onSelectView('dispatch');
          }}
        >
          <IconSend width={18} height={18} />
          <span>Simulador de Despacho</span>
        </button>

        {/* SECCIÓN OTROS MICROSERVICIOS (EN CONSTRUCCIÓN) */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '20px', marginBottom: '8px' }}>
          <div className="sidebar-domain-label" style={{ marginBottom: 0 }}>
            Otros Microservicios
          </div>
          <span className="badge badge-gray" style={{ fontSize: '9px', padding: '1px 5px' }}>
            Slots
          </span>
        </div>

        <button
          className={`sidebar-link ${activeDomain === 'shipments' ? 'active' : ''}`}
          onClick={() => onSelectDomain('shipments')}
        >
          <IconTruck width={18} height={18} />
          <span>Envíos (ms-shipments)</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'audit' ? 'active' : ''}`}
          onClick={() => onSelectDomain('audit')}
        >
          <IconClock width={18} height={18} />
          <span>Auditoría (ms-audit)</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'report' ? 'active' : ''}`}
          onClick={() => onSelectDomain('report')}
        >
          <IconChartBar width={18} height={18} />
          <span>Reportes (ms-report)</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'bff' ? 'active' : ''}`}
          onClick={() => onSelectDomain('bff')}
        >
          <IconServer width={18} height={18} />
          <span>BFF & Gateway</span>
        </button>

        <button
          className={`sidebar-link ${activeDomain === 'messaging' ? 'active' : ''}`}
          onClick={() => onSelectDomain('messaging')}
        >
          <IconServer width={18} height={18} />
          <span>Admin Brokers</span>
        </button>
      </div>

      <div className="sidebar-footer">
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
          <span>Microservicios:</span>
          <strong style={{ color: 'var(--yellow-400)' }}>8 definidos</strong>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>Integración:</span>
          <strong style={{ color: 'var(--gray-300)' }}>Modular</strong>
        </div>
      </div>
    </aside>
  );
}
