import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'shipments',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/shipments/shipments.page').then((m) => m.ShipmentsPage),
  },
  {
    path: 'catalog',
    canActivate: [authGuard, roleGuard(['Admin', 'Despachador'])],
    loadComponent: () => import('./pages/catalog/catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: 'reports',
    canActivate: [authGuard, roleGuard(['Admin'])],
    loadComponent: () => import('./pages/reports/reports.page').then((m) => m.ReportsPage),
  },
  {
    path: 'audit',
    canActivate: [authGuard, roleGuard(['Admin', 'Auditor'])],
    loadComponent: () => import('./pages/audit/audit.page').then((m) => m.AuditPage),
  },
  { path: 'login', redirectTo: '' },
  { path: '**', redirectTo: '' },
];
