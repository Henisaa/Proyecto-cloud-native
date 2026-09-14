import React, { useState } from 'react';
import { 
  IconSend, 
  IconCheckCircle, 
  IconAlertCircle, 
  IconMail, 
  IconBarcode, 
  IconWarehouse 
} from '../common/Icons';

export function DispatcherSimulatorView({ onDispatchEvent }) {
  const [channel, setChannel] = useState('EMAIL');
  const [shipmentId, setShipmentId] = useState('RTX-2026-98150');
  const [recipientName, setRecipientName] = useState('Mariana Gómez');
  const [recipientEmail, setRecipientEmail] = useState('mariana.gomez@logistica.cl');
  const [messageOrDesc, setMessageOrDesc] = useState('Paquete retirado en sucursal Pudahuel y asignado a ruta express.');
  const [lastDispatched, setLastDispatched] = useState(null);

  const handleSubmit = (e) => {
    e.preventDefault();

    const eventId = crypto.randomUUID ? crypto.randomUUID() : `uuid-${Date.now()}`;
    const timestamp = new Date().toISOString();
    const queueName = channel === 'EMAIL' ? 'q.cmd.email' : channel === 'LABEL_PDF' ? 'q.cmd.label' : 'q.cmd.warehouse';
    const routingKey = channel === 'EMAIL' ? 'cmd.email' : channel === 'LABEL_PDF' ? 'cmd.label' : 'cmd.warehouse';
    const idempotencyKey = `idemp:${channel.toLowerCase()}:${shipmentId}`;

    const newLog = {
      id: `evt-${Date.now()}`,
      eventId,
      timestamp,
      channel,
      queue: queueName,
      shipmentId,
      recipient: recipientEmail,
      recipientName,
      status: 'SUCCESS',
      details: messageOrDesc,
      retryCount: 0,
      idempotencyKey,
    };

    onDispatchEvent(newLog, queueName);

    setLastDispatched({
      eventId,
      timestamp,
      exchange: 'cmd.direct',
      routingKey,
      queue: queueName,
      idempotencyKey,
      envelope: {
        eventId,
        correlationId: `corr-${shipmentId}`,
        timestamp,
        payloadType: channel,
        data: {
          shipmentId,
          recipientName,
          recipientEmail,
          content: messageOrDesc,
        },
      },
    });

    // Generate next shipment ID
    const randomNum = Math.floor(10000 + Math.random() * 90000);
    setShipmentId(`RTX-2026-${randomNum}`);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconSend width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Simulador de Despacho de Comandos AMQP
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-notify</code> — Emulación de publicación de eventos hacia <code>cmd.direct</code>
          </p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '24px' }}>
        {/* Formulario de Despacho */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Generar Evento de Prueba</div>
              <div className="card-subtitle">Publica un payload hacia el broker RabbitMQ con validación de idempotencia</div>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">Canal / Cola de Destino</label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '10px' }}>
                <button
                  type="button"
                  onClick={() => setChannel('EMAIL')}
                  className={`btn ${channel === 'EMAIL' ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ fontSize: '12px', padding: '10px' }}
                >
                  <IconMail width={14} height={14} />
                  <span>Email (q.cmd.email)</span>
                </button>

                <button
                  type="button"
                  onClick={() => setChannel('LABEL_PDF')}
                  className={`btn ${channel === 'LABEL_PDF' ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ fontSize: '12px', padding: '10px' }}
                >
                  <IconBarcode width={14} height={14} />
                  <span>Etiqueta (q.cmd.label)</span>
                </button>

                <button
                  type="button"
                  onClick={() => setChannel('WAREHOUSE_TICKET')}
                  className={`btn ${channel === 'WAREHOUSE_TICKET' ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ fontSize: '12px', padding: '10px' }}
                >
                  <IconWarehouse width={14} height={14} />
                  <span>Bodega (q.cmd.wh)</span>
                </button>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="form-group">
                <label className="form-label">Envío ID (shipmentId)</label>
                <input
                  type="text"
                  className="form-input code-cell"
                  required
                  value={shipmentId}
                  onChange={(e) => setShipmentId(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Nombre del Destinatario</label>
                <input
                  type="text"
                  className="form-input"
                  required
                  value={recipientName}
                  onChange={(e) => setRecipientName(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Correo Destino (Cliente o Bodega)</label>
              <input
                type="email"
                className="form-input"
                required
                value={recipientEmail}
                onChange={(e) => setRecipientEmail(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Contenido / Instrucción del Comando</label>
              <textarea
                className="form-textarea"
                rows={3}
                required
                value={messageOrDesc}
                onChange={(e) => setMessageOrDesc(e.target.value)}
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '8px' }}>
              <IconSend width={16} height={16} />
              <span>Publicar Comando a RabbitMQ</span>
            </button>
          </form>
        </div>

        {/* Consola de Inspección del Sobre (Event Envelope) */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Inspección de Sobre AMQP</div>
              <div className="card-subtitle">Estructura serializada <code>EventEnvelope&lt;T&gt;</code></div>
            </div>
          </div>

          {lastDispatched ? (
            <div>
              <div style={{ 
                background: 'rgba(16, 185, 129, 0.12)', 
                border: '1px solid rgba(16, 185, 129, 0.3)',
                padding: '12px', 
                borderRadius: '6px', 
                marginBottom: '14px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: 'var(--color-success)',
                fontSize: '12.5px',
                fontWeight: '600'
              }}>
                <IconCheckCircle width={16} height={16} />
                <span>Mensaje entregado y confirmado (ACK manual) en {lastDispatched.queue}</span>
              </div>

              <div style={{ fontSize: '11px', color: 'var(--gray-400)', marginBottom: '6px' }}>
                Clave de Idempotencia Redis: <strong className="code-cell" style={{ color: 'var(--yellow-400)' }}>{lastDispatched.idempotencyKey}</strong>
              </div>

              <pre style={{
                background: 'var(--gray-950)',
                border: '1px solid var(--gray-800)',
                borderRadius: '6px',
                padding: '14px',
                fontSize: '11.5px',
                color: 'var(--yellow-200)',
                overflowX: 'auto',
                fontFamily: 'var(--font-mono)',
                lineHeight: '1.4'
              }}>
                {JSON.stringify(lastDispatched.envelope, null, 2)}
              </pre>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--gray-500)' }}>
              <IconSend width={32} height={32} style={{ margin: '0 auto 12px', color: 'var(--gray-600)' }} />
              <div>Complete el formulario y presione "Publicar Comando" para observar el sobre de evento JSON en tiempo real.</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
