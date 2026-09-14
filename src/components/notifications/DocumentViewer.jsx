import React, { useState } from 'react';
import { 
  IconFileText, 
  IconBarcode, 
  IconWarehouse, 
  IconMail, 
  IconCheckCircle 
} from '../common/Icons';
import { sampleDocuments } from '../../data/mockData';

export function DocumentViewer() {
  const [activeDocTab, setActiveDocTab] = useState('label'); // 'label' | 'ticket' | 'email'

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconFileText width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Visor de Comprobantes & Documentos Generados
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-notify</code> — Motor OpenPDF y plantillas HTML para clientes y operarios
          </p>
        </div>

        {/* Selector de tipo de documento */}
        <div style={{ display: 'flex', background: 'var(--gray-900)', border: '1px solid var(--gray-800)', borderRadius: '6px', padding: '3px' }}>
          <button
            onClick={() => setActiveDocTab('label')}
            className={`btn btn-sm ${activeDocTab === 'label' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ border: 'none' }}
          >
            <IconBarcode width={14} height={14} />
            <span>Etiqueta Térmica (PDF)</span>
          </button>
          <button
            onClick={() => setActiveDocTab('ticket')}
            className={`btn btn-sm ${activeDocTab === 'ticket' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ border: 'none' }}
          >
            <IconWarehouse width={14} height={14} />
            <span>Ticket Bodega</span>
          </button>
          <button
            onClick={() => setActiveDocTab('email')}
            className={`btn btn-sm ${activeDocTab === 'email' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ border: 'none' }}
          >
            <IconMail width={14} height={14} />
            <span>Correo al Cliente</span>
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'center' }}>
        {/* VISTA 1: ETIQUETA TÉRMICA DE DESPACHO */}
        {activeDocTab === 'label' && (
          <div style={{
            width: '420px',
            background: '#ffffff',
            color: '#111827',
            padding: '24px',
            borderRadius: '4px',
            boxShadow: 'var(--shadow-lg)',
            border: '2px dashed var(--yellow-500)',
            fontFamily: 'var(--font-sans)',
          }}>
            {/* Header Etiqueta */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '3px solid #111827', paddingBottom: '12px' }}>
              <div>
                <div style={{ fontWeight: '900', fontSize: '20px', letterSpacing: '-0.03em', color: '#111827' }}>
                  RUTAEXPRESS
                </div>
                <div style={{ fontSize: '11px', fontWeight: '700', letterSpacing: '0.06em', color: '#6b7280' }}>
                  PRIORITY CARRIER SERVICE
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ 
                  background: '#111827', 
                  color: '#fbbf24', 
                  padding: '4px 8px', 
                  borderRadius: '3px', 
                  fontWeight: '800', 
                  fontSize: '12px' 
                }}>
                  {sampleDocuments.label.serviceCode}
                </span>
                <div style={{ fontSize: '10px', marginTop: '3px', fontWeight: '600' }}>SCL-HUB-01</div>
              </div>
            </div>

            {/* Código de barras simulado SVG */}
            <div style={{ textAlign: 'center', margin: '20px 0 10px' }}>
              <svg width="340" height="60" viewBox="0 0 340 60" style={{ display: 'block', margin: '0 auto' }}>
                <rect x="0" y="0" width="4" height="60" fill="#111827" />
                <rect x="8" y="0" width="2" height="60" fill="#111827" />
                <rect x="14" y="0" width="6" height="60" fill="#111827" />
                <rect x="24" y="0" width="4" height="60" fill="#111827" />
                <rect x="32" y="0" width="2" height="60" fill="#111827" />
                <rect x="38" y="0" width="8" height="60" fill="#111827" />
                <rect x="50" y="0" width="4" height="60" fill="#111827" />
                <rect x="58" y="0" width="2" height="60" fill="#111827" />
                <rect x="64" y="0" width="6" height="60" fill="#111827" />
                <rect x="74" y="0" width="3" height="60" fill="#111827" />
                <rect x="82" y="0" width="7" height="60" fill="#111827" />
                <rect x="94" y="0" width="4" height="60" fill="#111827" />
                <rect x="102" y="0" width="2" height="60" fill="#111827" />
                <rect x="108" y="0" width="5" height="60" fill="#111827" />
                <rect x="118" y="0" width="3" height="60" fill="#111827" />
                <rect x="126" y="0" width="8" height="60" fill="#111827" />
                <rect x="138" y="0" width="4" height="60" fill="#111827" />
                <rect x="146" y="0" width="2" height="60" fill="#111827" />
                <rect x="154" y="0" width="6" height="60" fill="#111827" />
                <rect x="164" y="0" width="4" height="60" fill="#111827" />
                <rect x="174" y="0" width="7" height="60" fill="#111827" />
                <rect x="186" y="0" width="3" height="60" fill="#111827" />
                <rect x="194" y="0" width="5" height="60" fill="#111827" />
                <rect x="204" y="0" width="4" height="60" fill="#111827" />
                <rect x="212" y="0" width="2" height="60" fill="#111827" />
                <rect x="220" y="0" width="6" height="60" fill="#111827" />
                <rect x="230" y="0" width="4" height="60" fill="#111827" />
                <rect x="240" y="0" width="7" height="60" fill="#111827" />
                <rect x="252" y="0" width="3" height="60" fill="#111827" />
                <rect x="260" y="0" width="5" height="60" fill="#111827" />
                <rect x="272" y="0" width="4" height="60" fill="#111827" />
                <rect x="280" y="0" width="2" height="60" fill="#111827" />
                <rect x="288" y="0" width="8" height="60" fill="#111827" />
                <rect x="300" y="0" width="3" height="60" fill="#111827" />
                <rect x="308" y="0" width="6" height="60" fill="#111827" />
                <rect x="320" y="0" width="4" height="60" fill="#111827" />
                <rect x="330" y="0" width="4" height="60" fill="#111827" />
              </svg>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: '700', fontSize: '13px', marginTop: '6px', letterSpacing: '0.1em' }}>
                {sampleDocuments.label.trackingCode}
              </div>
            </div>

            {/* Datos de Remitente y Destinatario */}
            <div style={{ borderTop: '2px solid #e5e7eb', borderBottom: '2px solid #e5e7eb', padding: '12px 0', margin: '12px 0', fontSize: '11px' }}>
              <div style={{ marginBottom: '10px' }}>
                <span style={{ fontWeight: '800', color: '#6b7280' }}>ORIGEN / REMITENTE:</span>
                <div style={{ fontWeight: '700', fontSize: '12px' }}>{sampleDocuments.label.sender.name}</div>
                <div>{sampleDocuments.label.sender.address}</div>
                <div>{sampleDocuments.label.sender.city}</div>
              </div>

              <div>
                <span style={{ fontWeight: '800', color: '#6b7280' }}>DESTINATARIO / ENTREGA:</span>
                <div style={{ fontWeight: '800', fontSize: '14px', color: '#111827' }}>{sampleDocuments.label.recipient.name}</div>
                <div style={{ fontWeight: '600' }}>{sampleDocuments.label.recipient.address}</div>
                <div>{sampleDocuments.label.recipient.city}</div>
                <div>Teléfono: {sampleDocuments.label.recipient.phone}</div>
              </div>
            </div>

            {/* Metadatos del Bulto */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', fontSize: '11px', textAlign: 'center', background: '#f3f4f6', padding: '10px', borderRadius: '4px' }}>
              <div>
                <div style={{ color: '#6b7280', fontSize: '10px', fontWeight: '700' }}>PESO</div>
                <div style={{ fontWeight: '800' }}>{sampleDocuments.label.package.weight}</div>
              </div>
              <div>
                <div style={{ color: '#6b7280', fontSize: '10px', fontWeight: '700' }}>VOLUMEN</div>
                <div style={{ fontWeight: '800' }}>{sampleDocuments.label.package.volume}</div>
              </div>
              <div>
                <div style={{ color: '#6b7280', fontSize: '10px', fontWeight: '700' }}>ZONA</div>
                <div style={{ fontWeight: '800' }}>{sampleDocuments.label.package.zone}</div>
              </div>
            </div>

            <div style={{ textAlign: 'center', marginTop: '14px', fontSize: '10px', color: '#9ca3af' }}>
              Emisión generada por microservicio <code>ms-rutaexpress-notify</code> vía OpenPDF
            </div>
          </div>
        )}

        {/* VISTA 2: TICKET DE PREPARACIÓN DE BODEGA */}
        {activeDocTab === 'ticket' && (
          <div style={{
            width: '460px',
            background: 'var(--gray-900)',
            color: 'var(--gray-100)',
            border: '1px solid var(--gray-700)',
            padding: '24px',
            borderRadius: '6px',
            boxShadow: 'var(--shadow-lg)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--gray-700)', paddingBottom: '14px' }}>
              <div>
                <div style={{ fontWeight: '800', fontSize: '17px', color: 'var(--yellow-400)' }}>
                  ORDEN DE PICKING & BODEGA
                </div>
                <div className="code-cell" style={{ fontSize: '12px', color: 'var(--gray-400)', marginTop: '2px' }}>
                  Ticket: <strong>{sampleDocuments.warehouseTicket.ticketNumber}</strong>
                </div>
              </div>
              <span className="badge badge-danger">
                {sampleDocuments.warehouseTicket.priority}
              </span>
            </div>

            <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ background: 'var(--gray-950)', padding: '12px', borderRadius: '6px', border: '1px solid var(--gray-800)' }}>
                <div style={{ fontSize: '11px', color: 'var(--yellow-400)', fontWeight: '700' }}>UBICACIÓN EN BODEGA</div>
                <div style={{ fontSize: '16px', fontWeight: '800', color: 'var(--gray-50)', marginTop: '4px' }}>
                  {sampleDocuments.warehouseTicket.aisle} — {sampleDocuments.warehouseTicket.bay}
                </div>
              </div>

              <div>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', fontWeight: '700' }}>DESCRIPCIÓN DEL BULTO:</span>
                <div style={{ fontSize: '13px', fontWeight: '600', color: 'var(--gray-100)', marginTop: '2px' }}>
                  {sampleDocuments.warehouseTicket.packageDescription}
                </div>
              </div>

              <div>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', fontWeight: '700' }}>ENVÍO ID / SERVICIO:</span>
                <div style={{ fontSize: '13px', marginTop: '2px' }}>
                  <code className="code-cell" style={{ color: 'var(--yellow-400)' }}>{sampleDocuments.warehouseTicket.shipmentId}</code> — {sampleDocuments.warehouseTicket.serviceName}
                </div>
              </div>

              <div style={{ borderTop: '1px solid var(--gray-800)', paddingTop: '12px', fontSize: '12px' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', fontWeight: '700' }}>DESTINO FINAL:</span>
                <div style={{ fontWeight: '600', color: 'var(--gray-200)' }}>{sampleDocuments.warehouseTicket.recipientName}</div>
                <div style={{ color: 'var(--gray-400)' }}>{sampleDocuments.warehouseTicket.recipientAddress}</div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: 'var(--gray-500)', marginTop: '8px' }}>
                <span>Emitido: {sampleDocuments.warehouseTicket.issuedAt}</span>
                <span style={{ color: 'var(--color-success)', fontWeight: '600' }}>Cola: q.cmd.warehouse</span>
              </div>
            </div>
          </div>
        )}

        {/* VISTA 3: PLANTILLA DE CORREO AL CLIENTE */}
        {activeDocTab === 'email' && (
          <div style={{
            width: '500px',
            background: '#ffffff',
            color: '#334155',
            borderRadius: '8px',
            overflow: 'hidden',
            boxShadow: 'var(--shadow-lg)'
          }}>
            {/* Header Email */}
            <div style={{ background: '#0f172a', padding: '20px 24px', borderBottom: '4px solid #f59e0b' }}>
              <div style={{ color: '#ffffff', fontWeight: '800', fontSize: '18px', letterSpacing: '-0.02em' }}>
                RutaExpress
              </div>
              <div style={{ color: '#fbbf24', fontSize: '12px', fontWeight: '600' }}>
                Actualización de tu Envío
              </div>
            </div>

            {/* Cuerpo Email */}
            <div style={{ padding: '24px', fontSize: '13.5px', lineHeight: '1.6' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', marginBottom: '12px' }}>
                ¡Hola {sampleDocuments.email.recipientName}!
              </h3>
              <p style={{ color: '#475569', marginBottom: '16px' }}>
                Queremos confirmarte que tu pedido <strong>{sampleDocuments.email.shipmentId}</strong> ha sido recibido exitosamente y se encuentra en proceso de preparación.
              </p>

              <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '6px', padding: '14px', marginBottom: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '12px' }}>
                  <span style={{ color: '#64748b' }}>Servicio contratado:</span>
                  <strong style={{ color: '#0f172a' }}>{sampleDocuments.email.serviceName}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '12px' }}>
                  <span style={{ color: '#64748b' }}>Entrega estimada:</span>
                  <strong style={{ color: '#059669' }}>{sampleDocuments.email.estimatedDelivery}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                  <span style={{ color: '#64748b' }}>Destino:</span>
                  <strong style={{ color: '#0f172a' }}>{sampleDocuments.email.destination}</strong>
                </div>
              </div>

              <div style={{ textAlign: 'center', margin: '24px 0' }}>
                <a
                  href="#tracking"
                  style={{
                    display: 'inline-block',
                    background: '#f59e0b',
                    color: '#090d16',
                    fontWeight: '700',
                    fontSize: '13px',
                    padding: '10px 24px',
                    borderRadius: '6px',
                    textDecoration: 'none',
                  }}
                >
                  Rastrear Mi Envío en Vivo
                </a>
              </div>

              <p style={{ fontSize: '11px', color: '#94a3b8', textAlign: 'center', borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
                Este es un mensaje automático emitido por <code>q.cmd.email</code> en RutaExpress.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
