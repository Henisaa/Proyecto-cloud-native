import { Injectable, inject, signal } from '@angular/core';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { AccountInfo, EventMessage, EventType, InteractionStatus } from '@azure/msal-browser';
import { filter, firstValueFrom } from 'rxjs';
import { runtimeConfig } from './app-config';
import { crearLoginRequest } from './msal.config';
import { normalizarRol, tieneAlgunRol } from './roles';

/** Datos del access token para el diagnóstico de sesión (FE-18). Nunca incluye el token en sí. */
export interface InfoToken {
  audiencia: string;
  emisor: string;
  vence: string;
  roles: string[];
  scopes: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly msal = inject(MsalService);
  private readonly broadcast = inject(MsalBroadcastService);

  readonly account = signal<AccountInfo | null>(null);
  readonly error = signal<string | null>(null);
  readonly enProceso = signal<boolean>(false);
  readonly infoToken = signal<InfoToken | null>(null);

  init(): void {
    this.msal.handleRedirectObservable().subscribe({
      next: (resultado) => {
        if (resultado?.account) {
          this.msal.instance.setActiveAccount(resultado.account);
        }
        this.refresh();
      },
      error: (err) => {
        console.error('MSAL redirect error', err);
        this.error.set(this.describirError(err));
        this.enProceso.set(false);
      },
    });

    this.broadcast.msalSubject$.subscribe((event: EventMessage) => {
      switch (event.eventType) {
        case EventType.LOGIN_SUCCESS:
        case EventType.ACQUIRE_TOKEN_SUCCESS:
        case EventType.LOGOUT_SUCCESS:
          this.error.set(null);
          this.refresh();
          break;
        case EventType.ACQUIRE_TOKEN_FAILURE:
          this.error.set(this.describirError(event.payload));
          break;
        default:
          break;
      }
    });

    this.broadcast.inProgress$
      .pipe(filter((status: InteractionStatus) => status === InteractionStatus.None))
      .subscribe(() => {
        this.enProceso.set(false);
        this.refresh();
      });

    this.broadcast.inProgress$
      .pipe(filter((status: InteractionStatus) => status !== InteractionStatus.None))
      .subscribe(() => this.enProceso.set(true));
  }

  private refresh(): void {
    const active = this.msal.instance.getActiveAccount();
    const account = active ?? this.msal.instance.getAllAccounts()[0] ?? null;
    if (account) {
      this.msal.instance.setActiveAccount(account);
    }
    this.account.set(account);
  }

  /** Lee audiencia, emisor, vencimiento, roles y scopes del access token, sin exponer el token. */
  async inspeccionarToken(): Promise<void> {
    const cuenta = this.cuentaActual();
    if (!cuenta) return;
    try {
      const resultado = await firstValueFrom(
        this.msal.acquireTokenSilent({ scopes: ['openid', 'profile', 'email', runtimeConfig.scope], account: cuenta })
      );
      const carga = decodificarPayload(resultado.accessToken);
      const exp = typeof carga['exp'] === 'number' ? new Date((carga['exp'] as number) * 1000).toLocaleString('es-CL') : '—';
      const roles = Array.isArray(carga['roles']) ? (carga['roles'] as string[]) : [];
      const scp = typeof carga['scp'] === 'string' ? (carga['scp'] as string).split(' ').filter((s) => s.length > 0) : [];
      this.infoToken.set({
        audiencia: String(carga['aud'] ?? '—'),
        emisor: String(carga['iss'] ?? '—'),
        vence: exp,
        roles,
        scopes: scp,
      });
    } catch (err) {
      console.warn('No se pudo inspeccionar el access token', err);
    }
  }

  /** Describe el error de MSAL de forma accionable (para mostrarlo en pantalla). */
  private describirError(err: unknown): string {
    const e = err as { errorCode?: string; errorMessage?: string; message?: string };
    const codigo = e?.errorCode ?? '';
    const mensaje = e?.errorMessage ?? e?.message ?? 'Error desconocido al autenticar';
    const texto = String(mensaje);

    if (texto.toLowerCase().includes('cors')) {
      return 'Error de CORS en el endpoint de tokens: el redirect URI debe estar registrado en Azure como plataforma "Single-page application (SPA)".';
    }
    if (codigo === 'interaction_required' || texto.toLowerCase().includes('consent')) {
      return 'Microsoft pide consentimiento: revisa que los scopes/roles estén consentidos para tu usuario.';
    }
    if (codigo === 'user_cancelled') {
      return 'Inicio de sesión cancelado por el usuario.';
    }
    return codigo ? `${codigo}: ${texto}` : texto;
  }

  login(): void {
    this.error.set(null);
    void this.msal.loginRedirect(crearLoginRequest());
  }

  logout(): void {
    const account = this.account() ?? undefined;
    void this.msal.logoutRedirect({ account });
  }

  get isLoggedIn(): boolean {
    return this.account() !== null;
  }

  /**
   * Cuenta vigente: la del signal o, si aún no se hidrató (por ejemplo, al evaluar un guard tras recargar la página),
   * la que ya está en el caché de MSAL.
   */
  private cuentaActual(): AccountInfo | null {
    return this.account() ?? this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0] ?? null;
  }

  /** Lectura sincrónica del caché de MSAL (útil en guards antes de que el signal se hidrate). */
  hasSession(): boolean {
    return this.msal.instance.getAllAccounts().length > 0;
  }

  get name(): string {
    const account = this.cuentaActual();
    return account?.name ?? account?.username ?? 'Usuario';
  }

  get username(): string {
    return this.cuentaActual()?.username ?? '';
  }

  get email(): string {
    const claims = (this.cuentaActual()?.idTokenClaims ?? {}) as Record<string, unknown>;
    const claimEmail = claims['email'] ?? claims['preferred_username'];
    return typeof claimEmail === 'string' ? claimEmail : this.username;
  }

  /** Roles tal como vienen en el token, ya normalizados (misma regla que el backend). */
  get roles(): string[] {
    const claims = (this.cuentaActual()?.idTokenClaims ?? {}) as Record<string, unknown>;
    const roles = claims['roles'];
    return Array.isArray(roles) ? (roles as string[]).map(normalizarRol) : [];
  }

  hasRole(...requeridos: string[]): boolean {
    return tieneAlgunRol(this.roles, requeridos);
  }
}

function decodificarPayload(token: string): Record<string, unknown> {
  const base64 = (token.split('.')[1] ?? '').replace(/-/g, '+').replace(/_/g, '/');
  try {
    return JSON.parse(atob(base64)) as Record<string, unknown>;
  } catch {
    return {};
  }
}
