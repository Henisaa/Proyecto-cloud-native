import { Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';

/** Avisos emergentes. role="status" + aria-live: los lectores de pantalla anuncian cada resultado asíncrono. */
@Component({
  selector: 'app-toasts',
  standalone: true,
  template: `
    <div class="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4 sm:items-end sm:px-6" aria-live="polite" role="status">
      @for (toast of toasts.toasts(); track toast.id) {
        <div
          class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border px-4 py-3 text-xs shadow-lg"
          [class]="clase(toast.tipo)"
        >
          <p class="flex-1 break-words font-medium">{{ toast.mensaje }}</p>
          <button type="button" class="shrink-0 font-bold opacity-60 hover:opacity-100" aria-label="Cerrar aviso" (click)="toasts.cerrar(toast.id)">×</button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  readonly toasts = inject(ToastService);

  clase(tipo: string): string {
    switch (tipo) {
      case 'exito':
        return 'border-emerald-200 bg-emerald-50 text-emerald-800';
      case 'error':
        return 'border-rose-200 bg-rose-50 text-rose-800';
      default:
        return 'border-blue-200 bg-blue-50 text-blue-800';
    }
  }
}
