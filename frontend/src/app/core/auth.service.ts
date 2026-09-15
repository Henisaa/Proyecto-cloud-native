import { Injectable, inject, signal } from '@angular/core';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { AccountInfo, EventMessage, EventType, InteractionStatus } from '@azure/msal-browser';
import { filter } from 'rxjs';
import { loginRequest } from './msal.config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly msal = inject(MsalService);
  private readonly broadcast = inject(MsalBroadcastService);

  readonly account = signal<AccountInfo | null>(null);

  init(): void {
    this.msal.handleRedirectObservable().subscribe({
      next: () => this.refresh(),
      error: (err) => console.error('MSAL redirect error', err),
    });

    this.broadcast.msalSubject$
      .pipe(
        filter(
          (event: EventMessage) =>
            event.eventType === EventType.LOGIN_SUCCESS ||
            event.eventType === EventType.ACQUIRE_TOKEN_SUCCESS ||
            event.eventType === EventType.LOGOUT_SUCCESS
        )
      )
      .subscribe(() => this.refresh());

    this.broadcast.inProgress$
      .pipe(filter((status: InteractionStatus) => status === InteractionStatus.None))
      .subscribe(() => this.refresh());
  }

  private refresh(): void {
    const active = this.msal.instance.getActiveAccount();
    const account = active ?? this.msal.instance.getAllAccounts()[0] ?? null;
    if (account) {
      this.msal.instance.setActiveAccount(account);
    }
    this.account.set(account);
  }

  login(): void {
    this.msal.loginRedirect(loginRequest);
  }

  logout(): void {
    const account = this.account() ?? undefined;
    this.msal.logoutRedirect({ account, postLogoutRedirectUri: window.location.origin });
  }

  get isLoggedIn(): boolean {
    return this.account() !== null;
  }

  /** Lectura sincrónica del caché de MSAL (útil en guards antes de que el signal se hidrate). */
  hasSession(): boolean {
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
