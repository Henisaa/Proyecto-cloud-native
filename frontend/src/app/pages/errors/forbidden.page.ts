import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

/** 403: el usuario inició sesión pero su rol no permite esta pantalla (o no tiene ningún rol asignado). */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="mx-auto mt-16 max-w-md rounded-2xl border border-zinc-200 bg-white p-8 text-center shadow-2xs">
      <p class="font-mono text-4xl font-bold text-zinc-300">403</p>
      <h1 class="mt-3 text-lg font-bold text-zinc-900">Sin permisos</h1>
      <p class="mt-2 text-sm text-zinc-500">
        @if (auth.roles.length === 0) {
          Su usuario no tiene ningún rol asignado en RutaExpress. Pida al administrador que le asigne uno.
        } @else {
          Su rol ({{ auth.roles.join(', ') }}) no permite acceder a esta pantalla.
        }
      </p>
      <div class="mt-6 flex justify-center gap-2">
        @if (auth.roles.length > 0) {
          <a routerLink="/dashboard" class="btn-primary">Ir al panel</a>
        }
        <button type="button" class="btn-secondary" (click)="auth.logout()">Cambiar de usuario</button>
      </div>
    </section>
  `,
})
export class ForbiddenPage {
  readonly auth = inject(AuthService);
}
