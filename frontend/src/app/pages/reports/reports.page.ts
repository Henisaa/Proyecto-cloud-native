import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { Kpis, TopServicio } from '../../core/models';
import { pillEstado } from '../../shared/estado-ui';
import { decimal, numero } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/** Rangos fijos que acepta el servicio de reportes; cualquier otro valor responde 400. */
export const RANGOS = [
  { valor: 'last1h', etiqueta: 'Última hora' },
  { valor: 'last24h', etiqueta: 'Últimas 24 horas' },
  { valor: 'last7d', etiqueta: 'Últimos 7 días' },
  { valor: 'last30d', etiqueta: 'Últimos 30 días' },
] as const;

/** Reportes (FE-09, solo Admin): KPIs de la operación y servicios más usados. */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Reportes</h1>
          <p class="mt-1 text-xs text-zinc-500">Indicadores operacionales calculados por el servicio de reportes (EP1).</p>
        </div>
        <div>
          <label class="label" for="rango">Rango</label>
          <select id="rango" class="input" [ngModel]="rango()" (ngModelChange)="rango.set($event); cargar()">
            @for (r of rangos; track r.valor) { <option [value]="r.valor">{{ r.etiqueta }}</option> }
          </select>
        </div>
      </header>

      <app-vista-estado [cargando]="cargando()" [error]="error()" (reintentar)="cargar()">
        @if (kpis(); as k) {
          <div class="grid gap-3 sm:grid-cols-3">
            <div class="card"><p class="label">Envíos totales</p><p class="font-mono text-3xl font-bold">{{ numero(k.totalShipments) }}</p></div>
            <div class="card"><p class="label">Envíos por hora</p><p class="font-mono text-3xl font-bold">{{ decimal(k.shipmentsPerHour) }}</p></div>
            <div class="card"><p class="label">Tiempo promedio de entrega</p><p class="font-mono text-3xl font-bold">{{ decimal(k.avgLeadTimeMinutes) }} <span class="text-sm">min</span></p></div>
          </div>

          <div class="card mt-3">
            <h2 class="text-sm font-bold text-zinc-800">Envíos por estado</h2>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (par of estados(); track par[0]) {
                <span class="badge" [class]="pillEstado(par[0])">{{ par[0] }} · {{ numero(par[1]) }}</span>
              } @empty {
                <span class="text-xs text-zinc-400">Sin envíos en el rango.</span>
              }
            </div>
          </div>

          <div class="card mt-3 overflow-x-auto">
            <h2 class="text-sm font-bold text-zinc-800">Servicios más usados</h2>
            <table class="mt-3 w-full min-w-[420px] text-left text-xs">
              <caption class="sr-only">Servicios más usados</caption>
              <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                <tr><th scope="col" class="px-3 py-2">Servicio</th><th scope="col" class="px-3 py-2">Envíos</th><th scope="col" class="px-3 py-2">Participación</th></tr>
              </thead>
              <tbody>
                @for (s of top(); track s.serviceType) {
                  <tr class="border-b border-zinc-50 last:border-0">
                    <td class="px-3 py-2">{{ s.serviceType }}</td>
                    <td class="px-3 py-2">{{ numero(s.totalShipments) }}</td>
                    <td class="px-3 py-2">{{ decimal(s.percentage) }} %</td>
                  </tr>
                } @empty {
                  <tr><td colspan="3" class="px-3 py-6 text-center text-zinc-400">Sin datos en el rango.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </app-vista-estado>
    </div>
  `,
})
export class ReportsPage implements OnInit {
  private readonly bff = inject(BffService);

  readonly rangos = RANGOS;
  readonly rango = signal<string>('last24h');
  readonly kpis = signal<Kpis | null>(null);
  readonly top = signal<TopServicio[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly pillEstado = pillEstado;
  readonly numero = numero;
  readonly decimal = decimal;

  ngOnInit(): void {
    this.cargar();
  }

  estados(): [string, number][] {
    return Object.entries(this.kpis()?.activeShipmentsByStatus ?? {});
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({ kpis: this.bff.kpis(this.rango()), top: this.bff.topServicios(this.rango()) }).subscribe({
      next: ({ kpis, top }) => {
        this.kpis.set(kpis);
        this.top.set(top);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }
}
