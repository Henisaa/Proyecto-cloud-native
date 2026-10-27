import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { EventoLinea, Envio } from '../../core/models';
import { fechaHora } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/**
 * Auditoría (FE-10, Admin y Auditor): se busca por código de seguimiento o por id de envío y se muestra la línea de
 * tiempo unificada con sus dos fuentes: el registro de RabbitMQ (shipments) y la auditoría de Kafka (EP1).
 */
@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent],
  template: `
    <div class="space-y-6">
      <header class="border-b border-zinc-200/60 pb-6">
        <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Auditoría</h1>
        <p class="mt-1 text-xs text-zinc-500">
          Eventos de un envío desde dos fuentes: el registro de RabbitMQ y la auditoría de Kafka (tópicos
          <span class="font-mono">shipments.events</span> y <span class="font-mono">audit.timeline</span>).
        </p>
      </header>

      <form class="flex flex-col gap-3 sm:flex-row sm:items-end" (ngSubmit)="buscar()">
        <div class="flex-1">
          <label class="label" for="a-q">Código de seguimiento o id del envío</label>
          <input id="a-q" class="input" name="q" [ngModel]="termino()" (ngModelChange)="termino.set($event)" placeholder="RX-XXXXXXXX" autocomplete="off" />
          @if (errorCampo()) { <p class="mt-1 text-[11px] text-rose-600" role="alert">{{ errorCampo() }}</p> }
        </div>
        <button type="submit" class="btn-primary" [disabled]="cargando()">Buscar</button>
      </form>

      @if (consultado()) {
        <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="eventos().length === 0" mensajeVacio="El envío no tiene eventos registrados." (reintentar)="buscar()">
          @if (envio(); as e) {
            <p class="text-xs text-zinc-500">Envío <span class="font-mono font-semibold text-zinc-800">{{ e.trackingCode }}</span> · estado {{ e.status }}</p>
          }
          @if (!kafkaDisponible()) {
            <p class="mb-3 rounded-lg border border-amber-200 bg-amber-50 p-2 text-[11px] text-amber-700">
              La auditoría de Kafka no está disponible (la EP1 puede estar apagada): se muestran solo los eventos de RabbitMQ.
            </p>
          }
          <div class="card overflow-x-auto !p-0">
            <table class="w-full min-w-[720px] text-left text-xs">
              <caption class="sr-only">Línea de tiempo del envío</caption>
              <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                <tr>
                  <th scope="col" class="px-4 py-3">Fecha</th>
                  <th scope="col" class="px-4 py-3">Fuente</th>
                  <th scope="col" class="px-4 py-3">Tipo</th>
                  <th scope="col" class="px-4 py-3">Routing key / origen</th>
                  <th scope="col" class="px-4 py-3">Actor</th>
                  <th scope="col" class="px-4 py-3">Detalle</th>
                </tr>
              </thead>
              <tbody>
                @for (ev of eventos(); track $index) {
                  <tr class="border-b border-zinc-50 align-top last:border-0">
                    <td class="px-4 py-3 whitespace-nowrap">{{ fechaHora(ev.occurredAt) }}</td>
                    <td class="px-4 py-3"><span class="badge" [class]="ev.source === 'KAFKA' ? 'border-violet-200 bg-violet-50 text-violet-700' : 'border-blue-200 bg-blue-50 text-blue-700'">{{ ev.source }}</span></td>
                    <td class="px-4 py-3">{{ ev.eventType ?? '—' }}</td>
                    <td class="px-4 py-3 font-mono text-[11px]">{{ ev.routingKey ?? '—' }}</td>
                    <td class="px-4 py-3">{{ ev.actor ?? 'sistema' }}</td>
                    <td class="px-4 py-3 break-words text-zinc-500">{{ ev.detail ?? '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </app-vista-estado>
      }
    </div>
  `,
})
export class AuditPage {
  private readonly bff = inject(BffService);

  readonly termino = signal('');
  readonly errorCampo = signal<string | null>(null);
  readonly consultado = signal(false);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly eventos = signal<EventoLinea[]>([]);
  readonly kafkaDisponible = signal(true);
  readonly envio = signal<Envio | null>(null);

  readonly fechaHora = fechaHora;

  buscar(): void {
    const valor = this.termino().trim();
    if (valor.length === 0) {
      this.errorCampo.set('Ingrese un código de seguimiento o un id de envío');
      return;
    }
    this.errorCampo.set(null);
    this.consultado.set(true);
    this.cargando.set(true);
    this.error.set(null);
    this.envio.set(null);

    this.bff.resolverAuditoria(valor).subscribe({
      next: (referencia) => this.cargarLinea(referencia.id),
      error: (err: unknown) => this.fallar(err),
    });
  }

  private cargarLinea(id: string): void {
    this.bff.lineaDeTiempo(id).subscribe({
      next: (linea) => {
        this.eventos.set(linea.events);
        this.kafkaDisponible.set(linea.kafkaAvailable);
        this.cargando.set(false);
      },
      error: (err: unknown) => this.fallar(err),
    });
  }

  private fallar(err: unknown): void {
    this.error.set(interpretarError(err).mensaje);
    this.cargando.set(false);
  }
}
