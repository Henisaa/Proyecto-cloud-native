import { Data, Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';
import { Rol, TODOS_LOS_ROLES } from './core/roles';

/** Metadatos de ruta: los roles se declaran una sola vez aquí y los usan el guard, el menú y las pantallas. */
export interface RutaMeta {
  roles: readonly Rol[];
  /** Si está presente, la ruta aparece en el menú con esta etiqueta (solo a quien tenga el rol). */
  menu?: string;
}

const meta = (m: RutaMeta): Data => ({ roles: m.roles, menu: m.menu });

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'dashboard',
    canActivate: [roleGuard],
    data: meta({ roles: TODOS_LOS_ROLES, menu: 'Panel' }),
    loadComponent: () => import('./pages/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'shipments',
    canActivate: [roleGuard],
    data: meta({ roles: TODOS_LOS_ROLES, menu: 'Envíos' }),
    loadComponent: () => import('./pages/shipments/shipments.page').then((m) => m.ShipmentsPage),
  },
  {
    // Debe ir antes de shipments/:id para que "new" no se tome como un id.
    path: 'shipments/new',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN', 'DESPACHADOR', 'CLIENTE'] }),
    loadComponent: () => import('./pages/shipments/shipment-new.page').then((m) => m.ShipmentNewPage),
  },
  {
    path: 'shipments/:id',
    canActivate: [roleGuard],
    data: meta({ roles: TODOS_LOS_ROLES }),
    loadComponent: () => import('./pages/shipments/shipment-detail.page').then((m) => m.ShipmentDetailPage),
  },
  {
    path: 'catalog',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN', 'DESPACHADOR', 'CLIENTE'], menu: 'Catálogo' }),
    loadComponent: () => import('./pages/catalog/catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: 'reports',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN'], menu: 'Reportes' }),
    loadComponent: () => import('./pages/reports/reports.page').then((m) => m.ReportsPage),
  },
  {
    path: 'audit',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN', 'AUDITOR'], menu: 'Auditoría' }),
    loadComponent: () => import('./pages/audit/audit.page').then((m) => m.AuditPage),
  },
  {
    path: 'admin/mq',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN'], menu: 'RabbitMQ' }),
    loadComponent: () => import('./pages/admin-mq/admin-mq.page').then((m) => m.AdminMqPage),
  },
  {
    path: 'admin/kafka',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN'], menu: 'Kafka' }),
    loadComponent: () => import('./pages/admin-kafka/admin-kafka.page').then((m) => m.AdminKafkaPage),
  },
  {
    path: 'admin/session',
    canActivate: [roleGuard],
    data: meta({ roles: ['ADMIN'], menu: 'Sesión' }),
    loadComponent: () => import('./pages/session/session.page').then((m) => m.SessionPage),
  },
  {
    path: 'forbidden',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/errors/forbidden.page').then((m) => m.ForbiddenPage),
  },
  { path: 'login', redirectTo: '' },
  {
    path: '**',
    loadComponent: () => import('./pages/errors/not-found.page').then((m) => m.NotFoundPage),
  },
];
