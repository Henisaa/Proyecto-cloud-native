import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService, Envio } from '../../core/bff.service';
import { FadeInDirective } from '../../shared/fade-in.directive';
import { pillEstado, puntoEstado } from '../../shared/estado-ui';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, FadeInDirective],
  template: `
    <div class="space-y-6" appFadeIn appFadeInStagger=".metric-card">
      <!-- HEADER -->
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Panel de operaciones</h1>
          <p class="mt-1 text-xs text-zinc-500 sm:text-sm">
            Bienvenido, {{ auth.name }} · monitoreo de despacho, latencia de microservicios y flota activa.
          </p>
        </div>
        <div class="flex items-center gap-2.5">
          <a routerLink="/reports" class="btn-secondary">Ver KPIs</a>
          <a routerLink="/shipments" class="btn-primary">+ Gestionar envíos</a>
        </div>
      </header>

      <!-- KPIs -->
      <section class="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Envíos totales</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-zinc-900">{{ envios.length }}</p>
          <p class="mt-1 text-[11px] text-zinc-400">Órdenes procesadas hoy</p>
        </div>

        <div class="metric-card rounded-2xl border border-blue-100 bg-white p-5 shadow-2xs transition-all hover:border-blue-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">En ruta</span>
            <span class="relative flex h-2 w-2">
              <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-blue-400 opacity-75"></span>
              <span class="relative inline-flex h-2 w-2 rounded-full bg-blue-600"></span>
            </span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-blue-600">{{ contar('EN_RUTA') }}</p>
          <p class="mt-1 text-[11px] font-medium text-blue-500">
            {{ contar('EN_RUTA') }} vehículo(s) en tránsito
          </p>
        </div>

        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">En bodega</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-zinc-900">{{ contar('EN_BODEGA') }}</p>
          <p class="mt-1 text-[11px] text-zinc-400">
            {{ contar('EN_BODEGA') === 0 ? 'Almacén despejado' : 'En preparación de picking' }}
          </p>
        </div>

        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Lead time prom.</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-zinc-900">
            {{ kpis?.['avgLeadTimeMinutes'] ?? '—' }}
            <span class="font-sans text-xs font-medium text-zinc-400">min</span>
          </p>
          <p class="mt-1 text-[11px] font-medium text-emerald-600">Calculado desde Kafka (report)</p>
        </div>
      </section>

      <!-- PANELES -->
      <div class="grid gap-6 lg:grid-cols-2">
        <!-- BFF -->
        <div class="flex flex-col justify-between rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs sm:p-6">
          <div>
            <div class="flex items-center justify-between border-b border-zinc-100 pb-4">
              <h2 class="text-xs font-semibold uppercase tracking-wider text-zinc-500">
                Infraestructura · Backend (BFF)
              </h2>
              @if (status) {
                <span class="badge border-emerald-200/70 bg-emerald-50 text-emerald-700">
                  <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
                  {{ status['status'] }}
                </span>
              } @else {
                <span class="badge border-zinc-200 bg-zinc-100 text-zinc-500">sin datos</span>
              }
            </div>

            <div class="mt-4 divide-y divide-zinc-100 text-xs">
              <div class="flex items-center justify-between py-2.5">
                <span class="text-zinc-500">Gateway principal</span>
                <span class="rounded bg-zinc-100 px-2 py-0.5 font-mono font-medium text-zinc-900">
                  {{ status?.['service'] ?? 'ms-rutaexpress-bff' }}
                </span>
              </div>
              @for (entry of downstream(); track entry[0]) {
                <div class="flex items-center justify-between py-2.5">
                  <span class="font-medium text-zinc-700">{{ entry[0] }}</span>
                  <span class="font-mono text-[11px] text-zinc-400 transition hover:text-zinc-700">
                    {{ entry[1] }}
                  </span>
                </div>
              }
            </div>
          </div>

          <div class="mt-4 flex items-center justify-between border-t border-zinc-100 pt-3 text-[11px] text-zinc-400">
            <span>Cluster: Docker Internal (EC2)</span>
            <span class="font-mono">Azure AD: {{ status?.['azureIdaasConfigured'] ? 'OK' : '—' }}</span>
          </div>
        </div>

        <!-- ÚLTIMOS ENVÍOS -->
        <div class="flex flex-col justify-between rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs sm:p-6">
          <div>
            <div class="flex items-center justify-between border-b border-zinc-100 pb-4">
              <h2 class="text-xs font-semibold uppercase tracking-wider text-zinc-500">Últimos despachos</h2>
              <a routerLink="/shipments" class="text-xs font-medium text-blue-600 transition hover:text-blue-700">
                Ver historial →
              </a>
            </div>

            @if (envios.length === 0) {
              <p class="mt-4 text-xs text-zinc-400">Aún no hay envíos registrados.</p>
            } @else {
              <ul class="mt-3 divide-y divide-zinc-100 text-xs">
                @for (envio of envios.slice(0, 5); track envio.id) {
                  <li class="-mx-2 flex items-center justify-between rounded-lg px-2 py-3 transition-colors hover:bg-zinc-50/70">
                    <div class="min-w-0 space-y-0.5 pr-3">
                      <p class="font-mono font-semibold" [class]="envio.status === 'EN_RUTA' ? 'text-blue-600' : 'text-zinc-900'">
                        {{ envio.trackingCode }}
                      </p>
                      <p class="truncate text-[11px] text-zinc-500">{{ envio.destinationAddress }}</p>
                    </div>
                    <span class="badge shrink-0" [class]="pill(envio.status)">
                      <span class="h-1.5 w-1.5 rounded-full" [class]="punto(envio.status)"></span>
                      {{ envio.status }}
                    </span>
                  </li>
                }
              </ul>
            }
          </div>

          <div class="mt-4 border-t border-zinc-100 pt-3 text-right">
            <span class="text-[11px] text-zinc-400">Datos en vivo desde el BFF</span>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class DashboardPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly bff = inject(BffService);

  status: Record<string, any> | null = null;
  kpis: Record<string, any> | null = null;
  envios: Envio[] = [];

  ngOnInit(): void {
    forkJoin({
      status: this.bff.status().pipe(catchError(() => of(null))),
      kpis: this.bff.reportKpis().pipe(catchError(() => of(null))),
      envios: this.bff.listarEnvios().pipe(catchError(() => of([] as Envio[]))),
    }).subscribe(({ status, kpis, envios }) => {
      this.status = status as Record<string, any> | null;
      this.kpis = kpis as Record<string, any> | null;
      this.envios = envios as Envio[];
    });
  }

  contar(status: string): number {
    return this.envios.filter((e) => e.status === status).length;
  }

  downstream(): [string, string][] {
    const services = (this.status?.['downstreamServices'] ?? {}) as Record<string, string>;
    return Object.entries(services);
  }

  pill(status: string): string {
    return pillEstado(status);
  }

  punto(status: string): string {
    return puntoEstado(status);
  }
}
