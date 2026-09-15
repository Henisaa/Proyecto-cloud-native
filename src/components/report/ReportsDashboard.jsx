import React, { useState, useMemo, useEffect } from 'react';
import { 
  IconChartBar, 
  IconClock, 
  IconTruck, 
  IconPackage, 
  IconRefresh, 
  IconFileText, 
  IconCheckCircle, 
  IconAlertCircle,
  IconLayers,
  IconServer
} from '../common/Icons';

export function ReportsDashboard({
  kpisDataByRange,
  topServicesByRange,
  currentSubView = 'kpis', // 'kpis' | 'top-services' | 'pipeline' | 'export'
  onSelectSubView
}) {
  // Rango activo seleccionado alineado con el backend de ms-rutaexpress-report ('last1h' | 'last24h' | 'last7d' | 'last30d')
  const [selectedRange, setSelectedRange] = useState('last24h');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastCacheHitTime, setLastCacheHitTime] = useState(new Date().toLocaleTimeString('es-CL'));
  const [liveKpis, setLiveKpis] = useState(null);
  const [liveTopServices, setLiveTopServices] = useState(null);
  const [isLiveConnected, setIsLiveConnected] = useState(false);

  useEffect(() => {
    Promise.all([
      fetch(`/api/report/kpis?range=${selectedRange}`).then(r => r.ok ? r.json() : null),
      fetch(`/api/report/top-services?range=${selectedRange}`).then(r => r.ok ? r.json() : null)
    ])
    .then(([kpis, topSrv]) => {
      if (kpis) {
        setLiveKpis(kpis);
        setIsLiveConnected(true);
      } else {
        setLiveKpis(null);
        setIsLiveConnected(false);
      }
      if (topSrv && Array.isArray(topSrv)) {
        setLiveTopServices(topSrv);
      } else {
        setLiveTopServices(null);
      }
      setLastCacheHitTime(new Date().toLocaleTimeString('es-CL'));
    })
    .catch(() => {
      setLiveKpis(null);
      setLiveTopServices(null);
      setIsLiveConnected(false);
    });
  }, [selectedRange]);

  // Obtener datos del rango activo
  const activeKpis = useMemo(() => {
    if (liveKpis) return liveKpis;
    return (kpisDataByRange && kpisDataByRange[selectedRange]) || {
      range: selectedRange,
      totalShipments: 0,
      shipmentsPerHour: 0.0,
      avgLeadTimeMinutes: 0.0,
      activeShipmentsByStatus: {},
      slaComplianceRate: 98.4
    };
  }, [liveKpis, kpisDataByRange, selectedRange]);

  const activeTopServices = useMemo(() => {
    if (liveTopServices) return liveTopServices;
    return (topServicesByRange && topServicesByRange[selectedRange]) || [];
  }, [liveTopServices, topServicesByRange, selectedRange]);

  // Refrescar caché Caffeine simulado / real
  const handleRefreshCache = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setIsRefreshing(false);
      setLastCacheHitTime(new Date().toLocaleTimeString('es-CL'));
    }, 450);
  };

  // Exportar datos a CSV nativo (Nativo sin dependencias)
  const handleExportCSV = () => {
    const rows = [
      ['RutaExpress Reporte de Rendimiento Logistico'],
      ['Rango Consultado', selectedRange],
      ['Total Envios', activeKpis.totalShipments],
      ['Envios por Hora', activeKpis.shipmentsPerHour],
      ['Lead Time Promedio (minutos)', activeKpis.avgLeadTimeMinutes],
      ['SLA Cumplimiento (%)', activeKpis.slaComplianceRate || 98.4],
      [''],
      ['Distribucion por Estado', 'Cantidad'],
      ...Object.entries(activeKpis.activeShipmentsByStatus || {}).map(([st, cnt]) => [st, cnt]),
      [''],
      ['Top Servicios', 'Envios', 'Porcentaje (%)'],
      ...activeTopServices.map(s => [s.label || s.serviceType, s.totalShipments, s.percentage + '%'])
    ];

    const csvContent = "data:text/csv;charset=utf-8," + rows.map(e => e.join(",")).join("\n");
    const downloadAnchor = document.createElement("a");
    downloadAnchor.setAttribute("href", encodeURI(csvContent));
    downloadAnchor.setAttribute("download", `reporte_rutaexpress_${selectedRange}_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  // Exportar datos a JSON nativo
  const handleExportJSON = () => {
    const payload = {
      generatedAt: new Date().toISOString(),
      range: selectedRange,
      kpis: activeKpis,
      topServices: activeTopServices
    };
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(payload, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `analitica_rutaexpress_${selectedRange}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  // Total de envíos activos en curso (excluye ENTREGADO y CANCELADO)
  const activeInFlightCount = useMemo(() => {
    const st = activeKpis.activeShipmentsByStatus || {};
    return (st.CREADO || 0) + (st.ACEPTADO || 0) + (st.EN_BODEGA || 0) + (st.EN_RUTA || 0);
  }, [activeKpis]);

  // Mapeo de estados con colores del sistema de diseño industrial
  const statusColors = {
    CREADO: { bg: 'rgba(245, 158, 11, 0.2)', border: 'var(--yellow-500)', text: 'var(--yellow-400)' },
    ACEPTADO: { bg: 'rgba(56, 189, 248, 0.2)', border: 'var(--color-info)', text: 'var(--color-info)' },
    EN_BODEGA: { bg: 'rgba(148, 163, 184, 0.2)', border: 'var(--gray-400)', text: 'var(--gray-200)' },
    EN_RUTA: { bg: 'rgba(251, 191, 36, 0.25)', border: 'var(--yellow-400)', text: 'var(--yellow-300)' },
    ENTREGADO: { bg: 'rgba(16, 185, 129, 0.2)', border: 'var(--color-success)', text: 'var(--color-success)' },
    CANCELADO: { bg: 'rgba(239, 68, 68, 0.2)', border: 'var(--color-danger)', text: 'var(--color-danger)' },
  };

  return (
    <div className="reports-module">
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
            <IconChartBar width={28} height={28} style={{ color: 'var(--yellow-400)' }} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)' }}>
                Reportes, KPIs & Analítica Operacional
              </h1>
              <span className="badge badge-yellow">
                ms-rutaexpress-report :8084
              </span>
              <span className="badge badge-success" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                <span className="nav-status-dot active-live" style={{ width: '5px', height: '5px' }} />
                Caffeine Cache 60s
              </span>
            </div>
            <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
              Analítica agregada en streaming (Kafka), tiempos de ciclo logístico y demanda por servicio
            </p>
          </div>
        </div>

        {/* Acciones Globales: Refrescar y Exportar */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <button
            onClick={handleRefreshCache}
            className={`btn btn-secondary btn-sm ${isRefreshing ? 'opacity-50' : ''}`}
            title="Refrescar métricas del microservicio"
          >
            <IconRefresh width={14} height={14} className={isRefreshing ? 'spin-icon' : ''} />
            <span>{isRefreshing ? 'Consultando...' : 'Refrescar'}</span>
          </button>

          <button
            onClick={handleExportCSV}
            className="btn btn-secondary btn-sm"
            title="Exportar reporte tabular a formato CSV"
          >
            <IconFileText width={14} height={14} />
            <span>Exportar CSV</span>
          </button>

          <button
            onClick={handleExportJSON}
            className="btn btn-secondary btn-sm"
            title="Exportar reporte analítico a formato JSON"
          >
            <IconFileText width={14} height={14} />
            <span>JSON</span>
          </button>
        </div>
      </div>

      {/* Selector de Rango Temporal (Contrato @RequestParam range: last1h, last24h, last7d, last30d) */}
      <div className="card" style={{ marginBottom: '20px', padding: '14px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
              Ventana de Tiempo Analítica:
            </span>
            <div style={{ display: 'flex', gap: '6px' }}>
              {[
                { id: 'last1h', label: 'Última 1 Hora (last1h)' },
                { id: 'last24h', label: 'Últimas 24 Horas (last24h)' },
                { id: 'last7d', label: 'Últimos 7 Días (last7d)' },
                { id: 'last30d', label: 'Últimos 30 Días (last30d)' }
              ].map(r => (
                <button
                  key={r.id}
                  onClick={() => setSelectedRange(r.id)}
                  className={`btn btn-sm ${selectedRange === r.id ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ fontSize: '12px', padding: '5px 10px' }}
                >
                  {r.label}
                </button>
              ))}
            </div>
          </div>

          <div style={{ fontSize: '11.5px', color: 'var(--gray-400)' }}>
            Caché TTL: <span style={{ color: 'var(--yellow-400)' }}>60s</span> · Último refresco: <span className="code-cell" style={{ color: 'var(--gray-200)' }}>{lastCacheHitTime}</span>
          </div>
        </div>
      </div>

      {/* Navegación Interna por Sub-Vistas */}
      <div style={{ 
        display: 'flex', 
        gap: '8px', 
        marginBottom: '20px', 
        borderBottom: '1px solid var(--gray-800)', 
        paddingBottom: '12px' 
      }}>
        <button
          className={`btn btn-sm ${currentSubView === 'kpis' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('kpis')}
        >
          <IconChartBar width={15} height={15} />
          <span>Panel de KPIs (/api/report/kpis)</span>
        </button>

        <button
          className={`btn btn-sm ${currentSubView === 'top-services' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('top-services')}
        >
          <IconLayers width={15} height={15} />
          <span>Top Servicios Demandados (/api/report/top-services)</span>
        </button>

        <button
          className={`btn btn-sm ${currentSubView === 'pipeline' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onSelectSubView && onSelectSubView('pipeline')}
        >
          <IconTruck width={15} height={15} />
          <span>Embudo & Estados Activos</span>
        </button>
      </div>

      {/* APARTADO 1: PANEL DE KPIS OPERACIONALES */}
      {(currentSubView === 'kpis' || currentSubView === 'pipeline') && (
        <div style={{ marginBottom: '24px' }}>
          {/* Fila de Tarjetas de Métricas Clave */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '24px' }}>
            {/* Total Envíos */}
            <div className="card" style={{ borderLeft: '4px solid var(--yellow-500)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
                  Total Envíos Procesados
                </span>
                <IconPackage width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
              </div>
              <div className="metric-value" style={{ fontSize: '30px', fontWeight: '800', color: 'var(--gray-50)', marginTop: '8px' }}>
                {activeKpis.totalShipments.toLocaleString('es-CL')}
              </div>
              <div style={{ fontSize: '11.5px', color: 'var(--gray-400)', marginTop: '4px' }}>
                Ventana temporal: <strong style={{ color: 'var(--yellow-300)' }}>{selectedRange}</strong>
              </div>
            </div>

            {/* Envíos por Hora */}
            <div className="card" style={{ borderLeft: '4px solid var(--color-info)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
                  Throughput Horario
                </span>
                <IconClock width={18} height={18} style={{ color: 'var(--color-info)' }} />
              </div>
              <div className="metric-value" style={{ fontSize: '30px', fontWeight: '800', color: 'var(--gray-50)', marginTop: '8px' }}>
                {activeKpis.shipmentsPerHour} <span style={{ fontSize: '14px', fontWeight: '500', color: 'var(--gray-400)' }}>env/h</span>
              </div>
              <div style={{ fontSize: '11.5px', color: 'var(--gray-400)', marginTop: '4px' }}>
                Cálculo: <code className="code-cell">total / horas</code>
              </div>
            </div>

            {/* Lead Time Promedio */}
            <div className="card" style={{ borderLeft: '4px solid var(--yellow-400)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
                  Lead Time Promedio
                </span>
                <IconClock width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
              </div>
              <div className="metric-value" style={{ fontSize: '30px', fontWeight: '800', color: 'var(--gray-50)', marginTop: '8px' }}>
                {activeKpis.avgLeadTimeMinutes} <span style={{ fontSize: '14px', fontWeight: '500', color: 'var(--gray-400)' }}>min</span>
              </div>
              <div style={{ fontSize: '11.5px', color: 'var(--gray-400)', marginTop: '4px' }}>
                Equivalente a: <strong style={{ color: 'var(--gray-200)' }}>{(activeKpis.avgLeadTimeMinutes / 60).toFixed(1)} hrs</strong> ciclo total
              </div>
            </div>

            {/* SLA Cumplimiento */}
            <div className="card" style={{ borderLeft: '4px solid var(--color-success)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <span style={{ fontSize: '11px', color: 'var(--gray-400)', textTransform: 'uppercase', fontWeight: '700' }}>
                  Cumplimiento SLA de Red
                </span>
                <IconCheckCircle width={18} height={18} style={{ color: 'var(--color-success)' }} />
              </div>
              <div className="metric-value" style={{ fontSize: '30px', fontWeight: '800', color: 'var(--color-success)', marginTop: '8px' }}>
                {activeKpis.slaComplianceRate || 98.4}%
              </div>
              <div style={{ fontSize: '11.5px', color: 'var(--gray-400)', marginTop: '4px' }}>
                20 Couriers PyME monitoreados
              </div>
            </div>
          </div>

          {/* Embudo y Distribución de Estados Activos */}
          <div className="card" style={{ marginBottom: '24px' }}>
            <div className="card-header">
              <div>
                <div className="card-title">
                  <IconTruck width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                  Embudo Operacional & Estados Activos ({activeInFlightCount} en tránsito)
                </div>
                <div className="card-subtitle">
                  Desglose del mapa <code>activeShipmentsByStatus</code> calculado por el repositorio analítico
                </div>
              </div>
              <span className="badge badge-yellow">
                Total activos: {activeInFlightCount}
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '12px', marginTop: '12px' }}>
              {['CREADO', 'ACEPTADO', 'EN_BODEGA', 'EN_RUTA', 'ENTREGADO', 'CANCELADO'].map((statusKey) => {
                const count = (activeKpis.activeShipmentsByStatus && activeKpis.activeShipmentsByStatus[statusKey]) || 0;
                const total = activeKpis.totalShipments || 1;
                const pct = ((count / total) * 100).toFixed(1);
                const colors = statusColors[statusKey] || statusColors.CREADO;

                return (
                  <div
                    key={statusKey}
                    style={{
                      background: 'var(--gray-950)',
                      border: `1px solid ${colors.border}`,
                      borderRadius: '8px',
                      padding: '14px',
                      position: 'relative',
                      overflow: 'hidden'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '11px', fontWeight: '700', color: colors.text }}>
                        {statusKey}
                      </span>
                      <span className="badge badge-gray" style={{ fontSize: '10px' }}>
                        {pct}%
                      </span>
                    </div>

                    <div className="metric-value" style={{ fontSize: '24px', fontWeight: '800', color: 'var(--gray-50)', marginTop: '8px' }}>
                      {count.toLocaleString('es-CL')}
                    </div>

                    {/* Barra de progreso miniatura */}
                    <div style={{ width: '100%', height: '4px', background: 'var(--gray-800)', borderRadius: '2px', marginTop: '10px', overflow: 'hidden' }}>
                      <div style={{ width: `${Math.min(pct, 100)}%`, height: '100%', background: colors.border }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* APARTADO 2: TOP SERVICIOS MÁS DEMANDADOS */}
      {(currentSubView === 'top-services' || currentSubView === 'kpis') && (
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">
                <IconLayers width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                Top Servicios Más Demandados (/api/report/top-services)
              </div>
              <div className="card-subtitle">
                Distribución porcentual de solicitudes según el tipo de servicio contratado
              </div>
            </div>
            <span className="badge badge-gray">
              {activeTopServices.length} servicios registrados
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px' }}>
            {activeTopServices.map((srv, idx) => (
              <div
                key={srv.serviceType}
                style={{
                  background: 'var(--gray-950)',
                  border: '1px solid var(--gray-800)',
                  borderRadius: '8px',
                  padding: '16px'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: idx === 0 ? 'var(--yellow-500)' : 'var(--gray-800)',
                      color: idx === 0 ? 'var(--gray-950)' : 'var(--gray-300)',
                      fontWeight: '800',
                      fontSize: '12px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}>
                      #{idx + 1}
                    </div>
                    <div>
                      <strong style={{ color: 'var(--gray-100)', fontSize: '14px' }}>
                        {srv.label || srv.serviceType}
                      </strong>
                      <span className="code-cell" style={{ fontSize: '11px', color: 'var(--gray-500)', marginLeft: '8px' }}>
                        {srv.serviceType}
                      </span>
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <span className="metric-value" style={{ fontSize: '16px', fontWeight: '700', color: 'var(--yellow-400)' }}>
                      {srv.totalShipments.toLocaleString('es-CL')} envíos
                    </span>
                    <span className="badge badge-yellow" style={{ marginLeft: '10px', fontSize: '11px' }}>
                      {srv.percentage}%
                    </span>
                  </div>
                </div>

                {/* Barra de Progreso Porcentual */}
                <div style={{
                  width: '100%',
                  height: '8px',
                  background: 'var(--gray-850)',
                  borderRadius: '4px',
                  overflow: 'hidden',
                  marginTop: '6px'
                }}>
                  <div style={{
                    width: `${srv.percentage}%`,
                    height: '100%',
                    background: idx === 0 ? 'var(--yellow-400)' : idx === 1 ? 'var(--yellow-500)' : 'var(--gray-600)',
                    borderRadius: '4px',
                    transition: 'width 0.4s ease'
                  }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
