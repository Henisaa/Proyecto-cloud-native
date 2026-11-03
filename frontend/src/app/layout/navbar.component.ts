import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { runtimeConfig } from '../core/app-config';
import { esSoloCliente, tieneAlgunRol } from '../core/roles';
import { routes } from '../app.routes';

interface ItemMenu {
  path: string;
  label: string;
}

/** El menú se arma a partir de los roles declarados en cada ruta (data.roles y data.menu). */
export function menuParaRoles(roles: readonly string[]): ItemMenu[] {
  return routes
    .filter((ruta) => typeof ruta.data?.['menu'] === 'string')
    .filter((ruta) => tieneAlgunRol(roles, (ruta.data?.['roles'] as string[] | undefined) ?? []))
    .map((ruta) => ({ path: `/${ruta.path}`, label: ruta.data?.['menu'] as string }));
}

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-40 border-b border-zinc-200/80 bg-white/85 backdrop-blur-md">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div class="flex items-center gap-3">
          <a routerLink="/dashboard" class="group flex items-center gap-2">
            <img src="logo.webp" alt="RutaExpress" class="h-8 w-8 object-contain transition-transform duration-200 group-hover:scale-105" />
            <span class="text-base font-bold tracking-tight text-zinc-900">Ruta<span class="text-blue-600">Express</span></span>
          </a>
          @if (!soloCliente()) {
            <span
              class="rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider"
              [class]="esProduccion ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50/80 text-amber-700'"
              data-testid="entorno"
            >
              {{ entorno }}
            </span>
          }
        </div>

        <button
          type="button"
          class="btn-secondary md:hidden"
          [attr.aria-expanded]="abierto()"
          aria-controls="menu-principal"
          (click)="abierto.set(!abierto())"
        >
          Menú
        </button>

        @if (auth.isLoggedIn) {
          <nav
            id="menu-principal"
            aria-label="Principal"
            class="order-3 w-full flex-col gap-1 md:order-none md:flex md:w-auto md:flex-row md:items-center"
            [class]="abierto() ? 'flex' : 'hidden'"
          >
            @for (item of menu(); track item.path) {
              <a
                [routerLink]="item.path"
                routerLinkActive
                #rla="routerLinkActive"
                (click)="abierto.set(false)"
                class="whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium transition"
                [class]="rla.isActive ? 'bg-zinc-900 text-white shadow-xs' : 'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'"
              >
                {{ item.label }}
              </a>
            }
          </nav>
        }

        <div class="flex items-center gap-3">
          @if (auth.isLoggedIn) {
            <div class="hidden text-right leading-tight lg:block">
              <p class="text-xs font-semibold text-zinc-800">{{ auth.name }}</p>
              <div class="mt-0.5 flex justify-end gap-1">
                @for (role of auth.roles; track role) {
                  <span class="rounded border border-zinc-200 bg-zinc-100 px-1.5 py-0.5 text-[9px] font-medium text-zinc-600">{{ role }}</span>
                }
                @if (auth.roles.length === 0) {
                  <span class="rounded border border-zinc-200 bg-zinc-100 px-1.5 py-0.5 text-[9px] font-medium text-zinc-500">sin rol</span>
                }
              </div>
            </div>
            <button type="button" class="btn-secondary" (click)="auth.logout()">Salir</button>
          } @else {
            <button type="button" class="btn-primary" (click)="auth.login()">Iniciar sesión</button>
          }
        </div>
      </div>
    </header>
  `,
})
export class NavbarComponent {
  readonly auth = inject(AuthService);
  readonly abierto = signal(false);
  readonly entorno = runtimeConfig.environmentName;
  readonly esProduccion = /prod/i.test(runtimeConfig.environmentName);
  readonly soloCliente = computed(() => esSoloCliente(this.auth.roles));

  readonly menu = computed(() => {
    // Depende de la cuenta (signal) para recalcularse al iniciar o cerrar sesión.
    this.auth.account();
    return menuParaRoles(this.auth.roles);
  });
}
