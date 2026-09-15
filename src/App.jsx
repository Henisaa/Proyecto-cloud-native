import React, { useState } from 'react';
import { Navbar } from './components/common/Navbar';
import { Sidebar } from './components/common/Sidebar';
import { MicroservicePlaceholder } from './components/common/MicroservicePlaceholder';
import { ServicesView } from './components/catalog/ServicesView';
import { FleetCapacityView } from './components/catalog/FleetCapacityView';
import { TariffMatrixView } from './components/catalog/TariffMatrixView';
import { NotificationMonitorView } from './components/notifications/NotificationMonitorView';
import { DocumentViewer } from './components/notifications/DocumentViewer';
import { DispatcherSimulatorView } from './components/notifications/DispatcherSimulatorView';
import { AuditTimelineView } from './components/audit/AuditTimelineView';
import { ReportsDashboard } from './components/report/ReportsDashboard';
import { 
  initialServices, 
  initialCapacities, 
  initialTariffs, 
  initialQueuesTelemetry, 
  initialNotificationLogs 
} from './data/mockData';
import {
  initialAuditEvents,
  reportKpisByRange,
  reportTopServicesByRange
} from './data/auditReportData';

export function App() {
  // Dominio activo seleccionado en la Navbar ('catalog' | 'notify' | 'shipments' | 'audit' | 'report' | 'bff' | 'messaging')
  const [activeDomain, setActiveDomain] = useState('catalog');
  
  // Sub-vista activa dentro del dominio seleccionado
  const [currentView, setCurrentView] = useState('services');

  // Estado del Dominio de Catálogo (ms-rutaexpress-catalog)
  const [services, setServices] = useState(initialServices);
  const [capacities, setCapacities] = useState(initialCapacities);
  const [tariffs, setTariffs] = useState(initialTariffs);

  // Estado del Dominio de Notificaciones (ms-rutaexpress-notify)
  const [queues, setQueues] = useState(initialQueuesTelemetry);
  const [logs, setLogs] = useState(initialNotificationLogs);

  // Estado del Dominio de Auditoría (ms-rutaexpress-audit)
  const [auditEvents, setAuditEvents] = useState(initialAuditEvents);

  // Cambio de dominio desde la Navbar o Sidebar
  const handleSelectDomain = (domainId) => {
    setActiveDomain(domainId);
    if (domainId === 'catalog') {
      setCurrentView('services');
    } else if (domainId === 'notify') {
      setCurrentView('queues');
    } else if (domainId === 'audit') {
      setCurrentView('timeline');
    } else if (domainId === 'report') {
      setCurrentView('kpis');
    }
  };

  // Handlers para Catálogo
  const handleAddService = (newSrv) => {
    setServices((prev) => [newSrv, ...prev]);
  };

  const handleUpdateService = (updatedSrv) => {
    setServices((prev) => prev.map((s) => (s.id === updatedSrv.id ? updatedSrv : s)));
  };

  const handleToggleStatus = (id) => {
    setServices((prev) =>
      prev.map((s) => {
        if (s.id === id) {
          const nextStatus = s.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
          return { ...s, status: nextStatus };
        }
        return s;
      })
    );
  };

  const handleReserveSlot = (capacityId, qty = 1) => {
    setCapacities((prev) =>
      prev.map((c) => {
        if (c.id === capacityId) {
          const nextBooked = Math.min(c.currentBookedPackages + qty, c.maxDailyPackages);
          return { ...c, currentBookedPackages: nextBooked };
        }
        return c;
      })
    );
  };

  const handleReleaseSlot = (capacityId, qty = 1) => {
    setCapacities((prev) =>
      prev.map((c) => {
        if (c.id === capacityId) {
          const nextBooked = Math.max(c.currentBookedPackages - qty, 0);
          return { ...c, currentBookedPackages: nextBooked };
        }
        return c;
      })
    );
  };

  const handleAddTariff = (newTariff) => {
    setTariffs((prev) => [newTariff, ...prev]);
  };

  // Handlers para Notificaciones
  const handleDispatchEvent = (newLog, queueName) => {
    setLogs((prev) => [newLog, ...prev]);
    setQueues((prev) =>
      prev.map((q) => {
        if (q.name === queueName) {
          return {
            ...q,
            totalProcessed: q.totalProcessed + 1,
            messagesReady: Math.max(q.messagesReady - 1, 0),
          };
        }
        return q;
      })
    );
  };

  const handleResetData = () => {
    setServices(initialServices);
    setCapacities(initialCapacities);
    setTariffs(initialTariffs);
    setQueues(initialQueuesTelemetry);
    setLogs(initialNotificationLogs);
    setAuditEvents(initialAuditEvents);
  };

  // Métricas calculadas para barra superior
  const totalMax = capacities.reduce((acc, c) => acc + c.maxDailyPackages, 0);
  const totalBooked = capacities.reduce((acc, c) => acc + c.currentBookedPackages, 0);
  const avgCapacity = totalMax > 0 ? Math.round((totalBooked / totalMax) * 100) : 0;

  return (
    <div className="app-container">
      <Sidebar 
        activeDomain={activeDomain}
        currentView={currentView} 
        onSelectView={setCurrentView}
        onSelectDomain={handleSelectDomain}
      />

      <div className="main-content">
        <Navbar
          activeDomain={activeDomain}
          onSelectDomain={handleSelectDomain}
          totalServices={services.length}
          avgCapacity={avgCapacity}
          onResetData={handleResetData}
        />

        <main className="content-viewport">
          {/* VISTAS DE CATÁLOGO */}
          {activeDomain === 'catalog' && currentView === 'services' && (
            <ServicesView
              services={services}
              onAddService={handleAddService}
              onUpdateService={handleUpdateService}
              onToggleStatus={handleToggleStatus}
            />
          )}

          {activeDomain === 'catalog' && currentView === 'capacity' && (
            <FleetCapacityView
              capacities={capacities}
              services={services}
              onReserveSlot={handleReserveSlot}
              onReleaseSlot={handleReleaseSlot}
            />
          )}

          {activeDomain === 'catalog' && currentView === 'tariffs' && (
            <TariffMatrixView
              tariffs={tariffs}
              services={services}
              onAddTariff={handleAddTariff}
            />
          )}

          {/* VISTAS DE NOTIFICACIONES */}
          {activeDomain === 'notify' && currentView === 'queues' && (
            <NotificationMonitorView
              queues={queues}
              logs={logs}
              onRefresh={handleResetData}
            />
          )}

          {activeDomain === 'notify' && currentView === 'documents' && (
            <DocumentViewer />
          )}

          {activeDomain === 'notify' && currentView === 'dispatch' && (
            <DispatcherSimulatorView
              onDispatchEvent={handleDispatchEvent}
            />
          )}

          {/* VISTAS DE AUDITORÍA (ms-rutaexpress-audit) */}
          {activeDomain === 'audit' && (
            <AuditTimelineView
              events={auditEvents}
              currentSubView={currentView}
              onSelectSubView={setCurrentView}
            />
          )}

          {/* VISTAS DE REPORTES & KPIS (ms-rutaexpress-report) */}
          {activeDomain === 'report' && (
            <ReportsDashboard
              kpisDataByRange={reportKpisByRange}
              topServicesByRange={reportTopServicesByRange}
              currentSubView={currentView}
              onSelectSubView={setCurrentView}
            />
          )}

          {/* ESPACIO RESERVADO PARA LOS OTROS MICROSERVICIOS */}
          {!['catalog', 'notify', 'audit', 'report'].includes(activeDomain) && (
            <MicroservicePlaceholder
              microserviceId={activeDomain}
              onSwitchToDemoDomain={handleSelectDomain}
            />
          )}
        </main>
      </div>
    </div>
  );
}
