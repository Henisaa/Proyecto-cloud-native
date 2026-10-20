import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { DashboardPage } from './dashboard.page';

describe('Panel por rol', () => {
  it('mantiene envíos visibles si métricas o servicios fallan', () => {
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { roles: ['ADMIN'], hasRole: () => true } },
      { provide: BffService, useValue: {
        dashboard: () => of({ shipmentsOverview: { available: true, data: { total: 3, byStatus: {}, byCapacity: {}, recent: [] } } }),
        mqMetricas: () => throwError(() => new Error('offline')),
        estadoServicios: () => throwError(() => new Error('offline')),
      } },
    ] });
    const page = TestBed.runInInjectionContext(() => new DashboardPage());
    page.cargar();
    expect(page.resumen()?.total).toBe(3);
    expect(page.errorBroker()).not.toBeNull();
    expect(page.cargandoBase()).toBeFalse();
  });
  it('no consulta administradores para otros roles', () => {
    const mq = jasmine.createSpy('mqMetricas');
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { roles: ['CLIENTE'], hasRole: () => false } },
      { provide: BffService, useValue: { mqMetricas: mq, dashboard: () => of({}) } },
    ] });
    TestBed.runInInjectionContext(() => new DashboardPage()).cargar();
    expect(mq).not.toHaveBeenCalled();
  });
});