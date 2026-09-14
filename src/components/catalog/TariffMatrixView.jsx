import React, { useState } from 'react';
import { 
  IconCalculator, 
  IconPlus, 
  IconArrowRight, 
  IconCheckCircle, 
  IconX 
} from '../common/Icons';

export function TariffMatrixView({ tariffs, services, onAddTariff }) {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Estado del cotizador en vivo
  const [calcServiceId, setCalcServiceId] = useState(services[0]?.id || 1);
  const [calcOrigin, setCalcOrigin] = useState('URBAN_CENTER');
  const [calcDest, setCalcDest] = useState('URBAN_CENTER');
  const [calcDistance, setCalcDistance] = useState(15);
  const [calcWeight, setCalcWeight] = useState(3.5);

  const [newTariff, setNewTariff] = useState({
    serviceId: 1,
    originZone: 'URBAN_CENTER',
    destinationZone: 'URBAN_CENTER',
    baseFare: 3000,
    perKmRate: 200,
    perKgRate: 80,
    minFare: 3000,
  });

  const formatCLP = (num) => {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(num);
  };

  // Cálculo en tiempo real
  const activeTariff = tariffs.find(
    (t) => t.serviceId === Number(calcServiceId) && t.originZone === calcOrigin && t.destinationZone === calcDest
  );

  const service = services.find((s) => s.id === Number(calcServiceId));

  let quoteBreakdown = null;
  if (activeTariff) {
    const kmCost = Math.round(calcDistance * activeTariff.perKmRate);
    const kgCost = Math.round(calcWeight * activeTariff.perKgRate);
    const calculatedSubtotal = activeTariff.baseFare + kmCost + kgCost;
    const finalTotal = Math.max(calculatedSubtotal, activeTariff.minFare);
    const minFareApplied = calculatedSubtotal < activeTariff.minFare;

    quoteBreakdown = {
      baseFare: activeTariff.baseFare,
      kmCost,
      kgCost,
      subtotal: calculatedSubtotal,
      minFare: activeTariff.minFare,
      minFareApplied,
      total: finalTotal,
    };
  }

  const handleAddSubmit = (e) => {
    e.preventDefault();
    const serviceObj = services.find(s => s.id === Number(newTariff.serviceId));
    onAddTariff({
      id: Date.now(),
      ...newTariff,
      serviceCode: serviceObj?.code || 'SRV-CUSTOM',
      baseFare: parseFloat(newTariff.baseFare),
      perKmRate: parseFloat(newTariff.perKmRate),
      perKgRate: parseFloat(newTariff.perKgRate),
      minFare: parseFloat(newTariff.minFare),
    });
    setIsModalOpen(false);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconCalculator width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Matriz de Tarifas Zonales & Cotizador
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-catalog</code> — API <code>/api/catalog/services/{'{id}'}/tariffs</code>
          </p>
        </div>

        <button onClick={() => setIsModalOpen(true)} className="btn btn-primary">
          <IconPlus width={16} height={16} />
          <span>Configurar Tarifa</span>
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
        {/* Tabla de Tarifas Configuradas */}
        <div>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Servicio</th>
                  <th>Origen → Destino</th>
                  <th>Tarifa Base</th>
                  <th>Tarifa x Km</th>
                  <th>Tarifa x Kg</th>
                  <th>Mínimo Garantizado</th>
                </tr>
              </thead>
              <tbody>
                {tariffs.map((t) => (
                  <tr key={t.id}>
                    <td className="code-cell" style={{ fontWeight: '700', color: 'var(--yellow-400)' }}>
                      {t.serviceCode}
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: '600' }}>
                        <span>{t.originZone}</span>
                        <IconArrowRight width={12} height={12} style={{ color: 'var(--yellow-500)' }} />
                        <span>{t.destinationZone}</span>
                      </div>
                    </td>
                    <td className="tabular-data font-semibold" style={{ color: 'var(--yellow-400)' }}>{formatCLP(t.baseFare)}</td>
                    <td className="tabular-data" style={{ color: 'var(--gray-300)' }}><strong style={{ color: 'var(--yellow-300)' }}>{formatCLP(t.perKmRate)}</strong> / km</td>
                    <td className="tabular-data" style={{ color: 'var(--gray-300)' }}><strong style={{ color: 'var(--yellow-300)' }}>{formatCLP(t.perKgRate)}</strong> / kg</td>
                    <td className="tabular-data font-semibold" style={{ color: 'var(--yellow-400)' }}>
                      {formatCLP(t.minFare)}
                    </td>

                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Cotizador de Fletes en Vivo */}
        <div>
          <div className="card" style={{ border: '1px solid var(--yellow-600)', position: 'sticky', top: '88px' }}>
            <div className="card-header">
              <div>
                <div className="card-title">
                  <IconCalculator width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                  Cotizador en Tiempo Real
                </div>
                <div className="card-subtitle">
                  Estimación inmediata según reglas de tarificación
                </div>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Servicio Logístico</label>
              <select
                className="form-select"
                value={calcServiceId}
                onChange={(e) => setCalcServiceId(e.target.value)}
              >
                {services.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.code} — {s.name}
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div className="form-group">
                <label className="form-label">Zona Origen</label>
                <select
                  className="form-select"
                  value={calcOrigin}
                  onChange={(e) => setCalcOrigin(e.target.value)}
                >
                  <option value="URBAN_CENTER">URBAN_CENTER</option>
                  <option value="URBAN_PERIPHERY">URBAN_PERIPHERY</option>
                  <option value="INTERURBAN">INTERURBAN</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Zona Destino</label>
                <select
                  className="form-select"
                  value={calcDest}
                  onChange={(e) => setCalcDest(e.target.value)}
                >
                  <option value="URBAN_CENTER">URBAN_CENTER</option>
                  <option value="URBAN_PERIPHERY">URBAN_PERIPHERY</option>
                  <option value="INTERURBAN">INTERURBAN</option>
                </select>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div className="form-group">
                <label className="form-label">Distancia (km)</label>
                <input
                  type="number"
                  min="1"
                  max="1000"
                  className="form-input"
                  value={calcDistance}
                  onChange={(e) => setCalcDistance(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Peso Bulto (kg)</label>
                <input
                  type="number"
                  step="0.1"
                  min="0.1"
                  className="form-input"
                  value={calcWeight}
                  onChange={(e) => setCalcWeight(e.target.value)}
                />
              </div>
            </div>

            {quoteBreakdown ? (
              <div style={{ 
                marginTop: '16px', 
                padding: '16px', 
                borderRadius: '6px', 
                background: 'var(--gray-950)',
                border: '1px solid var(--gray-750)'
              }}>
                <div style={{ fontSize: '11px', fontWeight: '700', color: 'var(--gray-400)', textTransform: 'uppercase', marginBottom: '10px' }}>
                  Desglose de Cotización
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '6px' }}>
                  <span style={{ color: 'var(--gray-400)' }}>Tarifa Base:</span>
                  <span className="tabular-data">{formatCLP(quoteBreakdown.baseFare)}</span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '6px' }}>
                  <span style={{ color: 'var(--gray-400)' }}>Distancia ({calcDistance} km):</span>
                  <span className="tabular-data">{formatCLP(quoteBreakdown.kmCost)}</span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '6px' }}>
                  <span style={{ color: 'var(--gray-400)' }}>Peso ({calcWeight} kg):</span>
                  <span className="tabular-data">{formatCLP(quoteBreakdown.kgCost)}</span>
                </div>

                {quoteBreakdown.minFareApplied && (
                  <div style={{ fontSize: '11px', color: 'var(--yellow-400)', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <IconCheckCircle width={12} height={12} />
                    <span>Ajustado al mínimo contractual garantizado ({formatCLP(quoteBreakdown.minFare)})</span>
                  </div>
                )}

                <div style={{ 
                  marginTop: '12px', 
                  paddingTop: '12px', 
                  borderTop: '1px solid var(--gray-800)',
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'baseline'
                }}>
                  <span style={{ fontWeight: '700', fontSize: '14px', color: 'var(--gray-200)' }}>Total Estimado:</span>
                  <span className="tabular-data" style={{ fontWeight: '800', fontSize: '22px', color: 'var(--yellow-400)' }}>
                    {formatCLP(quoteBreakdown.total)}
                  </span>
                </div>
              </div>
            ) : (
              <div style={{ 
                marginTop: '16px', 
                padding: '12px', 
                borderRadius: '6px', 
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                fontSize: '12px',
                color: 'var(--color-danger)'
              }}>
                No hay una tarifa configurada para la combinación seleccionada de servicio y zonas.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Modal Nueva Tarifa */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            <div className="modal-header">
              <h2 style={{ fontSize: '16px', fontWeight: '700', color: 'var(--gray-50)' }}>
                Configurar Nueva Tarifa Zonal
              </h2>
              <button
                onClick={() => setIsModalOpen(false)}
                style={{ background: 'transparent', border: 'none', color: 'var(--gray-400)', cursor: 'pointer' }}
              >
                <IconX width={18} height={18} />
              </button>
            </div>

            <form onSubmit={handleAddSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Servicio Asociado</label>
                  <select
                    className="form-select"
                    value={newTariff.serviceId}
                    onChange={(e) => setNewTariff({ ...newTariff, serviceId: e.target.value })}
                  >
                    {services.map((s) => (
                      <option key={s.id} value={s.id}>{s.code} — {s.name}</option>
                    ))}
                  </select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label className="form-label">Zona Origen</label>
                    <select
                      className="form-select"
                      value={newTariff.originZone}
                      onChange={(e) => setNewTariff({ ...newTariff, originZone: e.target.value })}
                    >
                      <option value="URBAN_CENTER">URBAN_CENTER</option>
                      <option value="URBAN_PERIPHERY">URBAN_PERIPHERY</option>
                      <option value="INTERURBAN">INTERURBAN</option>
                      <option value="RURAL">RURAL</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Zona Destino</label>
                    <select
                      className="form-select"
                      value={newTariff.destinationZone}
                      onChange={(e) => setNewTariff({ ...newTariff, destinationZone: e.target.value })}
                    >
                      <option value="URBAN_CENTER">URBAN_CENTER</option>
                      <option value="URBAN_PERIPHERY">URBAN_PERIPHERY</option>
                      <option value="INTERURBAN">INTERURBAN</option>
                      <option value="RURAL">RURAL</option>
                    </select>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label className="form-label">Tarifa Base ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={newTariff.baseFare}
                      onChange={(e) => setNewTariff({ ...newTariff, baseFare: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Tarifa Mínima ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={newTariff.minFare}
                      onChange={(e) => setNewTariff({ ...newTariff, minFare: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label className="form-label">Precio por Km ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={newTariff.perKmRate}
                      onChange={(e) => setNewTariff({ ...newTariff, perKmRate: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Precio por Kg ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={newTariff.perKgRate}
                      onChange={(e) => setNewTariff({ ...newTariff, perKgRate: e.target.value })}
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsModalOpen(false)}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn btn-primary">
                  Guardar Tarifa
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
