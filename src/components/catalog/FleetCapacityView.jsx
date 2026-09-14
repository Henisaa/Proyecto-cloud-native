import React, { useState } from 'react';
import { 
  IconTruck, 
  IconVan, 
  IconMotorcycle, 
  IconElectric, 
  IconCheckCircle, 
  IconAlertCircle, 
  IconPackage,
  IconArrowRight
} from '../common/Icons';

export function FleetCapacityView({ capacities, services, onReserveSlot, onReleaseSlot }) {
  const [selectedZoneFilter, setSelectedZoneFilter] = useState('ALL');
  
  // Estado para el simulador de disponibilidad
  const [simServiceId, setSimServiceId] = useState(services[0]?.id || 1);
  const [simZone, setSimZone] = useState('URBAN_CENTER');
  const [simWeight, setSimWeight] = useState(8.5);
  const [simVolume, setSimVolume] = useState(0.05);
  const [simPackages, setSimPackages] = useState(1);
  const [simResult, setSimResult] = useState(null);

  const zones = [
    { value: 'ALL', label: 'Todas las Zonas' },
    { value: 'URBAN_CENTER', label: 'Radio Urbano Central' },
    { value: 'URBAN_PERIPHERY', label: 'Periferia Urbana' },
    { value: 'INTERURBAN', label: 'Interurbano / Regional' },
    { value: 'RURAL', label: 'Zona Rural' },
  ];

  const filteredCapacities = capacities.filter((c) => {
    return selectedZoneFilter === 'ALL' || c.zone === selectedZoneFilter;
  });

  const getVehicleIcon = (type) => {
    switch (type) {
      case 'MOTORCYCLE': return <IconMotorcycle width={18} height={18} />;
      case 'VAN': return <IconVan width={18} height={18} />;
      case 'TRUCK': return <IconTruck width={18} height={18} />;
      case 'ELECTRIC': return <IconElectric width={18} height={18} />;
      default: return <IconTruck width={18} height={18} />;
    }
  };

  const getVehicleLabel = (type) => {
    switch (type) {
      case 'MOTORCYCLE': return 'Motocicleta Express';
      case 'VAN': return 'Furgón Mediano';
      case 'TRUCK': return 'Camión de Carga';
      case 'ELECTRIC': return 'Flota Eléctrica Urbana';
      default: return type;
    }
  };

  const handleSimulateCheck = (e) => {
    e.preventDefault();
    const targetCapacity = capacities.find(
      (c) => c.serviceId === Number(simServiceId) && c.zone === simZone
    );

    if (!targetCapacity) {
      setSimResult({
        available: false,
        reason: 'No existe flota asignada para esta combinación de servicio y zona geográfica.',
      });
      return;
    }

    const availableSlots = targetCapacity.maxDailyPackages - targetCapacity.currentBookedPackages;
    const canFitPackages = availableSlots >= simPackages;
    const canFitWeight = simWeight <= targetCapacity.maxWeightKg;
    const canFitVolume = simVolume <= targetCapacity.maxVolumeM3;

    if (canFitPackages && canFitWeight && canFitVolume) {
      setSimResult({
        available: true,
        capacityId: targetCapacity.id,
        vehicleType: targetCapacity.vehicleType,
        availableSlots,
        utilizationPct: Math.round(((targetCapacity.currentBookedPackages + Number(simPackages)) / targetCapacity.maxDailyPackages) * 100),
      });
    } else {
      let failReasons = [];
      if (!canFitPackages) failReasons.push(`Cupos insuficientes (requeridos: ${simPackages}, libres: ${availableSlots})`);
      if (!canFitWeight) failReasons.push(`Excede peso máx. vehículo (${simWeight}kg > ${targetCapacity.maxWeightKg}kg)`);
      if (!canFitVolume) failReasons.push(`Excede volumen máx. (${simVolume}m³ > ${targetCapacity.maxVolumeM3}m³)`);

      setSimResult({
        available: false,
        reason: failReasons.join(' | '),
      });
    }
  };

  const handleExecuteReserve = () => {
    if (simResult && simResult.available && simResult.capacityId) {
      onReserveSlot(simResult.capacityId, Number(simPackages));
      setSimResult({
        ...simResult,
        reserved: true,
        message: `¡${simPackages} cupo(s) reservado(s) exitosamente en ${getVehicleLabel(simResult.vehicleType)}!`,
      });
    }
  };

  return (
    <div>
      {/* Encabezado */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconTruck width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Capacidad de Flota & Control de Sobrecupo
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-catalog</code> — Endpoints <code>/api/catalog/services/{'{id}'}/capacity/*</code>
          </p>
        </div>

        <div>
          <select
            className="form-select"
            value={selectedZoneFilter}
            onChange={(e) => setSelectedZoneFilter(e.target.value)}
          >
            {zones.map((z) => (
              <option key={z.value} value={z.value}>{z.label}</option>
            ))}
          </select>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
        {/* Lista de Capacidades por Vehículo y Zona */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {filteredCapacities.map((cap) => {
            const usagePercent = Math.round((cap.currentBookedPackages / cap.maxDailyPackages) * 100);
            const isNearFull = usagePercent >= 80 && usagePercent < 100;
            const isFull = usagePercent >= 100;

            let fillClass = 'capacity-fill-normal';
            if (isNearFull) fillClass = 'capacity-fill-warning';
            if (isFull) fillClass = 'capacity-fill-full';

            return (
              <div key={cap.id} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <div style={{ 
                      width: '40px', 
                      height: '40px', 
                      borderRadius: '8px', 
                      background: 'rgba(245, 158, 11, 0.12)', 
                      border: '1px solid rgba(245, 158, 11, 0.3)',
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center',
                      color: 'var(--yellow-400)'
                    }}>
                      {getVehicleIcon(cap.vehicleType)}
                    </div>
                    <div>
                      <div style={{ fontWeight: '700', fontSize: '15px', color: 'var(--gray-50)' }}>
                        {getVehicleLabel(cap.vehicleType)}
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--gray-400)', display: 'flex', gap: '8px', marginTop: '2px' }}>
                        <span>Servicio: <strong style={{ color: 'var(--yellow-300)' }}>{cap.serviceCode}</strong></span>
                        <span>•</span>
                        <span>Zona: <strong style={{ color: 'var(--gray-200)' }}>{cap.zone}</strong></span>
                      </div>
                    </div>
                  </div>


                  <div>
                    {isFull ? (
                      <span className="badge badge-danger">
                        <IconAlertCircle width={12} height={12} />
                        Cupos Agotados
                      </span>
                    ) : isNearFull ? (
                      <span className="badge badge-yellow">
                        <IconAlertCircle width={12} height={12} />
                        Alta Ocupación ({usagePercent}%)
                      </span>
                    ) : (
                      <span className="badge badge-success">
                        <IconCheckCircle width={12} height={12} />
                        Disponible ({usagePercent}%)
                      </span>
                    )}
                  </div>
                </div>

                {/* Barra de progreso */}
                <div style={{ marginTop: '8px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                    <span style={{ color: 'var(--gray-300)' }}>
                      Paquetes reservados: <strong className="tabular-data" style={{ color: 'var(--yellow-400)' }}>{cap.currentBookedPackages}</strong> de <strong className="tabular-data" style={{ color: 'var(--gray-100)' }}>{cap.maxDailyPackages}</strong>
                    </span>
                    <span className="tabular-data" style={{ fontWeight: '700', color: isFull ? 'var(--color-danger)' : 'var(--yellow-400)' }}>
                      {cap.maxDailyPackages - cap.currentBookedPackages} cupos disponibles
                    </span>
                  </div>
                  <div className="capacity-track">
                    <div className={`capacity-fill ${fillClass}`} style={{ transform: `scaleX(${Math.min(usagePercent, 100) / 100})` }} />
                  </div>
                </div>

                {/* Especificaciones técnicas de carga */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: '12px', marginTop: '14px', paddingTop: '12px', borderTop: '1px solid var(--gray-800)', fontSize: '12px' }}>
                  <div>
                    <span style={{ color: 'var(--gray-400)' }}>Límite de peso por bulto: </span>
                    <strong className="tabular-data" style={{ color: 'var(--yellow-300)' }}>{cap.maxWeightKg} kg</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--gray-400)' }}>Volumen máximo: </span>
                    <strong className="tabular-data" style={{ color: 'var(--yellow-300)' }}>{cap.maxVolumeM3} m³</strong>
                  </div>

                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button
                      onClick={() => onReserveSlot(cap.id, 1)}
                      disabled={isFull}
                      className="btn btn-secondary btn-sm"
                      title="Reservar 1 paquete manual"
                    >
                      +1 Reservar
                    </button>
                    <button
                      onClick={() => onReleaseSlot(cap.id, 1)}
                      disabled={cap.currentBookedPackages === 0}
                      className="btn btn-secondary btn-sm"
                      title="Liberar 1 cupo"
                    >
                      -1 Liberar
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Simulador de Verificación y Reserva en Vivo */}
        <div>
          <div className="card" style={{ position: 'sticky', top: '88px', border: '1px solid var(--yellow-600)' }}>
            <div className="card-header">
              <div>
                <div className="card-title">
                  <IconPackage width={18} height={18} style={{ color: 'var(--yellow-400)' }} />
                  Simulador de Reserva
                </div>
                <div className="card-subtitle">
                  Valida reglas de sobrecupo de <code>ms-rutaexpress-catalog</code>
                </div>
              </div>
            </div>

            <form onSubmit={handleSimulateCheck}>
              <div className="form-group">
                <label className="form-label">Servicio</label>
                <select
                  className="form-select"
                  value={simServiceId}
                  onChange={(e) => setSimServiceId(e.target.value)}
                >
                  {services.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.code} — {s.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Zona de Despacho</label>
                <select
                  className="form-select"
                  value={simZone}
                  onChange={(e) => setSimZone(e.target.value)}
                >
                  <option value="URBAN_CENTER">URBAN_CENTER (Radio Urbano)</option>
                  <option value="URBAN_PERIPHERY">URBAN_PERIPHERY (Periferia)</option>
                  <option value="INTERURBAN">INTERURBAN (Interurbano)</option>
                  <option value="RURAL">RURAL (Rural)</option>
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div className="form-group">
                  <label className="form-label">Peso (kg)</label>
                  <input
                    type="number"
                    step="0.5"
                    min="0.1"
                    className="form-input"
                    value={simWeight}
                    onChange={(e) => setSimWeight(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Volumen (m³)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.001"
                    className="form-input"
                    value={simVolume}
                    onChange={(e) => setSimVolume(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Cantidad de Bultos</label>
                <input
                  type="number"
                  min="1"
                  max="50"
                  className="form-input"
                  value={simPackages}
                  onChange={(e) => setSimPackages(e.target.value)}
                />
              </div>

              <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '8px' }}>
                <span>Comprobar Disponibilidad</span>
                <IconArrowRight width={16} height={16} />
              </button>
            </form>

            {/* Resultado de la simulación */}
            {simResult && (
              <div style={{ 
                marginTop: '16px', 
                padding: '14px', 
                borderRadius: '6px', 
                background: simResult.available ? 'rgba(16, 185, 129, 0.12)' : 'rgba(239, 68, 68, 0.12)',
                border: `1px solid ${simResult.available ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: '700', fontSize: '13px', color: simResult.available ? 'var(--color-success)' : 'var(--color-danger)' }}>
                  {simResult.available ? <IconCheckCircle width={16} height={16} /> : <IconAlertCircle width={16} height={16} />}
                  <span>{simResult.available ? 'CAPACIDAD DISPONIBLE' : 'NO DISPONIBLE'}</span>
                </div>

                {simResult.available ? (
                  <div style={{ marginTop: '8px', fontSize: '12px', color: 'var(--gray-200)' }}>
                    <div>Vehículo asignable: <strong style={{ color: 'var(--yellow-400)' }}>{getVehicleLabel(simResult.vehicleType)}</strong></div>
                    <div>Cupos libres restantes: <strong className="tabular-data">{simResult.availableSlots}</strong></div>
                    <div>Ocupación proyectada: <strong className="tabular-data">{simResult.utilizationPct}%</strong></div>

                    {!simResult.reserved ? (
                      <button
                        onClick={handleExecuteReserve}
                        className="btn btn-primary btn-sm"
                        style={{ width: '100%', marginTop: '12px' }}
                      >
                        Confirmar y Reservar Cupo
                      </button>
                    ) : (
                      <div style={{ marginTop: '10px', color: 'var(--color-success)', fontWeight: '600' }}>
                        {simResult.message}
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--gray-300)' }}>
                    {simResult.reason}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
