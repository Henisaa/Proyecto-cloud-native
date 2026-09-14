import React, { useState } from 'react';
import { 
  IconBell, 
  IconMail, 
  IconWarehouse, 
  IconBarcode, 
  IconCheckCircle, 
  IconAlertCircle, 
  IconRefresh,
  IconSearch
} from '../common/Icons';

export function NotificationMonitorView({ queues, logs, onPurgeDlq, onRefresh }) {
  const [filterChannel, setFilterChannel] = useState('ALL');
  const [searchShipment, setSearchShipment] = useState('');

  const filteredLogs = logs.filter((l) => {
    const matchesChannel = filterChannel === 'ALL' || l.channel === filterChannel;
    const matchesSearch = 
      l.shipmentId.toLowerCase().includes(searchShipment.toLowerCase()) ||
      l.recipient.toLowerCase().includes(searchShipment.toLowerCase()) ||
      l.idempotencyKey.toLowerCase().includes(searchShipment.toLowerCase());
    return matchesChannel && matchesSearch;
  });

  const getChannelBadge = (channel) => {
    switch (channel) {
      case 'EMAIL':
        return (
          <span className="badge badge-yellow">
            <IconMail width={12} height={12} />
            Email Cliente
          </span>
        );
      case 'WAREHOUSE_TICKET':
        return (
          <span className="badge badge-gray">
            <IconWarehouse width={12} height={12} />
            Ticket Bodega
          </span>
        );
      case 'LABEL_PDF':
        return (
          <span className="badge badge-gray" style={{ color: 'var(--yellow-300)' }}>
            <IconBarcode width={12} height={12} />
            Etiqueta PDF
          </span>
        );
      default:
        return <span className="badge badge-gray">{channel}</span>;
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconBell width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Monitor de Colas RabbitMQ & Telemetría DLQ
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-notify</code> — Consumidores AMQP en modo <code>AcknowledgeMode.MANUAL</code>
          </p>
        </div>

        <button onClick={onRefresh} className="btn btn-secondary btn-sm">
          <IconRefresh width={14} height={14} />
          <span>Sincronizar Broker</span>
        </button>
      </div>

      {/* Tarjetas de Telemetría de Colas AMQP */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        {queues.map((q) => (
          <div key={q.name} className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <div>
                <div className="code-cell" style={{ fontWeight: '700', fontSize: '14px', color: 'var(--gray-100)' }}>
                  {q.name}
                </div>
                <div style={{ fontSize: '11px', color: 'var(--gray-400)', marginTop: '2px' }}>
                  Exchange: <code>{q.exchange}</code> | Key: <code>{q.routingKey}</code>
                </div>
              </div>
              <span className="badge badge-success">
                <span className="status-dot-live" style={{ width: '6px', height: '6px' }}></span>
                {q.status}
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', padding: '10px', background: 'var(--gray-950)', borderRadius: '6px', textAlign: 'center' }}>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--gray-500)' }}>EN ESPERA</div>
                <div className="tabular-data" style={{ fontSize: '18px', fontWeight: '800', color: q.messagesReady > 0 ? 'var(--yellow-400)' : 'var(--gray-200)' }}>
                  {q.messagesReady}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--gray-500)' }}>CONSUMIDORES</div>
                <div className="tabular-data" style={{ fontSize: '18px', fontWeight: '800', color: 'var(--gray-200)' }}>
                  {q.consumers}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--gray-500)' }}>PROCESADOS</div>
                <div className="tabular-data" style={{ fontSize: '18px', fontWeight: '800', color: 'var(--color-success)' }}>
                  {q.totalProcessed}
                </div>
              </div>
            </div>

            {/* DLQ Counter */}
            <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px' }}>
              <span style={{ color: 'var(--gray-400)' }}>
                Cola de Reintentos ({q.dlqName}):
              </span>
              <span className="tabular-data" style={{ fontWeight: '700', color: q.dlqCount > 0 ? 'var(--color-danger)' : 'var(--gray-300)' }}>
                {q.dlqCount} errores
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* Filtros de Event Stream */}
      <div className="card" style={{ marginBottom: '16px', padding: '14px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: '1 1 280px' }}>
            <IconSearch width={16} height={16} style={{ color: 'var(--gray-500)' }} />
            <input
              type="text"
              className="form-input"
              style={{ width: '100%' }}
              placeholder="Buscar por ID de envío, destinatario o clave Redis..."
              value={searchShipment}
              onChange={(e) => setSearchShipment(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            {['ALL', 'EMAIL', 'LABEL_PDF', 'WAREHOUSE_TICKET'].map((ch) => (
              <button
                key={ch}
                onClick={() => setFilterChannel(ch)}
                className={`btn btn-sm ${filterChannel === ch ? 'btn-primary' : 'btn-secondary'}`}
              >
                {ch === 'ALL' ? 'Todos los Canales' : ch.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Registro de Eventos Procesados */}
      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Hora (UTC)</th>
              <th>Canal</th>
              <th>Envío ID</th>
              <th>Destinatario / Receptor</th>
              <th>Idempotencia Redis Key</th>
              <th>Detalles del Procesamiento</th>
              <th style={{ textAlign: 'right' }}>Estado</th>
            </tr>
          </thead>
          <tbody>
            {filteredLogs.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '32px', color: 'var(--gray-400)' }}>
                  No hay eventos registrados bajo los filtros seleccionados.
                </td>
              </tr>
            ) : (
              filteredLogs.map((log) => (
                <tr key={log.id}>
                  <td className="tabular-data" style={{ fontSize: '12px', color: 'var(--gray-400)' }}>
                    {log.timestamp.replace('T', ' ').replace('Z', '')}
                  </td>
                  <td>{getChannelBadge(log.channel)}</td>
                  <td className="code-cell" style={{ fontWeight: '700', color: 'var(--yellow-400)' }}>
                    {log.shipmentId}
                  </td>
                  <td>
                    <div style={{ fontWeight: '600', color: 'var(--gray-100)' }}>{log.recipientName}</div>
                    <div style={{ fontSize: '11px', color: 'var(--gray-400)' }}>{log.recipient}</div>
                  </td>
                  <td className="code-cell" style={{ fontSize: '11px', color: 'var(--gray-400)' }}>
                    {log.idempotencyKey}
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--gray-200)', maxWidth: '320px' }}>
                    {log.details}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <span className="badge badge-success">
                      <IconCheckCircle width={12} height={12} />
                      {log.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
