import { Injectable, signal } from '@angular/core';

export type TipoToast = 'exito' | 'error' | 'info';

export interface Toast {
  id: number;
  tipo: TipoToast;
  mensaje: string;
}

/** Avisos emergentes. El contenedor tiene aria-live para que los lectores de pantalla anuncien los resultados. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private siguiente = 1;
  readonly toasts = signal<Toast[]>([]);

  exito(mensaje: string): void {
    this.mostrar('exito', mensaje, 4000);
  }

  error(mensaje: string): void {
    this.mostrar('error', mensaje, 7000);
  }

  info(mensaje: string): void {
    this.mostrar('info', mensaje, 4000);
  }

  cerrar(id: number): void {
    this.toasts.update((lista) => lista.filter((t) => t.id !== id));
  }

  private mostrar(tipo: TipoToast, mensaje: string, duracionMs: number): void {
    // Un mismo mensaje repetido no apila avisos idénticos.
    if (this.toasts().some((t) => t.mensaje === mensaje && t.tipo === tipo)) return;
    const id = this.siguiente++;
    this.toasts.update((lista) => [...lista, { id, tipo, mensaje }]);
    setTimeout(() => this.cerrar(id), duracionMs);
  }
}
