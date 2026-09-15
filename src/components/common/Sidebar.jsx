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
        {/* DOMINIO 1: CATÁLOGO */}
        <div className="sidebar-domain-group">
          <div
            onClick={() => { onSelectDomain('catalog'); onSelectView('services'); }}
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', cursor: 'pointer' }}
          >
            <div className="sidebar-domain-label" style={{ marginBottom: 0, color: activeDomain === 'catalog' ? 'var(--yellow-400)' : 'var(--gray-300)' }}>
              Catálogo & Tarifas
            </div>
            <span className={`badge ${activeDomain === 'catalog' ? 'badge-yellow' : 'badge-gray'}`} style={{ fontSize: '9px', padding: '1px 6px' }}>
              ms-catalog
            </span>
          </div>

          <button
            className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'services' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('catalog'); onSelectView('services'); }}
          >
            <IconLayers width={16} height={16} />
            <span>Servicios y Categorías</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'capacity' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('catalog'); onSelectView('capacity'); }}
          >
            <IconTruck width={16} height={16} />
            <span>Capacidad de Flota</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'catalog' && currentView === 'tariffs' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('catalog'); onSelectView('tariffs'); }}
          >
            <IconCalculator width={16} height={16} />
            <span>Matriz de Tarifas</span>
          </button>
        </div>

        {/* DOMINIO 2: NOTIFICACIONES */}
        <div className="sidebar-domain-group">
          <div
            onClick={() => { onSelectDomain('notify'); onSelectView('queues'); }}
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', cursor: 'pointer' }}
          >
            <div className="sidebar-domain-label" style={{ marginBottom: 0, color: activeDomain === 'notify' ? 'var(--yellow-400)' : 'var(--gray-300)' }}>
              Notificaciones & Colas
            </div>
            <span className={`badge ${activeDomain === 'notify' ? 'badge-yellow' : 'badge-gray'}`} style={{ fontSize: '9px', padding: '1px 6px' }}>
              ms-notify
            </span>
          </div>

          <button
            className={`sidebar-link ${activeDomain === 'notify' && currentView === 'queues' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('notify'); onSelectView('queues'); }}
          >
            <IconBell width={16} height={16} />
            <span>Monitor Colas & DLQ</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'notify' && currentView === 'documents' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('notify'); onSelectView('documents'); }}
          >
            <IconFileText width={16} height={16} />
            <span>Etiquetas & Tickets</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'notify' && currentView === 'dispatch' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('notify'); onSelectView('dispatch'); }}
          >
            <IconSend width={16} height={16} />
            <span>Simulador AMQP</span>
          </button>
        </div>

        {/* DOMINIO 3: AUDITORÍA */}
        <div className="sidebar-domain-group">
          <div
            onClick={() => { onSelectDomain('audit'); onSelectView('timeline'); }}
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', cursor: 'pointer' }}
          >
            <div className="sidebar-domain-label" style={{ marginBottom: 0, color: activeDomain === 'audit' ? 'var(--yellow-400)' : 'var(--gray-300)' }}>
              Auditoría & Trazabilidad
            </div>
            <span className={`badge ${activeDomain === 'audit' ? 'badge-yellow' : 'badge-gray'}`} style={{ fontSize: '9px', padding: '1px 6px' }}>
              ms-audit
            </span>
          </div>

          <button
            className={`sidebar-link ${activeDomain === 'audit' && currentView === 'timeline' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('audit'); onSelectView('timeline'); }}
          >
            <IconClock width={16} height={16} />
            <span>Timeline por Entidad</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'audit' && currentView === 'all-events' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('audit'); onSelectView('all-events'); }}
          >
            <IconFileText width={16} height={16} />
            <span>Registro General Eventos</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'audit' && currentView === 'types' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('audit'); onSelectView('types'); }}
          >
            <IconLayers width={16} height={16} />
            <span>Filtro por Tipo Entidad</span>
          </button>
        </div>

        {/* DOMINIO 4: REPORTES & KPIS */}
        <div className="sidebar-domain-group">
          <div
            onClick={() => { onSelectDomain('report'); onSelectView('kpis'); }}
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', cursor: 'pointer' }}
          >
            <div className="sidebar-domain-label" style={{ marginBottom: 0, color: activeDomain === 'report' ? 'var(--yellow-400)' : 'var(--gray-300)' }}>
              Reportes & Analítica
            </div>
            <span className={`badge ${activeDomain === 'report' ? 'badge-yellow' : 'badge-gray'}`} style={{ fontSize: '9px', padding: '1px 6px' }}>
              ms-report
            </span>
          </div>

          <button
            className={`sidebar-link ${activeDomain === 'report' && currentView === 'kpis' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('report'); onSelectView('kpis'); }}
          >
            <IconChartBar width={16} height={16} />
            <span>Panel de KPIs</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'report' && currentView === 'top-services' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('report'); onSelectView('top-services'); }}
          >
            <IconLayers width={16} height={16} />
            <span>Top Servicios Demandados</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'report' && currentView === 'pipeline' ? 'active' : ''}`}
            onClick={() => { onSelectDomain('report'); onSelectView('pipeline'); }}
          >
            <IconTruck width={16} height={16} />
            <span>Embudo & Estados</span>
          </button>
        </div>

        {/* SLOTS EN DESARROLLO */}
        <div className="sidebar-domain-group">
          <div className="sidebar-domain-label" style={{ marginBottom: '8px', color: 'var(--gray-400)' }}>
            Slots en Desarrollo
          </div>
          <button
            className={`sidebar-link ${activeDomain === 'shipments' ? 'active' : ''}`}
            onClick={() => onSelectDomain('shipments')}
          >
            <IconTruck width={16} height={16} />
            <span>Envíos (ms-shipments)</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'bff' ? 'active' : ''}`}
            onClick={() => onSelectDomain('bff')}
          >
            <IconServer width={16} height={16} />
            <span>BFF & Gateway</span>
          </button>
          <button
            className={`sidebar-link ${activeDomain === 'messaging' ? 'active' : ''}`}
            onClick={() => onSelectDomain('messaging')}
          >
            <IconServer width={16} height={16} />
            <span>Admin Brokers</span>
          </button>
        </div>
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
