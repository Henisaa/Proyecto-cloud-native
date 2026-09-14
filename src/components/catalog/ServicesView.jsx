import React, { useState } from 'react';
import { 
  IconLayers, 
  IconPlus, 
  IconSearch, 
  IconFilter, 
  IconCheckCircle, 
  IconAlertCircle, 
  IconClock, 
  IconX 
} from '../common/Icons';

export function ServicesView({ services, onAddService, onUpdateService, onToggleStatus }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingService, setEditingService] = useState(null);

  // Formulario para nuevo o editar servicio
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    category: 'STANDARD',
    status: 'ACTIVE',
    estimatedDeliveryHours: 24,
    basePrice: 3000,
    pricePerKm: 200,
    pricePerKg: 100,
  });

  const categories = [
    { value: 'ALL', label: 'Todas las categorías' },
    { value: 'SAME_DAY', label: 'Mismo Día (SAME_DAY)' },
    { value: 'STANDARD', label: 'Estándar 24h (STANDARD)' },
    { value: 'FRAGILE', label: 'Frágil / Delicado (FRAGILE)' },
    { value: 'COLD_CHAIN', label: 'Cadena de Frío (COLD_CHAIN)' },
  ];

  const filteredServices = services.filter((srv) => {
    const matchesSearch = 
      srv.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      srv.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      srv.description.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCategory = selectedCategory === 'ALL' || srv.category === selectedCategory;
    const matchesStatus = selectedStatus === 'ALL' || srv.status === selectedStatus;
    return matchesSearch && matchesCategory && matchesStatus;
  });

  const handleOpenCreate = () => {
    setEditingService(null);
    setFormData({
      code: `SRV-EXP-0${services.length + 1}`,
      name: '',
      description: '',
      category: 'STANDARD',
      status: 'ACTIVE',
      estimatedDeliveryHours: 24,
      basePrice: 3000,
      pricePerKm: 200,
      pricePerKg: 100,
    });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (srv) => {
    setEditingService(srv);
    setFormData({
      code: srv.code,
      name: srv.name,
      description: srv.description,
      category: srv.category,
      status: srv.status,
      estimatedDeliveryHours: srv.estimatedDeliveryHours,
      basePrice: srv.basePrice,
      pricePerKm: srv.pricePerKm,
      pricePerKg: srv.pricePerKg,
    });
    setIsModalOpen(true);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name || !formData.code) return;

    if (editingService) {
      onUpdateService({
        ...editingService,
        ...formData,
        basePrice: parseFloat(formData.basePrice),
        pricePerKm: parseFloat(formData.pricePerKm),
        pricePerKg: parseFloat(formData.pricePerKg),
        estimatedDeliveryHours: parseInt(formData.estimatedDeliveryHours, 10),
      });
    } else {
      onAddService({
        id: Date.now(),
        ...formData,
        basePrice: parseFloat(formData.basePrice),
        pricePerKm: parseFloat(formData.pricePerKm),
        pricePerKg: parseFloat(formData.pricePerKg),
        estimatedDeliveryHours: parseInt(formData.estimatedDeliveryHours, 10),
        active: true,
        createdAt: new Date().toISOString(),
      });
    }
    setIsModalOpen(false);
  };

  const formatCLP = (num) => {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(num);
  };

  return (
    <div>
      {/* Encabezado y Métricas Rápidas */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: '800', color: 'var(--gray-50)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconLayers width={24} height={24} style={{ color: 'var(--yellow-400)' }} />
            Catálogo de Servicios Logísticos
          </h1>
          <p style={{ color: 'var(--gray-400)', marginTop: '4px', fontSize: '13px' }}>
            Microservicio <code>ms-rutaexpress-catalog</code> — API REST <code>/api/catalog/services</code>
          </p>
        </div>

        <button onClick={handleOpenCreate} className="btn btn-primary">
          <IconPlus width={16} height={16} />
          <span>Nuevo Servicio</span>
        </button>
      </div>

      {/* Tarjetas de Métricas de Resumen */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div className="card">
          <div className="card-subtitle">TOTAL SERVICIOS</div>
          <div className="metric-value" style={{ fontSize: '26px', fontWeight: '800', color: 'var(--yellow-400)', marginTop: '6px' }}>
            {services.length}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--gray-400)', marginTop: '4px' }}>
            Registrados en base de datos Oracle
          </div>
        </div>


        <div className="card">
          <div className="card-subtitle">SERVICIOS ACTIVOS</div>
          <div className="metric-value" style={{ fontSize: '26px', fontWeight: '800', color: 'var(--color-success)', marginTop: '6px' }}>
            {services.filter(s => s.status === 'ACTIVE').length}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--gray-400)', marginTop: '4px' }}>
            Disponibles para despacho inmediato
          </div>
        </div>

        <div className="card">
          <div className="card-subtitle">SLA PROMEDIO ESTIMADO</div>
          <div className="metric-value" style={{ fontSize: '26px', fontWeight: '800', color: 'var(--yellow-400)', marginTop: '6px' }}>
            {Math.round(services.reduce((acc, s) => acc + s.estimatedDeliveryHours, 0) / (services.length || 1))} hrs
          </div>
          <div style={{ fontSize: '12px', color: 'var(--gray-400)', marginTop: '4px' }}>
            Ventana de entrega comprometida
          </div>
        </div>
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="card" style={{ marginBottom: '20px', padding: '16px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
          <div style={{ flex: '1 1 240px', position: 'relative' }}>
            <div style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--gray-500)' }}>
              <IconSearch width={16} height={16} />
            </div>
            <input
              type="text"
              className="form-input"
              style={{ width: '100%', paddingLeft: '36px' }}
              placeholder="Buscar por código, nombre o descripción..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div style={{ minWidth: '200px' }}>
            <select
              className="form-select"
              style={{ width: '100%' }}
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              {categories.map((c) => (
                <option key={c.value} value={c.value}>{c.label}</option>
              ))}
            </select>
          </div>

          <div style={{ minWidth: '150px' }}>
            <select
              className="form-select"
              style={{ width: '100%' }}
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <option value="ALL">Todos los estados</option>
              <option value="ACTIVE">Activos</option>
              <option value="SUSPENDED">Suspendidos</option>
            </select>
          </div>
        </div>
      </div>

      {/* Tabla de Servicios */}
      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre del Servicio</th>
              <th>Categoría</th>
              <th>SLA Estimado</th>
              <th>Tarifa Base</th>
              <th>Por Km / Por Kg</th>
              <th>Estado</th>
              <th style={{ textAlign: 'right' }}>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {filteredServices.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--gray-400)' }}>
                  No se encontraron servicios que coincidan con los filtros aplicados.
                </td>
              </tr>
            ) : (
              filteredServices.map((srv) => (
                <tr key={srv.id}>
                  <td className="code-cell" style={{ fontWeight: '700', color: 'var(--yellow-400)' }}>
                    {srv.code}
                  </td>
                  <td>
                    <div style={{ fontWeight: '600', color: 'var(--gray-50)' }}>{srv.name}</div>
                    <div style={{ fontSize: '12px', color: 'var(--gray-400)', maxWidth: '380px', marginTop: '2px' }}>
                      {srv.description}
                    </div>
                  </td>
                  <td>
                    <span className="badge badge-yellow">
                      {srv.category}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--gray-200)' }}>
                      <IconClock width={14} height={14} style={{ color: 'var(--yellow-400)' }} />
                      <span className="tabular-data font-semibold">{srv.estimatedDeliveryHours} horas</span>
                    </div>
                  </td>
                  <td className="tabular-data" style={{ fontWeight: '700', color: 'var(--yellow-300)' }}>
                    {formatCLP(srv.basePrice)}
                  </td>

                  <td className="tabular-data" style={{ fontSize: '12px', color: 'var(--gray-400)' }}>
                    <div>Km: <strong style={{ color: 'var(--gray-200)' }}>{formatCLP(srv.pricePerKm)}</strong></div>
                    <div>Kg: <strong style={{ color: 'var(--gray-200)' }}>{formatCLP(srv.pricePerKg)}</strong></div>
                  </td>
                  <td>
                    {srv.status === 'ACTIVE' ? (
                      <span className="badge badge-success">
                        <IconCheckCircle width={12} height={12} />
                        Activo
                      </span>
                    ) : (
                      <span className="badge badge-danger">
                        <IconAlertCircle width={12} height={12} />
                        Suspendido
                      </span>
                    )}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                      <button
                        onClick={() => handleOpenEdit(srv)}
                        className="btn btn-secondary btn-sm"
                      >
                        Editar
                      </button>
                      <button
                        onClick={() => onToggleStatus(srv.id)}
                        className={`btn btn-sm ${srv.status === 'ACTIVE' ? 'btn-danger' : 'btn-outline-yellow'}`}
                      >
                        {srv.status === 'ACTIVE' ? 'Suspender' : 'Activar'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Modal Crear / Editar */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            <div className="modal-header">
              <h2 style={{ fontSize: '16px', fontWeight: '700', color: 'var(--gray-50)' }}>
                {editingService ? `Editar Servicio: ${editingService.code}` : 'Nuevo Servicio Logístico'}
              </h2>
              <button
                onClick={() => setIsModalOpen(false)}
                style={{ background: 'transparent', border: 'none', color: 'var(--gray-400)', cursor: 'pointer' }}
              >
                <IconX width={18} height={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="modal-body">
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '16px' }}>
                  <div className="form-group">
                    <label className="form-label">Código Único</label>
                    <input
                      type="text"
                      className="form-input code-cell"
                      required
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                      placeholder="SRV-EXP-01"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Nombre Comercial</label>
                    <input
                      type="text"
                      className="form-input"
                      required
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      placeholder="Ej: RutaExpress Mismo Día"
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Descripción Operativa</label>
                  <textarea
                    className="form-textarea"
                    rows={3}
                    required
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    placeholder="Detalles de cobertura, condiciones del despacho y embalaje..."
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label className="form-label">Categoría</label>
                    <select
                      className="form-select"
                      value={formData.category}
                      onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    >
                      <option value="SAME_DAY">Mismo Día (SAME_DAY)</option>
                      <option value="STANDARD">Estándar 24h (STANDARD)</option>
                      <option value="EXPRESS">Express (EXPRESS)</option>
                      <option value="FRAGILE">Frágil (FRAGILE)</option>
                      <option value="COLD_CHAIN">Cadena de Frío (COLD_CHAIN)</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">SLA Entrega (Horas)</label>
                    <input
                      type="number"
                      min={1}
                      max={168}
                      className="form-input"
                      required
                      value={formData.estimatedDeliveryHours}
                      onChange={(e) => setFormData({ ...formData, estimatedDeliveryHours: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
                  <div className="form-group">
                    <label className="form-label">Precio Base ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={formData.basePrice}
                      onChange={(e) => setFormData({ ...formData, basePrice: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Precio por Km ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={formData.pricePerKm}
                      onChange={(e) => setFormData({ ...formData, pricePerKm: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Precio por Kg ($)</label>
                    <input
                      type="number"
                      min={0}
                      className="form-input"
                      required
                      value={formData.pricePerKg}
                      onChange={(e) => setFormData({ ...formData, pricePerKg: e.target.value })}
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
                  {editingService ? 'Guardar Cambios' : 'Crear Servicio'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
