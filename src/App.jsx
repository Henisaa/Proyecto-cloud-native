import React, { useState } from 'react';
import { Navbar } from './components/common/Navbar';
import { Sidebar } from './components/common/Sidebar';
import { ServicesView } from './components/catalog/ServicesView';
import { FleetCapacityView } from './components/catalog/FleetCapacityView';
import { TariffMatrixView } from './components/catalog/TariffMatrixView';
import { NotificationMonitorView } from './components/notifications/NotificationMonitorView';
import { DocumentViewer } from './components/notifications/DocumentViewer';
import { DispatcherSimulatorView } from './components/notifications/DispatcherSimulatorView';
import { 
  initialServices, 
  initialCapacities, 
  initialTariffs, 
  initialQueuesTelemetry, 
  initialNotificationLogs 
} from './data/mockData';

export function App() {
  const [currentView, setCurrentView] = useState('services');

  // Estado del Dominio de Catálogo (ms-rutaexpress-catalog)
  const [services, setServices] = useState(initialServices);
  const [capacities, setCapacities] = useState(initialCapacities);
  const [tariffs, setTariffs] = useState(initialTariffs);

  // Estado del Dominio de Notificaciones (ms-rutaexpress-notify)
  const [queues, setQueues] = useState(initialQueuesTelemetry);
  const [logs, setLogs] = useState(initialNotificationLogs);

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
  };

  // Métricas calculadas para barra superior
  const totalMax = capacities.reduce((acc, c) => acc + c.maxDailyPackages, 0);
  const totalBooked = capacities.reduce((acc, c) => acc + c.currentBookedPackages, 0);
  const avgCapacity = totalMax > 0 ? Math.round((totalBooked / totalMax) * 100) : 0;

  return (
    <div className="app-container">
      <Sidebar 
        currentView={currentView} 
        onSelectView={setCurrentView} 
      />

      <div className="main-content">
        <Navbar
          totalServices={services.length}
          avgCapacity={avgCapacity}
          onResetData={handleResetData}
        />

        <main className="content-viewport">
          {currentView === 'services' && (
            <ServicesView
              services={services}
              onAddService={handleAddService}
              onUpdateService={handleUpdateService}
              onToggleStatus={handleToggleStatus}
            />
          )}

          {currentView === 'capacity' && (
            <FleetCapacityView
              capacities={capacities}
              services={services}
              onReserveSlot={handleReserveSlot}
              onReleaseSlot={handleReleaseSlot}
            />
          )}

          {currentView === 'tariffs' && (
            <TariffMatrixView
              tariffs={tariffs}
              services={services}
              onAddTariff={handleAddTariff}
            />
          )}

          {currentView === 'queues' && (
            <NotificationMonitorView
              queues={queues}
              logs={logs}
              onRefresh={handleResetData}
            />
          )}

          {currentView === 'documents' && (
            <DocumentViewer />
          )}

          {currentView === 'dispatch' && (
            <DispatcherSimulatorView
              onDispatchEvent={handleDispatchEvent}
            />
          )}
        </main>
      </div>
    </div>
  );
}
