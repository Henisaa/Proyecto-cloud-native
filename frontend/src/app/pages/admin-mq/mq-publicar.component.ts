import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { from, concatMap, tap } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqExchange, MqPublicarResultado } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { validarClaveMq, validarNombreMq } from '../../shared/mq-validators';

interface Plantilla {
  id: string;
  etiqueta: string;
  exchange: string;
  routingKey: string;
  cuerpo: string;
}

/** Plantillas de las pruebas 9 y 14: un mensaje válido, uno inválido que debe terminar en la DLQ y un comando de capacidad. */
export const PLANTILLAS: Plantilla[] = [
  {
    id: 'notificacion-valida',
    etiqueta: 'Notificación válida',
    exchange: 'ex.rutaexpress.events',
    routingKey: 'notification.email.send',
    cuerpo: JSON.stringify(
      {
        type: 'notification.requested',
        eventId: 'REEMPLAZAR-UUID',
        timestamp: '2026-10-20T12:00:00Z',
        traceId: 'prueba',
        correlationId: 'prueba',
        payload: {
          shipmentId: 'RX-PRUEBA',
          recipientEmail: 'cliente@example.com',
          recipientName: 'Cliente de prueba',
          subject: 'Mensaje de prueba',
          message: 'Hola desde el administrador de RabbitMQ',
        },
      },
      null,
      2
    ),
  },
  {
    id: 'sin-event-id',
    etiqueta: 'Mensaje inválido sin eventId (debe ir a la DLQ)',
    exchange: 'ex.rutaexpress.events',
    routingKey: 'notification.email.send',
    cuerpo: JSON.stringify({ type: 'notification.requested', payload: { shipmentId: 'RX-INVALIDO' } }, null, 2),
  },
  {
    id: 'comando-capacidad',
    etiqueta: 'Comando de capacidad válido',
    exchange: 'ex.rutaexpress.cmd',
    routingKey: 'capacity.reserve',
    cuerpo: JSON.stringify(
      {
        type: 'capacity.reserve.requested',
        eventId: 'REEMPLAZAR-UUID',
        timestamp: '2026-10-20T12:00:00Z',
        traceId: 'prueba',
        correlationId: 'prueba',
        payload: { shipmentId: 'RX-PRUEBA', serviceId: 1, zone: 'URBAN_CENTER', vehicleType: null, packagesCount: 1, weightKg: 1.5, volumeM3: 0.01 },
      },
      null,
      2
    ),
  },
];

/** Genera un UUID por cada mensaje enviado, para que los duplicados no se descarten por idempotencia. */
export function conIdNuevo(cuerpo: string): string {
  return cuerpo.replace(/REEMPLAZAR-UUID/g, () => crypto.randomUUID());
}

/** Publicar un mensaje de prueba (FE-16): se usa en las pruebas 9 y 14 de la guía. */
@Component({
  selector: 'app-mq-publicar',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="card grid gap-4 sm:grid-cols-3" (ngSubmit)="publicar()" aria-label="Publicar mensaje de prueba">
      <h2 class="text-sm font-bold text-zinc-800 sm:col-span-3">Publicar mensaje de prueba</h2>
      <div class="sm:col-span-3">
        <label class="label" for="p-plantilla">Plantilla</label>
        <select id="p-plantilla" class="input" name="plantilla" [ngModel]="plantilla()" (ngModelChange)="usarPlantilla($event)">
          <option value="">(ninguna)</option>
          @for (p of plantillas; track p.id) { <option [value]="p.id">{{ p.etiqueta }}</option> }
        </select>
      </div>
      <div>
        <label class="label" for="p-exchange">Exchange</label>
        <select id="p-exchange" class="input" name="exchange" [ngModel]="exchange()" (ngModelChange)="exchange.set($event)">
          <option value="">Seleccione…</option>
          @for (e of exchanges(); track e.name) { <option [value]="e.name">{{ e.name }} ({{ e.type }})</option> }
        </select>
      </div>
      <div>
        <label class="label" for="p-clave">Routing key</label>
        <input id="p-clave" class="input" name="clave" [ngModel]="clave()" (ngModelChange)="clave.set($event)" autocomplete="off" />
      </div>
      <div>
        <label class="label" for="p-veces">Repetir N veces</label>
        <input id="p-veces" class="input" name="veces" type="number" min="1" max="50" [ngModel]="veces()" (ngModelChange)="veces.set($event)" />
      </div>
      <div class="sm:col-span-3">
        <label class="label" for="p-cuerpo">Cuerpo (JSON)</label>
        <textarea id="p-cuerpo" class="input font-mono text-xs" name="cuerpo" rows="10" [ngModel]="cuerpo()" (ngModelChange)="cuerpo.set($event)"></textarea>
        <p class="mt-1 text-[10px] text-zinc-400">«REEMPLAZAR-UUID» se cambia por un identificador nuevo en cada envío.</p>
      </div>
      @if (errorForm()) { <p class="text-xs font-semibold text-rose-600 sm:col-span-3" role="alert">{{ errorForm() }}</p> }
      <div class="sm:col-span-3"><button type="submit" class="btn-primary" [disabled]="enCurso()">Publicar</button></div>

      @if (resultados().length > 0) {
        <div class="sm:col-span-3" role="status" aria-live="polite">
          <p class="label">Resultados</p>
          <ul class="space-y-1 text-xs">
            @for (r of resultados(); track $index) {
              <li class="flex flex-wrap items-center gap-2">
                <span class="font-mono text-zinc-500">#{{ $index + 1 }}</span>
                <span class="badge" [class]="r.confirmed ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-rose-200 bg-rose-50 text-rose-700'">{{ r.confirmed ? 'confirmado' : 'no confirmado' }}</span>
                <span class="badge" [class]="r.routed ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50 text-amber-700'">{{ r.routed ? 'enrutado' : 'sin ruta' }}</span>
              </li>
            }
          </ul>
        </div>
      }
    </form>
  `,
})
export class MqPublicarComponent implements OnInit {
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly plantillas = PLANTILLAS;
  readonly exchanges = signal<MqExchange[]>([]);
  readonly plantilla = signal('');
  readonly exchange = signal('');
  readonly clave = signal('');
  readonly cuerpo = signal('{}');
  readonly veces = signal(1);
  readonly enCurso = signal(false);
  readonly errorForm = signal<string | null>(null);
  readonly resultados = signal<MqPublicarResultado[]>([]);

  ngOnInit(): void {
    this.bff.mqExchanges().subscribe({ next: (lista) => this.exchanges.set(lista) });
  }

  usarPlantilla(id: string): void {
    this.plantilla.set(id);
    const p = PLANTILLAS.find((x) => x.id === id);
    if (p) {
      this.exchange.set(p.exchange);
      this.clave.set(p.routingKey);
      this.cuerpo.set(p.cuerpo);
    }
  }

  publicar(): void {
    if (this.enCurso()) return;
    const motivo = validarNombreMq(this.exchange(), 'el exchange') ?? validarClaveMq(this.clave());
    if (motivo) {
      this.errorForm.set(motivo);
      return;
    }
    try { JSON.parse(this.cuerpo()); } catch {
      this.errorForm.set('El cuerpo debe ser un JSON válido');
      return;
    }
    const repeticiones = Math.min(50, Math.max(1, Math.floor(Number(this.veces()) || 1)));
    this.errorForm.set(null);
    this.resultados.set([]);
    this.enCurso.set(true);

    // Se publican de a uno, en orden: así en la prueba 14 el 6.º mensaje es el que recibe la confirmación negativa.
    from(Array.from({ length: repeticiones }))
      .pipe(
        concatMap(() =>
          this.bff.publicarMensaje(this.exchange(), { routingKey: this.clave(), body: conIdNuevo(this.cuerpo()) })
        ),
        tap((resultado) => this.resultados.update(actuales => [...actuales, resultado]))
      )
      .subscribe({
        complete: () => {
          this.enCurso.set(false);
          this.toasts.exito(`${this.resultados().length} mensaje(s) publicado(s).`);
        },
        error: (err: unknown) => {
          this.enCurso.set(false);
          this.errorForm.set(interpretarError(err).mensaje);
        },
      });
  }
}

