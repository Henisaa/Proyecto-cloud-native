import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { FadeInDirective } from '../../shared/fade-in.directive';
import { pillServicio } from '../../shared/estado-ui';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [FadeInDirective],
  template: `
    <div class="space-y-6" appFadeIn appFadeInStagger=".catalog-row">
      <!-- HEADER -->
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Catálogo de servicios</h1>
          <p class="mt-1 text-xs text-zinc-500 sm:text-sm">
            Tipos de envío, tarifas base y SLA sincronizados con
            <span class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-[11px] text-zinc-700">ms-rutaexpress-catalog</span>
            vía BFF.
          </p>
        </div>

        <div class="flex items-center gap-2.5">
          <div class="inline-flex items-center gap-1.5 rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-xs text-zinc-600 shadow-2xs">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
            {{ servicios.length }} servicios registrados
          </div>
        </div>
      </header>

      @if (error) {
        <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-medium text-red-700">{{ error }}</div>
      }

      <!-- TABLA -->
      <div class="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-2xs">
        <div class="overflow-x-auto">
          <table class="min-w-full border-collapse text-left">
            <thead>
              <tr class="border-b border-zinc-100 bg-zinc-50/50 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                <th class="px-4 py-3 sm:px-6">Código</th>
                <th class="px-4 py-3">Nombre del servicio</th>
                <th class="px-4 py-3">Categoría</th>
                <th class="px-4 py-3">Estado</th>
                <th class="px-4 py-3">Precio base</th>
                <th class="px-4 py-3 text-right sm:px-6">SLA comprometido</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-100 text-xs">
              @if (cargando) {
                <tr>
                  <td class="px-4 py-6 text-zinc-400" colspan="6">Cargando catálogo…</td>
                </tr>
              }

              @for (servicio of servicios; track $index) {
                <tr class="catalog-row transition-colors hover:bg-zinc-50/70" [class.opacity-80]="servicio['status'] === 'SUSPENDED'">
                  <td class="px-4 py-3.5 sm:px-6">
                    <span class="rounded border border-zinc-200/60 bg-zinc-100/70 px-2 py-0.5 font-mono text-[11px] font-semibold text-zinc-900">
                      {{ servicio['code'] }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <div class="space-y-0.5">
                      <p class="font-semibold text-zinc-900">{{ servicio['name'] }}</p>
                      <p class="text-[11px] text-zinc-400">{{ servicio['description'] ?? servicio['category'] }}</p>
                    </div>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="rounded border border-zinc-200 bg-zinc-50 px-2 py-0.5 font-mono text-[11px] text-zinc-600">
                      {{ servicio['category'] }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="badge" [class]="pillServ(servicio['status'])">
                      <span class="h-1.5 w-1.5 rounded-full" [class]="servicio['status'] === 'ACTIVE' ? 'bg-emerald-500' : 'bg-rose-500'"></span>
                      {{ servicio['status'] }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="font-mono text-[13px] font-medium text-zinc-900">
                      {{ precio(servicio['basePrice']) }}
                    </span>
                    <span class="ml-0.5 font-sans text-[10px] text-zinc-400">CLP</span>
                  </td>
                  <td class="px-4 py-3.5 text-right sm:px-6">
                    <span class="rounded border border-blue-100 bg-blue-50 px-2 py-0.5 font-mono text-[11px] font-semibold text-blue-600">
                      {{ servicio['estimatedHours'] ?? '—' }} horas
                    </span>
                  </td>
                </tr>
              }

              @if (!cargando && servicios.length === 0) {
                <tr>
                  <td class="px-4 py-6 text-zinc-400" colspan="6">Sin datos de catálogo.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between border-t border-zinc-100 px-4 py-3 text-[11px] text-zinc-400 sm:px-6">
          <span>Tarifas base sin incluir IVA ni recargos por peso volumétrico</span>
          <span class="font-mono">Cache TTL: 300s</span>
        </div>
      </div>
    </div>
  `,
})
export class CatalogPage implements OnInit {
  private readonly bff = inject(BffService);
  private readonly auth = inject(AuthService);

  servicios: Record<string, any>[] = [];
  cargando = true;
  error = '';

  ngOnInit(): void {
    this.bff.catalogServices().subscribe({
      next: (servicios) => {
        this.servicios = servicios as Record<string, any>[];
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.mensajeDeError(err);
      },
    });
  }

  pillServ(status: unknown): string {
    return pillServicio(String(status));
  }

  precio(valor: unknown): string {
    const numero = Number(valor);
    return Number.isFinite(numero) ? `$${numero.toLocaleString('es-CL')}` : '—';
  }

  private mensajeDeError(err: unknown): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.status === 401) {
      return this.auth.hasSession()
        ? 'No autorizado (401): token rechazado por el backend. Revisa el valor "aud" en el Dashboard.'
        : 'No autorizado (401): no hay token adjunto. Vuelve a iniciar sesión.';
    }
    if (e?.status === 403) {
      return 'Tu usuario no tiene el rol Admin/Despachador para ver el catálogo (403).';
    }
    return e?.error?.message ?? 'No se pudo cargar el catálogo.';
  }
}
