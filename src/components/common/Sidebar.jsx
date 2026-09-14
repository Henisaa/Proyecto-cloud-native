import React from 'react';
import { 
  IconLayers, 
  IconTruck, 
  IconCalculator, 
  IconBell, 
  IconFileText, 
  IconSend,
  IconShield
} from './Icons';

export function Sidebar({ currentView, onSelectView }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--yellow-400)', marginBottom: '4px' }}>
          <IconShield width={16} height={16} />
          <span style={{ fontSize: '13px', fontWeight: '700', letterSpacing: '0.04em' }}>CENTRO DE CONTROL</span>
        </div>
        <div style={{ fontSize: '12px', color: 'var(--gray-400)' }}>
          Gestión unificada de microservicios
        </div>
      </div>

      <div className="sidebar-nav">
        <div className="sidebar-domain-label">Microservicio Catálogo</div>

        <button
          className={`sidebar-link ${currentView === 'services' ? 'active' : ''}`}
          onClick={() => onSelectView('services')}
        >
          <IconLayers width={18} height={18} />
          <span>Servicios y Categorías</span>
        </button>

        <button
          className={`sidebar-link ${currentView === 'capacity' ? 'active' : ''}`}
          onClick={() => onSelectView('capacity')}
        >
          <IconTruck width={18} height={18} />
          <span>Capacidad de Flota</span>
        </button>

        <button
          className={`sidebar-link ${currentView === 'tariffs' ? 'active' : ''}`}
          onClick={() => onSelectView('tariffs')}
        >
          <IconCalculator width={18} height={18} />
          <span>Matriz de Tarifas</span>
        </button>

        <div className="sidebar-domain-label" style={{ marginTop: '16px' }}>
          Microservicio Notificaciones
        </div>

        <button
          className={`sidebar-link ${currentView === 'queues' ? 'active' : ''}`}
          onClick={() => onSelectView('queues')}
        >
          <IconBell width={18} height={18} />
          <span>Monitor de Colas & DLQ</span>
        </button>

        <button
          className={`sidebar-link ${currentView === 'documents' ? 'active' : ''}`}
          onClick={() => onSelectView('documents')}
        >
          <IconFileText width={18} height={18} />
          <span>Etiquetas & Comprobantes</span>
        </button>

        <button
          className={`sidebar-link ${currentView === 'dispatch' ? 'active' : ''}`}
          onClick={() => onSelectView('dispatch')}
        >
          <IconSend width={18} height={18} />
          <span>Simulador de Despacho</span>
        </button>
      </div>

      <div className="sidebar-footer">
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
          <span>Versión Frontend:</span>
          <strong style={{ color: 'var(--gray-300)' }}>v1.0.0</strong>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>Arquitectura:</span>
          <strong style={{ color: 'var(--yellow-400)' }}>Cloud-Native</strong>
        </div>
      </div>
    </aside>
  );
}
