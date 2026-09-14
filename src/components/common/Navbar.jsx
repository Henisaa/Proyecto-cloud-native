import React from 'react';
import { IconPackage, IconRefresh } from './Icons';

export function Navbar({ activeTabTitle, onResetData, totalServices, avgCapacity }) {
  return (
    <header className="topbar">
      <div className="brand-section">
        <div className="brand-logo-icon">
          <IconPackage width={22} height={22} strokeWidth={2.2} />
        </div>
        <div>
          <div className="brand-title">RutaExpress</div>
          <div className="brand-subtitle">Consola de Operaciones & Logística</div>
        </div>
      </div>

      <div className="topbar-telemetry">
        <div className="status-indicator">
          <span className="status-dot-live"></span>
          <span>Brokers Kafka & RabbitMQ <strong>Online</strong></span>
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-gray">
            Servicios: <strong style={{ color: 'var(--yellow-400)', marginLeft: '4px' }}>{totalServices}</strong>
          </span>
          <span className="badge badge-yellow">
            Ocupación Flota: <strong style={{ marginLeft: '4px' }}>{avgCapacity}%</strong>
          </span>
        </div>

        <button 
          onClick={onResetData}
          className="btn btn-secondary btn-sm"
          title="Restablecer datos semilla"
        >
          <IconRefresh width={14} height={14} />
          <span>Restablecer</span>
        </button>
      </div>
    </header>
  );
}
