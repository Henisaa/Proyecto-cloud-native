import { Component, input, output } from '@angular/core';

/**
 * Un estado para cada situación (carga, error, vacío y contenido). Envuelve el contenido de una vista:
 * mientras carga o si falla, el contenido no se muestra y el usuario ve qué pasó y puede reintentar.
 */
@Component({
  selector: 'app-vista-estado',
  standalone: true,
  template: `
    @if (cargando()) {
      <div class="flex items-center justify-center gap-3 rounded-2xl border border-zinc-200/80 bg-white p-10 text-xs text-zinc-500" role="status">
        <svg class="h-4 w-4 animate-spin text-zinc-400" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3.5"></circle>
          <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        {{ mensajeCarga() }}
      </div>
    } @else if (error()) {
      <div class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center" role="alert">
        <p class="text-sm font-semibold text-rose-700">No se pudo cargar</p>
        <p class="mt-1 text-xs text-rose-600">{{ error() }}</p>
        <button type="button" class="btn-secondary mt-4" (click)="reintentar.emit()">Reintentar</button>
      </div>
    } @else if (vacio()) {
      <div class="rounded-2xl border border-dashed border-zinc-300 bg-white p-10 text-center text-xs text-zinc-500">
        {{ mensajeVacio() }}
      </div>
    } @else {
      <ng-content />
    }
  `,
})
export class VistaEstadoComponent {
  readonly cargando = input(false);
  readonly error = input<string | null>(null);
  readonly vacio = input(false);
  readonly mensajeCarga = input('Cargando…');
  readonly mensajeVacio = input('No hay datos para mostrar.');
  readonly reintentar = output<void>();
}
