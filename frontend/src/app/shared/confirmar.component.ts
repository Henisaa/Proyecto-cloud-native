import { Component, input, output } from '@angular/core';

/** Diálogo de confirmación para acciones destructivas (cancelar un envío, eliminar una cola, forzar un borrado). */
@Component({
  selector: 'app-confirmar',
  standalone: true,
  template: `
    @if (abierto()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-zinc-900/40 p-4" role="presentation" (click)="cancelar.emit()">
        <div
          class="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-xl"
          role="alertdialog"
          aria-modal="true"
          [attr.aria-label]="titulo()"
          (click)="$event.stopPropagation()"
        >
          <h2 class="text-base font-bold text-zinc-900">{{ titulo() }}</h2>
          <p class="mt-2 text-sm text-zinc-600">{{ mensaje() }}</p>
          @if (advertencia()) {
            <p class="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-700">{{ advertencia() }}</p>
          }
          <div class="mt-6 flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="cancelar.emit()">Volver</button>
            <button type="button" class="btn-primary" [class]="peligroso() ? '!bg-rose-600 hover:!bg-rose-700' : ''" (click)="confirmar.emit()">
              {{ textoConfirmar() }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmarComponent {
  readonly abierto = input(false);
  readonly titulo = input('Confirmar');
  readonly mensaje = input('');
  readonly advertencia = input<string | null>(null);
  readonly textoConfirmar = input('Confirmar');
  readonly peligroso = input(false);
  readonly confirmar = output<void>();
  readonly cancelar = output<void>();
}
