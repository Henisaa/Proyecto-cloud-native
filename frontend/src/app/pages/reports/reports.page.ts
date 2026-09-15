import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { FadeInDirective } from '../../shared/fade-in.directive';

const COLORES_ESTADO: Record<string, string> = {
  ENTREGADO: 'bg-emerald-500',
  EN_RUTA: 'bg-blue-500',
  EN_BODEGA: 'bg-amber-500',
  ACEPTADO: 'bg-indigo-500',
  CANCELADO: 'bg-rose-500',
  CREADO: 'bg-zinc-300',
};

const PILL_ESTADO: Record<string, string> = {
  ENTREGADO: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  EN_RUTA: 'border-blue-200 bg-blue-50 text-blue-700',
  EN_BODEGA: 'border-amber-200 bg-amber-50 text-amber-700',
  ACEPTADO: 'border-indigo-200 bg-indigo-50 text-indigo-700',
  CANCELADO: 'border-rose-200 bg-rose-50 text-rose-700',
  CREADO: 'border-zinc-200 bg-white text-zinc-600',
};

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [FormsModule, FadeInDirective],
  template: `
    <div class="space-y-6" appFadeIn appFadeInStagger=".kpi-card">
      <!-- HEADER -->
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Reportería y KPIs</h1>
            <span class="badge border-emerald-200/60 bg-emerald-50 text-emerald-700">
              <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500"></span>
              Kafka Live
            </span>
          </div>
          <p class="mt-1 text-xs text-zinc-500 sm:text-sm">
            Métricas de eventos agregadas en tiempo real por
            <span class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-[11px] text-zinc-700">ms-rutaexpress-report</span>.
          </p>
        </div>

        <div class="flex items-center gap-2">
          <div class="relative">
            <select
              class="cursor-pointer appearance-none rounded-full border border-zinc-300 bg-white py-2 pl-4 pr-9 text-xs font-medium text-zinc-700 shadow-2xs transition-colors hover:border-zinc-400 focus:border-zinc-900 focus:outline-none"
              [(ngModel)]="rango"
              (ngModelChange)="cargar()"
            >
              <option value="last1h">Última hora</option>
              <option value="last24h">Últimas 24 horas</option>
              <option value="last7d">Últimos 7 días</option>
              <option value="last30d">Últimos 30 días</option>
            </select>
            <div class="pointer-events-none absolute inset-y-0 right-3 flex items-center text-zinc-400">
              <svg class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
              </svg>
            </div>
          </div>

          <button
            type="button"
            class="cursor-pointer rounded-full border border-zinc-200 bg-white p-2 text-zinc-600 shadow-2xs transition-all hover:border-zinc-900 hover:text-zinc-900 active:scale-95"
            title="Actualizar datos"
            (click)="cargar()"
          >
            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
            </svg>
          </button>
        </div>
      </header>

      @if (error) {
        <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-medium text-red-700">{{ error }}</div>
      }

      <!-- KPI GRID -->
      <section class="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
        <div class="kpi-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Total envíos</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-zinc-900">{{ kpis?.['totalShipments'] ?? '—' }}</p>
          <p class="mt-1 text-[11px] text-zinc-400">En la ventana seleccionada</p>
        </div>

        <div class="kpi-card rounded-2xl border border-blue-100 bg-white p-5 shadow-2xs transition-all hover:border-blue-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Envíos por hora</span>
            <span class="h-2 w-2 rounded-full bg-blue-500"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-blue-600">{{ kpis?.['shipmentsPerHour'] ?? '—' }}</p>
          <p class="mt-1 text-[11px] font-medium text-blue-500">Throughput promedio</p>
        </div>

        <div class="kpi-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Lead time promedio</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-3xl font-bold tracking-tight text-zinc-900">
            {{ kpis?.['avgLeadTimeMinutes'] ?? '—' }}
            <span class="font-sans text-xs font-medium text-zinc-400">min</span>
          </p>
          <p class="mt-1 text-[11px] font-medium text-emerald-600">≈ {{ horas() }} horas por ciclo</p>
        </div>

        <div class="kpi-card rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs transition-all hover:border-zinc-300 hover:shadow-xs">
          <div class="flex items-center justify-between">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Rango consultado</span>
            <span class="h-2 w-2 rounded-full bg-zinc-200"></span>
          </div>
          <p class="mt-3 font-mono text-xl font-semibold tracking-tight text-zinc-700">{{ kpis?.['range'] ?? rango }}</p>
          <p class="mt-2 font-mono text-[11px] text-zinc-400">Ventana deslizante</p>
        </div>
      </section>

      <!-- ESTADOS -->
      <section class="rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs sm:p-6">
        <div class="flex flex-col justify-between gap-2 border-b border-zinc-100 pb-5 sm:flex-row sm:items-center">
          <div>
            <h2 class="text-xs font-semibold uppercase tracking-wider text-zinc-500">Distribución de estados activos</h2>
            <p class="mt-0.5 text-xs text-zinc-400">Desglose de la máquina de estados actual</p>
          </div>
          <span class="font-mono text-[11px] text-zinc-400">{{ totalEstados() }} órdenes en seguimiento</span>
        </div>

        @if (estados().length === 0) {
          <p class="mt-4 text-xs text-zinc-400">Sin datos de estados en esta ventana.</p>
        } @else {
          <div class="mt-6 grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
            @for (entrada of estados(); track entrada[0]) {
              <div class="rounded-xl border p-4 transition-all" [class]="bordeEstado(entrada[0])">
                <div class="flex items-center justify-between">
                  <span class="badge" [class]="pillEstado(entrada[0])">
                    <span class="h-1.5 w-1.5 rounded-full" [class]="colorEstado(entrada[0])"></span>
                    {{ entrada[0] }}
                  </span>
                  <span class="font-mono text-[10px] text-zinc-500">{{ porcentaje(entrada[1]) }}%</span>
                </div>
                <p class="mt-3 font-mono text-2xl font-bold tracking-tight text-zinc-900">{{ entrada[1] }}</p>
                <p class="mt-1 text-[11px] text-zinc-500">{{ descripcionEstado(entrada[0]) }}</p>
              </div>
            }
          </div>

          <div class="mt-6 border-t border-zinc-100 pt-5">
            <div class="mb-2 flex items-center justify-between text-[11px] text-zinc-400">
              <span>Distribución proporcional de flota</span>
              <span class="font-mono">100% de cobertura</span>
            </div>
            <div class="flex h-2 w-full overflow-hidden rounded-full bg-zinc-100">
              @for (entrada of estados(); track entrada[0]) {
                <div
                  class="h-full"
                  [class]="colorEstado(entrada[0])"
                  [style.width.%]="porcentaje(entrada[1])"
                  [title]="entrada[0] + ': ' + porcentaje(entrada[1]) + '%'"
                ></div>
              }
            </div>
          </div>
        }
      </section>
    </div>
  `,
})
export class ReportsPage implements OnInit {
  private readonly bff = inject(BffService);

  rango = 'last24h';
  kpis: Record<string, any> | null = null;
  error = '';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.error = '';
    this.bff.reportKpis(this.rango).subscribe({
      next: (kpis) => (this.kpis = kpis),
      error: (err) => {
        const e = err as { status?: number };
        this.error =
          e?.status === 401 || e?.status === 403
            ? 'El reporte requiere token con rol Admin.'
            : 'No se pudieron cargar los KPIs.';
      },
    });
  }

  estados(): [string, number][] {
    const value = (this.kpis?.['activeShipmentsByStatus'] ?? {}) as Record<string, number>;
    return Object.entries(value);
  }

  totalEstados(): number {
    return this.estados().reduce((sum, [, cantidad]) => sum + cantidad, 0);
  }

  porcentaje(cantidad: number): number {
    const total = this.totalEstados();
    return total === 0 ? 0 : Math.round((cantidad / total) * 100);
  }

  horas(): string {
    const promedio = Number(this.kpis?.['avgLeadTimeMinutes'] ?? 0);
    return Number.isFinite(promedio) ? (promedio / 60).toFixed(1) : '—';
  }

  colorEstado(estado: string): string {
    return COLORES_ESTADO[estado] ?? 'bg-zinc-300';
  }

  pillEstado(estado: string): string {
    return PILL_ESTADO[estado] ?? 'border-zinc-200 bg-zinc-100 text-zinc-600';
  }

  bordeEstado(estado: string): string {
    switch (estado) {
      case 'ENTREGADO':
        return 'border-emerald-100 bg-emerald-50/30 hover:border-emerald-200';
      case 'EN_RUTA':
        return 'border-blue-100 bg-blue-50/30 hover:border-blue-200';
      case 'EN_BODEGA':
        return 'border-amber-100 bg-amber-50/30 hover:border-amber-200';
      case 'ACEPTADO':
        return 'border-indigo-100 bg-indigo-50/30 hover:border-indigo-200';
      case 'CANCELADO':
        return 'border-rose-100 bg-rose-50/30 hover:border-rose-200';
      default:
        return 'border-zinc-200 bg-zinc-50/50 hover:border-zinc-300';
    }
  }

  descripcionEstado(estado: string): string {
    switch (estado) {
      case 'ENTREGADO':
        return 'Entrega completada';
      case 'EN_RUTA':
        return 'En tránsito con chofer';
      case 'EN_BODEGA':
        return 'Preparación de picking';
      case 'ACEPTADO':
        return 'Validado en sistema';
      case 'CANCELADO':
        return 'Orden anulada';
      default:
        return 'Pendiente de asignación';
    }
  }
}
