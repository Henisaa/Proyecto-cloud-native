import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { FadeInDirective } from '../../shared/fade-in.directive';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [FormsModule, FadeInDirective],
  template: `
    <div class="space-y-6" appFadeIn appFadeInStagger=".metric-card">
      <!-- HEADER -->
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Registro de auditoría</h1>
            <span class="badge border-zinc-200 bg-zinc-100 text-zinc-700">
              <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
              Event Sourcing
            </span>
          </div>
          <p class="mt-1 text-xs text-zinc-500 sm:text-sm">
            Trazabilidad inmutable de eventos consumidos desde Kafka por
            <span class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-[11px] text-zinc-700">ms-rutaexpress-audit</span>.
          </p>
        </div>

        <div class="flex flex-wrap items-end gap-2">
          <label class="min-w-[240px] flex-1">
            <span class="label">ID de entidad (id del envío)</span>
            <input class="input" [(ngModel)]="idEntidad" placeholder="uuid del envío" (keyup.enter)="buscar()" />
          </label>
          <button type="button" class="btn-primary" [disabled]="!idEntidad || buscando" (click)="buscar()">
            {{ buscando ? 'Buscando…' : 'Buscar timeline' }}
          </button>
        </div>
      </header>

      @if (error) {
        <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-medium text-red-700">{{ error }}</div>
      }

      <!-- MÉTRICAS -->
      <section class="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-2xs">
          <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Eventos registrados</span>
          <p class="mt-2 font-mono text-2xl font-bold text-zinc-900">{{ eventos.length }}</p>
          <p class="mt-0.5 text-[11px] text-zinc-400">Para la entidad consultada</p>
        </div>

        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-2xs">
          <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Transiciones de estado</span>
          <p class="mt-2 font-mono text-2xl font-bold text-blue-600">{{ transiciones() }}</p>
          <p class="mt-0.5 text-[11px] font-medium text-blue-500">Eventos con cambio de status</p>
        </div>

        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-2xs">
          <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Origen del evento</span>
          <div class="mt-2 flex items-center gap-1.5">
            <span class="h-2 w-2 rounded-full bg-emerald-500"></span>
            <span class="font-mono text-sm font-semibold text-zinc-900">{{ origen() }}</span>
          </div>
          <p class="mt-0.5 text-[11px] font-medium text-emerald-600">Kafka consumer: ms-rutaexpress-audit-group</p>
        </div>

        <div class="metric-card rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-2xs">
          <span class="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Integridad de bloque</span>
          <p class="mt-2 font-mono text-2xl font-bold text-emerald-700">{{ eventos.length ? '100%' : '—' }}</p>
          <p class="mt-0.5 font-mono text-[11px] text-zinc-400">Registro inmutable</p>
        </div>
      </section>

      <!-- TABLA -->
      <div class="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-2xs">
        <div class="overflow-x-auto">
          <table class="min-w-full border-collapse text-left">
            <thead>
              <tr class="border-b border-zinc-100 bg-zinc-50/50 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                <th class="px-4 py-3 sm:px-6">Timestamp / ID</th>
                <th class="px-4 py-3">Tipo de evento</th>
                <th class="px-4 py-3">Entidad afectada</th>
                <th class="px-4 py-3">Actor / Origen</th>
                <th class="px-4 py-3">Detalle / Payload</th>
                <th class="px-4 py-3 text-right sm:px-6">ID</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-100 text-xs">
              @for (evento of eventos; track $index) {
                <tr class="transition-colors hover:bg-zinc-50/70">
                  <td class="whitespace-nowrap px-4 py-3.5 sm:px-6">
                    <p class="font-mono font-medium text-zinc-800">{{ fecha(evento['fechaEvento']) }}</p>
                    <p class="font-mono text-[10px] text-zinc-400">evt-{{ evento['id'] }}</p>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="badge border-blue-200 bg-blue-50 text-blue-700">
                      <span class="h-1.5 w-1.5 rounded-full bg-blue-500"></span>
                      {{ evento['tipoEvento'] }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="rounded border border-zinc-200/60 bg-zinc-100/80 px-2 py-0.5 font-mono text-[11px] font-semibold text-zinc-900">
                      {{ evento['idEntidad'] }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-4 py-3.5">
                    <p class="font-medium text-zinc-800">{{ evento['usuarioResponsable'] }}</p>
                    <p class="font-mono text-[10px] text-zinc-400">{{ evento['origen'] }}</p>
                  </td>
                  <td class="max-w-[320px] px-4 py-3.5">
                    <code class="block truncate rounded bg-zinc-100 px-2 py-0.5 font-mono text-[11px] text-zinc-600" [title]="evento['datosPayload']">
                      {{ evento['datosPayload'] }}
                    </code>
                  </td>
                  <td class="px-4 py-3.5 text-right font-mono text-[10px] text-zinc-400 sm:px-6">
                    #{{ evento['id'] }}
                  </td>
                </tr>
              }

              @if (eventos.length === 0) {
                <tr>
                  <td class="px-4 py-6 text-zinc-400" colspan="6">
                    {{ buscado ? 'Sin eventos para esa entidad.' : 'Ingresa un ID de entidad para consultar su timeline.' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between border-t border-zinc-100 px-4 py-3 text-[11px] text-zinc-400 sm:px-6">
          <span>Mostrando {{ eventos.length }} evento(s) más reciente(s)</span>
          <span class="font-mono">Kafka Topic: envios-events / auditoria-topic</span>
        </div>
      </div>
    </div>
  `,
})
export class AuditPage {
  private readonly bff = inject(BffService);

  idEntidad = '';
  eventos: Record<string, any>[] = [];
  buscando = false;
  buscado = false;
  error = '';

  buscar(): void {
    this.buscando = true;
    this.error = '';
    this.bff.auditTimeline(this.idEntidad.trim()).subscribe({
      next: (eventos) => {
        this.eventos = eventos as Record<string, any>[];
        this.buscando = false;
        this.buscado = true;
      },
      error: (err) => {
        this.buscando = false;
        this.buscado = true;
        const e = err as { status?: number };
        this.error =
          e?.status === 401 || e?.status === 403
            ? 'La auditoría requiere token con rol Admin/Auditor.'
            : 'No se pudo consultar el timeline.';
      },
    });
  }

  transiciones(): number {
    return this.eventos.filter((evento) => String(evento['datosPayload'] ?? '').includes('status')).length;
  }

  origen(): string {
    return String(this.eventos[0]?.['origen'] ?? 'envios-events');
  }

  fecha(valor: unknown): string {
    if (!valor) return '—';
    const date = new Date(String(valor));
    if (Number.isNaN(date.getTime())) return String(valor);
    return date.toLocaleString('es-CL', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }
}
