import { Component, computed, inject, input, output, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { Envio } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmarComponent } from '../../shared/confirmar.component';

export const ETIQUETA_ACCION: Record<string, string> = {
  ACEPTADO: 'Aceptar',
  EN_BODEGA: 'Enviar a bodega',
  EN_RUTA: 'Despachar a ruta',
  ENTREGADO: 'Marcar entregado',
  CANCELADO: 'Cancelar envío',
};

/**
 * Acciones de un envío según el rol (Admin y Despachador) y según lo que el backend permite hoy (allowedTransitions).
 * Los botones se deshabilitan mientras hay una petición en curso (evita el doble clic), cancelar pide confirmación
 * y un 409 muestra el mensaje exacto del backend (lo muestra el interceptor de errores).
 */
@Component({
  selector: 'app-acciones-envio',
  standalone: true,
  imports: [ConfirmarComponent],
  template: `
    @if (puedeOperar()) {
      <div class="flex flex-wrap items-center gap-1.5">
        @for (destino of avanzar(); track destino) {
          <button
            type="button"
            class="btn-primary !px-3 !py-1.5"
            [disabled]="enCurso()"
            [attr.data-accion]="destino"
            (click)="aplicar(destino)"
          >
            {{ etiqueta(destino) }}
          </button>
        }
        @if (envio().canRetryCapacity) {
          <button type="button" class="btn-secondary !px-3 !py-1.5" [disabled]="enCurso()" data-accion="reintentar" (click)="reintentar()">
            Reintentar reserva
          </button>
        }
        @if (puedeCancelar()) {
          <button
            type="button"
            class="btn-secondary !px-3 !py-1.5 !border-rose-200 !text-rose-700 hover:!bg-rose-600 hover:!text-white"
            [disabled]="enCurso()"
            data-accion="CANCELADO"
            (click)="confirmando.set(true)"
          >
            {{ etiqueta('CANCELADO') }}
          </button>
        }
      </div>
    }

    <app-confirmar
      [abierto]="confirmando()"
      titulo="Cancelar envío"
      [mensaje]="'¿Cancelar el envío ' + envio().trackingCode + '? Esta acción no se puede deshacer.'"
      [advertencia]="envio().capacityStatus === 'RESERVADA' ? 'Se devolverá el cupo reservado al catálogo.' : null"
      textoConfirmar="Sí, cancelar"
      [peligroso]="true"
      (cancelar)="confirmando.set(false)"
      (confirmar)="confirmarCancelacion()"
    />
  `,
})
export class AccionesEnvioComponent {
  private readonly bff = inject(BffService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

  readonly envio = input.required<Envio>();
  readonly actualizado = output<Envio>();

  readonly enCurso = signal(false);
  readonly confirmando = signal(false);

  readonly puedeOperar = computed(() => this.auth.hasRole('Admin', 'Despachador'));
  readonly avanzar = computed(() => this.envio().allowedTransitions.filter((t) => t !== 'CANCELADO'));
  readonly puedeCancelar = computed(() => this.envio().allowedTransitions.includes('CANCELADO'));

  etiqueta(destino: string): string {
    return ETIQUETA_ACCION[destino] ?? destino;
  }

  aplicar(destino: string): void {
    if (this.enCurso()) return;
    this.enCurso.set(true);
    this.bff.cambiarEstado(this.envio().id, destino).subscribe({
      next: (envio) => {
        this.enCurso.set(false);
        if (destino === 'ACEPTADO' && envio.capacityStatus === 'PENDIENTE') {
          this.toasts.info('Reserva en proceso: la capacidad se confirma en unos segundos.');
        } else {
          this.toasts.exito(`Envío ${envio.trackingCode}: ${this.etiqueta(destino).toLowerCase()}.`);
        }
        this.actualizado.emit(envio);
      },
      error: () => this.enCurso.set(false),
    });
  }

  confirmarCancelacion(): void {
    this.confirmando.set(false);
    this.aplicar('CANCELADO');
  }

  reintentar(): void {
    if (this.enCurso()) return;
    this.enCurso.set(true);
    this.bff.reintentarReserva(this.envio().id).subscribe({
      next: (envio) => {
        this.enCurso.set(false);
        this.toasts.info('Reserva en proceso: se volvió a pedir la capacidad.');
        this.actualizado.emit(envio);
      },
      error: () => this.enCurso.set(false),
    });
  }
}
