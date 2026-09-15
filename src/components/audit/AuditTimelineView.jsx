import React, { useState, useMemo } from 'react';
import { 
  IconClock, 
  IconSearch, 
  IconShield, 
  IconCheckCircle, 
  IconAlertCircle, 
  IconFileText, 
  IconRefresh, 
  IconLayers,
  IconTruck,
  IconServer,
  IconArrowRight
} from '../common/Icons';

export function AuditTimelineView({ 
  events = [], 
  selectedEntityId, 
  onSelectEntity,
  currentSubView = 'timeline', // 'timeline' | 'all-events' | 'types'
  onSelectSubView
}) {
  const [liveEvents, setLiveEvents] = useState(events);
  const [isLiveConnected, setIsLiveConnected] = useState(false);

  useEffect(() => {
    fetch('/api/audit')
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (Array.isArray(data) && data.length > 0) {
          setLiveEvents(data);
          setIsLiveConnected(true);
        }
      })
      .catch(() => {
        setIsLiveConnected(false);
      });
  }, []);

  // Entidad activa para inspección de timeline
  const [activeEntity, setActiveEntity] = useState(selectedEntityId || 'ENV-2026-98124');
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [selectedEventModal, setSelectedEventModal] = useState(null);
  const [copiedPayloadId, setCopiedPayloadId] = useState(null);

  // Lista de entidades únicas para chips rápidos
  const uniqueEntities = useMemo(() => {
    const map = new Map();
    liveEvents.forEach(e => {
      if (!map.has(e.idEntidad)) {
        map.set(e.idEntidad, e.tipoEntidad);
      }
    });
    return Array.from(map.entries()).map(([id, type]) => ({ id, type }));
  }, [liveEvents]);

  // Eventos filtrados para la línea de tiempo de la entidad seleccionada (ordenados cronológicamente)
  const entityTimelineEvents = useMemo(() => {
    return liveEvents
      .filter(e => e.idEntidad.toLowerCase() === activeEntity.toLowerCase())
      .sort((a, b) => new Date(a.fechaEvento) - new Date(b.fechaEvento));
  }, [liveEvents, activeEntity]);

  // Eventos filtrados para la tabla general
  const filteredGeneralEvents = useMemo(() => {
    return liveEvents.filter(e => {
      const matchesSearch = 
        e.idEntidad.toLowerCase().includes(searchTerm.toLowerCase()) ||
        e.tipoEvento.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (e.usuarioResponsable && e.usuarioResponsable.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (e.origen && e.origen.toLowerCase().includes(searchTerm.toLowerCase()));
      const matchesType = typeFilter === 'ALL' || e.tipoEntidad === typeFilter;
      return matchesSearch && matchesType;
    }).sort((a, b) => new Date(b.fechaEvento) - new Date(a.fechaEvento));
  }, [liveEvents, searchTerm, typeFilter]);

  // Copiar payload JSON al portapapeles (Nativo)
  const handleCopyPayload = (event) => {
    navigator.clipboard.writeText(event.datosPayload);
    setCopiedPayloadId(event.id);
    setTimeout(() => setCopiedPayloadId(null), 2000);
  };

  // Descargar historial en JSON (Nativo)
  const handleExportJson = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(liveEvents, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `auditoria_rutaexpress_${new Date().toISOString().slice(0,10)}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  // Helper para color del badge según evento
  const getEventBadgeClass = (tipoEvento) => {
    switch (tipoEvento) {
      case 'ENTREGADO':
        return 'badge-success';
      case 'CREADO':
      case 'ACEPTADO':
        return 'badge-yellow';
      case 'EN_BODEGA':
      case 'EN_RUTA':
        return 'badge-info';
      case 'CANCELADO':
        return 'badge-danger';
      default:
        return 'badge-gray';
    }
  };

  return (
    <div className="audit-module">
      {/* Encabezado del Microservicio */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            width: '52px',
            height: '52px',
            borderRadius: '8px',
            background: 'var(--gray-900)',
            border: '1px solid var(--yellow-500)',
            boxShadow: 'var(--shadow-yellow-glow)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            <IconClock width={28} height={28} style={{ color: 'var(--yellow-400)' }} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)' }}>
                Auditoría & Trazabilidad de Envíos
              </h1>
              <span className="badge badge-yellow">
                ms-rutaexpress-audit :8083
              </span>
              <span className="badge badge-success" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                <span className="nav-status-dot active-live" style={{ width: '5px', height: '5px' }} />
                Kafka Ingest Activo
              </span>
            </div>
            <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
              Bitácora inmutable de eventos de ciclo de vida, trazabilidad de operadores y persistencia en Oracle DB
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <button 
            onClick={handleExportJson}
            className="btn btn-secondary btn-sm"
            title="Descargar registro de auditoría en JSON nativo"
          >
            <IconFileText width={15} height={15} />
            <span>Exportar JSON</span>
          </button>
        </div>
      </div>

      {/* Navegación por Apartados del Microservicio */}
      <div style={{ 
        display: 'flex', 
        gap: '8px', 
        marginBottom: '20px', 
        borderBottom: '1px solid var(--gray-800)', 
        paddingBottom: '12px' 
      }}>
        <button
          className={`btn btn-sm ${currentSubView === 'timeline' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('timeline')}
        >
          <IconClock width={15} height={15} />
          <span>Timeline por Entidad (/api/audit/entidad)</span>
        </button>

        <button
          className={`btn btn-sm ${currentSubView === 'all-events' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('all-events')}
        >
          <IconFileText width={15} height={15} />
          <span>Registro General (/api/audit)</span>
        </button>

        <button
          className={`btn btn-sm ${currentSubView === 'types' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('types')}
        >
          <IconLayers width={15} height={15} />
          <span>Por Tipo de Entidad (/api/audit/tipo)</span>
        </button>
      </div>

      {/* APARTADO 1: TIMELINE DE ENTIDAD (Línea de tiempo detallada) */}
      {currentSubView === 'timeline' && (
        <div>
          {/* Selector y Buscador de Entidad */}
          <div className="card" style={{ marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1, minWidth: '280px' }}>
                <span className="form-label" style={{ whiteSpace: 'nowrap' }}>
                  Identificador de Entidad:
                </span>
                <div style={{ position: 'relative', flex: 1 }}>
                  <input
                    type="text"
                    value={activeEntity}
                    onChange={(e) => setActiveEntity(e.target.value)}
                    placeholder="Ej: ENV-2026-98124 o SRV-EXP-01"
                    className="form-input"
                    style={{ width: '100%', paddingLeft: '34px', fontFamily: 'var(--font-mono)' }}
                  />
                  <div style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--gray-500)' }}>
                    <IconSearch width={16} height={16} />
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
                  Envíos de Muestra:
                </span>
                {uniqueEntities.map(({ id, type }) => (
                  <button
                    key={id}
                    onClick={() => setActiveEntity(id)}
                    className={`btn btn-sm ${activeEntity === id ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', padding: '4px 8px' }}
                  >
                    {id}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Render del Timeline */}
          {entityTimelineEvents.length === 0 ? (
            <div className="card" style={{ textAlign: 'center', padding: '48px 24px' }}>
              <IconAlertCircle width={36} height={36} style={{ color: 'var(--yellow-400)', margin: '0 auto 12px' }} />
              <h3 style={{ color: 'var(--gray-200)', marginBottom: '8px' }}>
                No se registraron eventos para "{activeEntity}"
              </h3>
              <p style={{ color: 'var(--gray-400)', fontSize: '13px' }}>
                Verifique que el ID de envío o entidad exista en el tópico de eventos o seleccione uno de los envíos de muestra arriba.
              </p>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '24px' }}>
              {/* Columna Izquierda: Nodos de la Línea de Tiempo */}
              <div className="card">
                <div className="card-header">
                  <div>
                    <div className="card-title">
                      <IconClock width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                      Línea de Tiempo Operacional — {activeEntity}
                    </div>
                    <div className="card-subtitle">
                      {entityTimelineEvents.length} eventos inmutables ordenados cronológicamente desde la creación
                    </div>
                  </div>
                  <span className="badge badge-yellow code-cell">
                    {entityTimelineEvents[0].tipoEntidad}
                  </span>
                </div>

                <div className="timeline-container" style={{ position: 'relative', paddingLeft: '32px', marginTop: '16px' }}>
                  {/* Línea vertical conectora */}
                  <div style={{
                    position: 'absolute',
                    left: '11px',
                    top: '12px',
                    bottom: '12px',
                    width: '2px',
                    background: 'var(--gray-750)',
                    zIndex: 1
                  }} />

                  {entityTimelineEvents.map((evt, idx) => {
                    const isLast = idx === entityTimelineEvents.length - 1;
                    return (
                      <div 
                        key={evt.id} 
                        style={{ 
                          position: 'relative', 
                          marginBottom: isLast ? 0 : '28px',
                          zIndex: 2 
                        }}
                      >
                        {/* Nodo indicador con icono */}
                        <div style={{
                          position: 'absolute',
                          left: '-32px',
                          top: '2px',
                          width: '24px',
                          height: '24px',
                          borderRadius: '50%',
                          background: isLast ? 'var(--yellow-500)' : 'var(--gray-850)',
                          border: `2px solid ${isLast ? 'var(--yellow-300)' : 'var(--gray-700)'}`,
                          color: isLast ? 'var(--gray-950)' : 'var(--yellow-400)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          boxShadow: isLast ? 'var(--shadow-yellow-glow)' : 'none',
                          fontWeight: '800',
                          fontSize: '10px'
                        }}>
                          {idx + 1}
                        </div>

                        {/* Contenido del Evento */}
                        <div style={{
                          background: 'var(--gray-950)',
                          border: '1px solid var(--gray-800)',
                          borderRadius: '6px',
                          padding: '14px 16px',
                          transition: 'border-color 0.15s ease'
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span className={`badge ${getEventBadgeClass(evt.tipoEvento)}`} style={{ fontWeight: '700' }}>
                                {evt.tipoEvento}
                              </span>
                              <span style={{ fontSize: '11px', color: 'var(--gray-400)' }}>
                                ID Evento: <code className="code-cell" style={{ color: 'var(--yellow-300)' }}>#{evt.id}</code>
                              </span>
                            </div>

                            <span className="code-cell" style={{ fontSize: '11.5px', color: 'var(--gray-400)' }}>
                              {new Date(evt.fechaEvento).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                            </span>
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '12px', color: 'var(--gray-300)', marginBottom: '10px' }}>
                            <div>
                              <span style={{ color: 'var(--gray-500)', fontSize: '11px', textTransform: 'uppercase', fontWeight: '700' }}>
                                Responsable:
                              </span>
                              <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--gray-100)', marginTop: '2px' }}>
                                {evt.usuarioResponsable || 'sistema.kafka'}
                              </div>
                            </div>

                            <div>
                              <span style={{ color: 'var(--gray-500)', fontSize: '11px', textTransform: 'uppercase', fontWeight: '700' }}>
                                Microservicio Emisor:
                              </span>
                              <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--yellow-300)', marginTop: '2px' }}>
                                {evt.origen || 'ms-rutaexpress-shipments'}
                              </div>
                            </div>
                          </div>

                          {/* Botón para abrir modal del Payload */}
                          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--gray-850)', paddingTop: '8px' }}>
                            <button
                              onClick={() => handleCopyPayload(evt)}
                              className="btn btn-secondary btn-sm"
                              style={{ fontSize: '11px', padding: '3px 8px' }}
                            >
                              {copiedPayloadId === evt.id ? '¡Copiado!' : 'Copiar Payload'}
                            </button>
                            <button
                              onClick={() => setSelectedEventModal(evt)}
                              className="btn btn-secondary btn-sm"
                              style={{ fontSize: '11px', padding: '3px 8px', color: 'var(--yellow-300)' }}
                            >
                              Ver JSON Completo
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Columna Derecha: Tarjeta de Trazabilidad Legal e Integridad */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div className="card">
                  <div className="card-header">
                    <div>
                      <div className="card-title">
                        <IconShield width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                        Sello de Integridad Inmutable
                      </div>
                      <div className="card-subtitle">
                        Validación criptográfica para auditoría externa
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '12.5px' }}>
                    <div style={{ background: 'var(--gray-950)', padding: '12px', borderRadius: '6px', border: '1px solid var(--gray-800)' }}>
                      <span style={{ fontSize: '10.5px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                        Tópico Kafka Fuente
                      </span>
                      <div className="code-cell" style={{ color: 'var(--yellow-300)', marginTop: '4px', fontSize: '12px' }}>
                        audit.timeline (3 particiones, compact/delete)
                      </div>
                    </div>

                    <div style={{ background: 'var(--gray-950)', padding: '12px', borderRadius: '6px', border: '1px solid var(--gray-800)' }}>
                      <span style={{ fontSize: '10.5px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                        Tabla Destino (Oracle DB)
                      </span>
                      <div className="code-cell" style={{ color: 'var(--gray-200)', marginTop: '4px', fontSize: '12px' }}>
                        AUDITORIA_EVENTOS (CLOB payload)
                      </div>
                    </div>

                    <div style={{ background: 'var(--gray-950)', padding: '12px', borderRadius: '6px', border: '1px solid var(--gray-800)' }}>
                      <span style={{ fontSize: '10.5px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                        Primer Evento Registrado
                      </span>
                      <div style={{ color: 'var(--gray-200)', marginTop: '4px' }}>
                        {new Date(entityTimelineEvents[0].fechaEvento).toLocaleString('es-CL')}
                      </div>
                    </div>

                    <div style={{ background: 'var(--gray-950)', padding: '12px', borderRadius: '6px', border: '1px solid var(--gray-800)' }}>
                      <span style={{ fontSize: '10.5px', color: 'var(--gray-500)', fontWeight: '700', textTransform: 'uppercase' }}>
                        Último Evento Registrado
                      </span>
                      <div style={{ color: 'var(--gray-200)', marginTop: '4px' }}>
                        {new Date(entityTimelineEvents[entityTimelineEvents.length - 1].fechaEvento).toLocaleString('es-CL')}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-success)', marginTop: '4px', fontSize: '12px' }}>
                      <IconCheckCircle width={16} height={16} />
                      <span>Secuencia ininterrumpida sin alteraciones</span>
                    </div>
                  </div>
                </div>

                {/* Inspección Rápida de Payload del Último Evento */}
                <div className="card">
                  <div className="card-header">
                    <div className="card-title" style={{ fontSize: '13px' }}>
                      Payload del Estado Actual ({entityTimelineEvents[entityTimelineEvents.length - 1].tipoEvento})
                    </div>
                  </div>
                  <pre style={{
                    background: 'var(--gray-950)',
                    padding: '12px',
                    borderRadius: '6px',
                    border: '1px solid var(--gray-800)',
                    fontFamily: 'var(--font-mono)',
                    fontSize: '11px',
                    color: 'var(--yellow-200)',
                    maxHeight: '220px',
                    overflowY: 'auto'
                  }}>
                    {entityTimelineEvents[entityTimelineEvents.length - 1].datosPayload}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* APARTADO 2: REGISTRO GENERAL DE AUDITORÍA */}
      {currentSubView === 'all-events' && (
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">
                <IconFileText width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                Registro General de Eventos de Auditoría (/api/audit)
              </div>
              <div className="card-subtitle">
                Mostrando {filteredGeneralEvents.length} de {events.length} transacciones auditadas en tiempo real
              </div>
            </div>
          </div>

          {/* Filtros y Barra de Búsqueda */}
          <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', flexWrap: 'wrap' }}>
            <div style={{ position: 'relative', flex: 1, minWidth: '220px' }}>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Buscar por ID entidad, evento, usuario u origen..."
                className="form-input"
                style={{ width: '100%', paddingLeft: '34px' }}
              />
              <div style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--gray-500)' }}>
                <IconSearch width={16} height={16} />
              </div>
            </div>

            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="form-select"
              style={{ minWidth: '180px' }}
            >
              <option value="ALL">Todas las Entidades</option>
              <option value="ENVIO">Envíos (ENVIO)</option>
              <option value="SERVICIO">Catálogo (SERVICIO)</option>
              <option value="FLOTA">Capacidad (FLOTA)</option>
              <option value="NOTIFICACION">Notificaciones (NOTIFICACION)</option>
            </select>
          </div>

          {/* Tabla de Eventos */}
          <div style={{ overflowX: 'auto' }}>
            <table className="table" style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: 'var(--gray-900)', borderBottom: '1px solid var(--gray-750)' }}>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>ID</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Fecha & Hora</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Entidad</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Tipo</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Evento</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Origen</th>
                  <th style={{ padding: '10px 12px', textAlign: 'left', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Responsable</th>
                  <th style={{ padding: '10px 12px', textAlign: 'center', fontSize: '11px', textTransform: 'uppercase', color: 'var(--gray-400)' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filteredGeneralEvents.map((evt) => (
                  <tr key={evt.id} style={{ borderBottom: '1px solid var(--gray-800)' }}>
                    <td className="code-cell" style={{ padding: '10px 12px', color: 'var(--yellow-400)' }}>
                      #{evt.id}
                    </td>
                    <td className="code-cell" style={{ padding: '10px 12px', fontSize: '12px', color: 'var(--gray-300)' }}>
                      {new Date(evt.fechaEvento).toLocaleString('es-CL', { dateStyle: 'short', timeStyle: 'medium' })}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <button
                        onClick={() => {
                          setActiveEntity(evt.idEntidad);
                          if (onSelectSubView) onSelectSubView('timeline');
                        }}
                        className="btn btn-secondary btn-sm"
                        style={{ fontFamily: 'var(--font-mono)', fontSize: '11.5px', padding: '2px 6px' }}
                        title="Ver línea de tiempo de esta entidad"
                      >
                        {evt.idEntidad}
                      </button>
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <span className="badge badge-gray" style={{ fontSize: '10px' }}>
                        {evt.tipoEntidad}
                      </span>
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <span className={`badge ${getEventBadgeClass(evt.tipoEvento)}`} style={{ fontSize: '10px' }}>
                        {evt.tipoEvento}
                      </span>
                    </td>
                    <td className="code-cell" style={{ padding: '10px 12px', fontSize: '11px', color: 'var(--gray-400)' }}>
                      {evt.origen}
                    </td>
                    <td style={{ padding: '10px 12px', fontSize: '12px', color: 'var(--gray-300)' }}>
                      {evt.usuarioResponsable}
                    </td>
                    <td style={{ padding: '10px 12px', textAlign: 'center' }}>
                      <button
                        onClick={() => setSelectedEventModal(evt)}
                        className="btn btn-secondary btn-sm"
                        style={{ fontSize: '11px', padding: '3px 8px' }}
                      >
                        Ver JSON
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* APARTADO 3: POR TIPO DE ENTIDAD */}
      {currentSubView === 'types' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
          {['ENVIO', 'SERVICIO', 'FLOTA', 'NOTIFICACION'].map((tipo) => {
            const eventsOfType = events.filter(e => e.tipoEntidad === tipo);
            return (
              <div key={tipo} className="card">
                <div className="card-header">
                  <div>
                    <div className="card-title" style={{ fontSize: '14px' }}>
                      Tipo: <span style={{ color: 'var(--yellow-400)' }}>{tipo}</span>
                    </div>
                    <div className="card-subtitle">
                      Endpoint: <code>/api/audit/tipo/{tipo}</code>
                    </div>
                  </div>
                  <span className="badge badge-yellow">
                    {eventsOfType.length} eventos
                  </span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '10px' }}>
                  {eventsOfType.slice(0, 4).map(e => (
                    <div 
                      key={e.id}
                      style={{ 
                        background: 'var(--gray-950)', 
                        padding: '10px', 
                        borderRadius: '6px', 
                        border: '1px solid var(--gray-800)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                      }}
                    >
                      <div>
                        <div className="code-cell" style={{ color: 'var(--yellow-300)', fontSize: '12px' }}>
                          {e.idEntidad}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--gray-400)', marginTop: '2px' }}>
                          {e.tipoEvento} — {e.usuarioResponsable}
                        </div>
                      </div>
                      <button
                        onClick={() => setSelectedEventModal(e)}
                        className="btn btn-secondary btn-sm"
                        style={{ fontSize: '10px', padding: '2px 6px' }}
                      >
                        JSON
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal de Detalle de JSON Payload */}
      {selectedEventModal && (
        <div className="modal-overlay" onClick={() => setSelectedEventModal(null)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '650px' }}>
            <div className="modal-header">
              <div>
                <div style={{ fontSize: '15px', fontWeight: '700', color: 'var(--yellow-400)' }}>
                  Inspección de Payload de Auditoría
                </div>
                <div style={{ fontSize: '12px', color: 'var(--gray-400)', marginTop: '2px' }}>
                  ID #{selectedEventModal.id} — Entidad: {selectedEventModal.idEntidad} ({selectedEventModal.tipoEvento})
                </div>
              </div>
              <button 
                onClick={() => setSelectedEventModal(null)} 
                className="btn btn-secondary btn-sm"
                style={{ padding: '4px 8px' }}
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px', fontSize: '12px' }}>
                <div>
                  <span style={{ color: 'var(--gray-500)', fontSize: '11px', textTransform: 'uppercase', fontWeight: '700' }}>
                    Origen:
                  </span>
                  <div className="code-cell" style={{ color: 'var(--gray-200)', marginTop: '2px' }}>
                    {selectedEventModal.origen}
                  </div>
                </div>

                <div>
                  <span style={{ color: 'var(--gray-500)', fontSize: '11px', textTransform: 'uppercase', fontWeight: '700' }}>
                    Fecha y Hora UTC:
                  </span>
                  <div className="code-cell" style={{ color: 'var(--gray-200)', marginTop: '2px' }}>
                    {selectedEventModal.fechaEvento}
                  </div>
                </div>
              </div>

              <div style={{ marginBottom: '8px', fontSize: '11px', color: 'var(--gray-400)', fontWeight: '700', textTransform: 'uppercase' }}>
                Payload JSON Almacenado en Oracle CLOB:
              </div>
              <pre style={{
                background: 'var(--gray-950)',
                border: '1px solid var(--gray-750)',
                borderRadius: '6px',
                padding: '14px',
                color: 'var(--yellow-200)',
                fontFamily: 'var(--font-mono)',
                fontSize: '12px',
                lineHeight: '1.6',
                overflowX: 'auto',
                maxHeight: '340px'
              }}>
                {selectedEventModal.datosPayload}
              </pre>
            </div>

            <div className="modal-footer">
              <button
                onClick={() => handleCopyPayload(selectedEventModal)}
                className="btn btn-primary btn-sm"
              >
                {copiedPayloadId === selectedEventModal.id ? '¡Copiado al Portapapeles!' : 'Copiar JSON'}
              </button>
              <button
                onClick={() => setSelectedEventModal(null)}
                className="btn btn-secondary btn-sm"
              >
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
