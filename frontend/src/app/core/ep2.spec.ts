import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { MsalService, MsalBroadcastService } from '@azure/msal-angular';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { BffService } from './bff.service';
import { runtimeConfig } from './app-config';
import { interpretarError } from './http-error';
import { roleGuard } from './role.guard';
import { sondear } from './polling';
import { validarCola, validarRetencion } from '../shared/mq-validators';

describe('Contratos EP2', () => {
  afterEach(() => TestBed.resetTestingModule());
  it('AuthService normaliza los roles del caché antes de hidratar la cuenta', () => {
    const cuenta = { idTokenClaims: { roles: ['Operador', 'Cliente del dominio'] } };
    TestBed.configureTestingModule({ providers: [
      { provide: MsalService, useValue: { instance: { getActiveAccount: () => cuenta, getAllAccounts: () => [cuenta] } } },
      { provide: MsalBroadcastService, useValue: {} },
    ] });
    const auth = TestBed.inject(AuthService);
    expect(auth.roles).toEqual(['DESPACHADOR', 'CLIENTE']);
    expect(auth.hasRole('Despachador')).toBeTrue();
    expect(auth.hasRole('Admin')).toBeFalse();
  });
  it('guard permite el sinónimo Operador y rechaza un usuario sin rol con 403', () => {
    const auth = { hasSession: () => true, roles: ['Operador'] };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
    const route = { data: { roles: ['DESPACHADOR'] } } as unknown as ActivatedRouteSnapshot;
    const ejecutar = () => TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot));
    expect(ejecutar()).toBeTrue();
    auth.roles = [];
    expect(TestBed.inject(Router).serializeUrl(ejecutar() as ReturnType<Router['createUrlTree']>)).toBe('/forbidden');
  });
  it('conserva el mensaje 409 y errores de validación de ambos formatos', () => {
    expect(interpretarError(new HttpErrorResponse({ status: 409, error: { detail: 'Capacidad rechazada' } })).mensaje).toBe('Capacidad rechazada');
    expect(interpretarError(new HttpErrorResponse({ status: 400, error: { validationErrors: { zone: 'Zona inválida' } } })).campos?.['zone']).toBe('Zona inválida');
    expect(interpretarError(new HttpErrorResponse({ status: 403 })).mensaje).toBe('Sin permiso');
  });
  it('BffService pagina y codifica la routing key usando las rutas del Gateway', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(BffService);
    api.listarEnvios({ page: 2, size: 20, capacity: 'PENDIENTE' }).subscribe();
    const lista = http.expectOne(r => r.url === `${runtimeConfig.apiBaseUrl}/api/bff/shipments`);
    expect(lista.request.params.get('page')).toBe('2');
    expect(lista.request.params.get('capacity')).toBe('PENDIENTE');
    lista.flush({ content: [] });
    api.eliminarBinding({ source: 'ex.demo', destination: 'q.demo', destinationType: 'queue', routingKey: 'demo.#.*' }).subscribe();
    const binding = http.expectOne(r => r.url.endsWith('/api/admin/mq/bindings'));
    expect(binding.request.method).toBe('DELETE');
    expect(binding.request.params.get('routingKey')).toBe('demo.#.*');
    expect(binding.request.urlWithParams).toContain('%23');
    binding.flush({ deleted: true });
    http.verify();
  });
  it('sondeo termina al confirmar capacidad y no sigue consultando', fakeAsync(() => {
    let llamadas = 0;
    let terminado = false;
    sondear(() => of(++llamadas), { cadaMs: 3000, maximoMs: 30000, hasta: n => n === 2 }).subscribe({ complete: () => terminado = true });
    tick(6000);
    expect(terminado).toBeTrue();
    tick(30000);
    expect(llamadas).toBe(2);
  }));
  it('sondeo vence a los 30 segundos aunque nunca llegue respuesta final', fakeAsync(() => {
    let terminado = false;
    sondear(() => of('PENDIENTE'), { cadaMs: 3000, maximoMs: 30000, hasta: () => false }).subscribe({ complete: () => terminado = true });
    tick(30000);
    expect(terminado).toBeTrue();
  }));
  it('formularios rechazan nombres reservados, fracciones, TTL y DLX incompletos', () => {
    expect(validarCola({ name: 'amq.x', durable: true })).not.toBeNull();
    expect(validarCola({ name: 'q.demo', durable: true, maxLength: 1.5 })).not.toBeNull();
    expect(validarCola({ name: 'q.demo', durable: true, ttlMs: 999 })).not.toBeNull();
    expect(validarCola({ name: 'q.demo', durable: true, deadLetterRoutingKey: '#' })).not.toBeNull();
    expect(validarRetencion(1000, 1000, 5)).toBeNull();
  });
});
