// Compiled ONLY by the e2e Docker target. The production entrypoint remains src/main.ts.
import { signal } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { MsalBroadcastService } from '@azure/msal-angular';
import { EMPTY } from 'rxjs';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { AuthService } from './app/core/auth.service';
import { loadRuntimeConfig, runtimeConfig } from './app/core/app-config';
import { tieneAlgunRol } from './app/core/roles';
import { errorInterceptor } from './app/core/error.interceptor';

async function main(): Promise<void> {
  await loadRuntimeConfig();
  const role = sessionStorage.getItem('ci-role') || 'ADMIN';
  const response = await fetch(`${runtimeConfig.apiBaseUrl}/fixture/session?role=${encodeURIComponent(role)}`);
  if (!response.ok) throw new Error('CI issuer unavailable');
  const session = await response.json() as { accessToken: string; claims: { roles: string[]; name: string; preferred_username: string } };
  const auth = {
    account: signal({ name: session.claims.name }), error: signal(null), enProceso: signal(false), infoToken: signal(null),
    init: () => undefined, hasSession: () => true, isLoggedIn: true,
    roles: session.claims.roles, name: session.claims.name, username: session.claims.preferred_username,
    email: session.claims.preferred_username,
    cargarRoles: async () => {},
    hasRole: (...required: string[]) => tieneAlgunRol(session.claims.roles, required),
    login: () => undefined, logout: () => { window.location.href = '/'; }, inspeccionarToken: async () => undefined,
  };
  await bootstrapApplication(AppComponent, { providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([(request, next) => next(request.clone({ setHeaders: { Authorization: `Bearer ${session.accessToken}` } })), errorInterceptor])),
    { provide: AuthService, useValue: auth },
    // No redirect callback runs in this fixture: keep requested deep links intact.
    { provide: MsalBroadcastService, useValue: { inProgress$: EMPTY } },
  ] });
}
void main();
