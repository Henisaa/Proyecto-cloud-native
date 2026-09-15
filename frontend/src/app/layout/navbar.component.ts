import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-40 border-b border-zinc-200/80 bg-white/85 backdrop-blur-md">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div class="flex items-center gap-3">
          <a routerLink="/dashboard" class="group flex items-center gap-2">
            <img
              src="logo.webp"
              alt="RutaExpress"
              class="h-8 w-8 object-contain transition-transform duration-200 group-hover:scale-105"
            />
            <span class="text-base font-bold tracking-tight text-zinc-900">
              Ruta<span class="text-blue-600">Express</span>
            </span>
          </a>
          @if (demo) {
            <span
              class="rounded-full border border-amber-200 bg-amber-50/80 px-2 py-0.5 text-[10px] font-semibold tracking-wider text-amber-700"
            >
              DEMO
            </span>
          }
        </div>

        @if (auth.isLoggedIn) {
          <nav class="order-3 flex w-full items-center gap-1 overflow-x-auto md:order-none md:w-auto">
            @for (item of menu; track item.path) {
              <a
                [routerLink]="item.path"
                routerLinkActive
                #rla="routerLinkActive"
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
                  <span class="rounded border border-zinc-200 bg-zinc-100 px-1.5 py-0.5 text-[9px] font-medium text-zinc-600">
                    {{ role }}
                  </span>
                }
                @if (auth.roles.length === 0) {
                  <span class="rounded border border-zinc-200 bg-zinc-100 px-1.5 py-0.5 text-[9px] font-medium text-zinc-500">
                    sin rol
                  </span>
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
  readonly demo = environment.demo;

  readonly menu = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/shipments', label: 'Envíos' },
    { path: '/catalog', label: 'Catálogo' },
    { path: '/reports', label: 'Reportes' },
    { path: '/audit', label: 'Auditoría' },
  ];
}
