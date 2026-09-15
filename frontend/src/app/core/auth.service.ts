import { Injectable, inject, signal } from '@angular/core';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { AccountInfo, EventMessage, EventType, InteractionStatus } from '@azure/msal-browser';
import { filter, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { loginRequest } from './msal.config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly msal = inject(MsalService);
  private readonly broadcast = inject(MsalBroadcastService);

  readonly account = signal<AccountInfo | null>(null);
  readonly error = signal<string | null>(null);
  readonly enProceso = signal<boolean>(false);
  readonly tokenAud = signal<string | null>(null);
  readonly tokenIss = signal<string | null>(null);

  init(): void {
    if (environment.demo) {
      return;
    }

    this.msal
      .handleRedirectObservable()
      .subscribe({
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
      void this.inspeccionarToken(account);
    }
    this.account.set(account);
  }

  /** Decodifica el access token para mostrar aud/iss (diagnóstico de 401 en el backend). */
  private async inspeccionarToken(account: AccountInfo): Promise<void> {
    try {
      const resultado = await firstValueFrom(
        this.msal.acquireTokenSilent({
          scopes: ['openid', 'profile', 'email', environment.msal.scope],
          account,
        })
      );
      const base64 = resultado.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(base64)) as Record<string, unknown>;
      this.tokenAud.set(String(payload['aud'] ?? '—'));
      this.tokenIss.set(String(payload['iss'] ?? '—'));
    } catch (err) {
      console.warn('No se pudo inspeccionar el access token', err);
    }
  }

  /** Describe el error de MSAL de forma accionable (para mostrarlo en pantalla). */
  private describirError(err: unknown): string {
    const e = err as { errorCode?: string; errorMessage?: string; message?: string; subError?: string };
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
    if (environment.demo) {
      return;
    }
    this.error.set(null);
    this.msal.loginRedirect(loginRequest);
  }

  logout(): void {
    if (environment.demo) {
      this.account.set(null);
      return;
    }
    const account = this.account() ?? undefined;
    this.msal.logoutRedirect({ account, postLogoutRedirectUri: window.location.origin });
  }

  get isLoggedIn(): boolean {
    return this.account() !== null;
  }

  /** Lectura sincrónica del caché de MSAL (útil en guards antes de que el signal se hidrate). */
  hasSession(): boolean {
    if (environment.demo) {
      return true;
    }
    return this.msal.instance.getAllAccounts().length > 0;
  }

  get name(): string {
    const account = this.account();
    return account?.name ?? account?.username ?? 'Usuario';
  }

  get username(): string {
    return this.account()?.username ?? '';
  }

  get email(): string {
    const claims = (this.account()?.idTokenClaims ?? {}) as Record<string, unknown>;
    const claimEmail = claims['email'] ?? claims['preferred_username'];
    return typeof claimEmail === 'string' ? claimEmail : this.username;
  }

  get roles(): string[] {
    const claims = (this.account()?.idTokenClaims ?? {}) as Record<string, unknown>;
    const roles = claims['roles'];
    return Array.isArray(roles) ? (roles as string[]) : [];
  }

  get scopes(): string[] {
    const claims = (this.account()?.idTokenClaims ?? {}) as Record<string, unknown>;
    const scp = claims['scp'] ?? claims['scope'];
    if (typeof scp === 'string') return scp.split(' ').filter((s) => s.length > 0);
    return Array.isArray(scp) ? (scp as string[]) : [];
  }

  hasRole(...roles: string[]): boolean {
    const own = this.roles.map((r) => r.toLowerCase());
    return roles.some((r) => own.includes(r.toLowerCase()));
  }
}
