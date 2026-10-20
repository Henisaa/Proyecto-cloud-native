import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqMetricas, MqNodo } from '../../core/models';
import { bytes, duracion, numero } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';
import { MqBindingsComponent } from './mq-bindings.component';
import { MqColasComponent } from './mq-colas.component';
import { MqExchangesComponent } from './mq-exchanges.component';
import { MqPublicarComponent } from './mq-publicar.component';
import { MqRetencionComponent } from './mq-retencion.component';

type Pestana = 'resumen' | 'colas' | 'exchanges' | 'bindings' | 'retencion' | 'publicar';

/** Ayuda junto a cada métrica: cómo se interpreta (sirve para la defensa). */
export const AYUDA_METRICAS = {
  activeQueues: 'Colas existentes en el vhost. Las tres colas de trabajo (command, notification, audit) más sus DLQ.',
  messagesReady: 'Mensajes esperando a un consumidor (backlog). Si crece sin parar, falta consumo.',
  messagesUnacknowledged: 'Mensajes entregados a un consumidor que aún no confirma (ACK): trabajo en proceso.',
  consumers: 'Consumidores conectados. Una cola con 0 consumidores acumula mensajes sin control.',
  dlqQueues: 'Colas de mensajes muertos: reciben lo que se rechazó (NACK sin reencolar), venció (TTL) o desbordó (maxlen).',
  dlqMessages: 'Mensajes en las DLQ ahora. Una DLQ que se consume y se registra en log casi siempre está en 0.',
  dlqArrivedTotal: 'Acumulado de mensajes que llegaron a las DLQ desde que arrancó el nodo: la métrica real de fallos.',
} as const;

/** Administración RabbitMQ (FE-11 a FE-16, solo Admin). */
@Component({
  selector: 'app-admin-mq',
  standalone: true,
  imports: [VistaEstadoComponent, MqColasComponent, MqExchangesComponent, MqBindingsComponent, MqRetencionComponent, MqPublicarComponent],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Administración de RabbitMQ</h1>
          <p class="mt-1 text-xs text-zinc-500">Colas, exchanges, bindings, retención y mensajes de prueba del clúster.</p>
        </div>
      </header>

      <div role="tablist" aria-label="Secciones de RabbitMQ" class="flex flex-wrap gap-1 border-b border-zinc-200">
        @for (p of pestanas; track p.id) {
          <button
            type="button"
            role="tab"
            [id]="'tab-' + p.id"
            [attr.aria-selected]="pestana() === p.id"
            [attr.aria-controls]="'panel-' + p.id"
            class="rounded-t-lg px-4 py-2 text-xs font-semibold transition"
            [class]="pestana() === p.id ? 'bg-zinc-900 text-white' : 'text-zinc-600 hover:bg-zinc-100'"
            (click)="pestana.set(p.id)"
          >
            {{ p.etiqueta }}
          </button>
        }
      </div>

      <div role="tabpanel" [id]="'panel-' + pestana()" [attr.aria-labelledby]="'tab-' + pestana()">
        @switch (pestana()) {
          @case ('resumen') {
            <div class="space-y-4">
              <div class="flex flex-wrap items-center gap-3 text-xs text-zinc-500">
                <label class="flex items-center gap-2">
                  <input type="checkbox" [checked]="auto()" (change)="auto.set($any($event.target).checked)" />
                  Actualizar cada 10 s
                </label>
                <button type="button" class="btn-secondary" (click)="cargar()">Actualizar ahora</button>
                @if (ultima()) { <span>Última consulta: {{ ultima() }}</span> }
              </div>

              <app-vista-estado [cargando]="cargando()" [error]="error()" (reintentar)="cargar()">
                <section aria-labelledby="titulo-nodos" class="space-y-2">
                  <h2 id="titulo-nodos" class="text-sm font-bold text-zinc-800">Nodos del clúster</h2>
                  <div class="grid gap-3 sm:grid-cols-2">
                    @for (n of nodos(); track n.name) {
                      <div class="card" [class]="n.running ? '' : 'border-rose-300'">
                        <div class="flex items-center justify-between">
                          <p class="font-mono text-sm font-semibold">{{ n.name }}</p>
                          <span class="badge" [class]="n.running ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-rose-200 bg-rose-50 text-rose-700'">
                            {{ n.running ? 'EN EJECUCIÓN' : 'CAÍDO' }}
                          </span>
                        </div>
                        <p class="mt-2 text-xs text-zinc-500">
                          Memoria {{ bytes(n.memUsed) }} / {{ bytes(n.memLimit) }} · activo {{ duracion(n.uptimeMs) }}
                        </p>
                        @if (n.memAlarm || n.diskFreeAlarm) {
                          <p class="mt-1 text-xs font-semibold text-rose-600" role="alert">
                            Alarma: {{ n.memAlarm ? 'memoria' : '' }}{{ n.memAlarm && n.diskFreeAlarm ? ' y ' : '' }}{{ n.diskFreeAlarm ? 'disco' : '' }}
                          </p>
                        }
                      </div>
                    } @empty {
                      <p class="text-xs text-zinc-400">Sin información de nodos.</p>
                    }
                  </div>
                </section>

                @if (metricas(); as m) {
                  <section aria-labelledby="titulo-metricas" class="mt-6 space-y-2">
                    <h2 id="titulo-metricas" class="text-sm font-bold text-zinc-800">Métricas</h2>
                    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      <div class="card" [attr.title]="ayuda.activeQueues"><p class="label">Colas activas</p><p class="font-mono text-2xl font-bold">{{ numero(m.activeQueues) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.activeQueues }}</p></div>
                      <div class="card" [attr.title]="ayuda.messagesReady"><p class="label">Mensajes listos</p><p class="font-mono text-2xl font-bold">{{ numero(m.messagesReady) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.messagesReady }}</p></div>
                      <div class="card" [attr.title]="ayuda.messagesUnacknowledged"><p class="label">Sin confirmar</p><p class="font-mono text-2xl font-bold">{{ numero(m.messagesUnacknowledged) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.messagesUnacknowledged }}</p></div>
                      <div class="card" [attr.title]="ayuda.consumers"><p class="label">Consumidores</p><p class="font-mono text-2xl font-bold">{{ numero(m.consumers) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.consumers }}</p></div>
                      <div class="card" [attr.title]="ayuda.dlqQueues"><p class="label">Colas DLQ</p><p class="font-mono text-2xl font-bold">{{ numero(m.dlqQueues) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.dlqQueues }}</p></div>
                      <div class="card" [attr.title]="ayuda.dlqMessages"><p class="label">En la DLQ ahora</p><p class="font-mono text-2xl font-bold">{{ numero(m.dlqMessages) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.dlqMessages }}</p></div>
                      <div class="card border-rose-200" [attr.title]="ayuda.dlqArrivedTotal"><p class="label">Llegados a la DLQ</p><p class="font-mono text-2xl font-bold text-rose-600" data-testid="dlq-acumulado">{{ numero(m.dlqArrivedTotal) }}</p><p class="mt-1 text-[10px] text-zinc-400">{{ ayuda.dlqArrivedTotal }} Ritmo reciente: {{ m.dlqArrivedRate }} /s.</p></div>
                    </div>
                  </section>
                }
              </app-vista-estado>
            </div>
          }
          @case ('colas') { <app-mq-colas /> }
          @case ('exchanges') { <app-mq-exchanges /> }
          @case ('bindings') { <app-mq-bindings /> }
          @case ('retencion') { <app-mq-retencion /> }
          @case ('publicar') { <app-mq-publicar /> }
        }
      </div>
    </div>
  `,
})
export class AdminMqPage implements OnInit {
  private readonly bff = inject(BffService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pestanas: { id: Pestana; etiqueta: string }[] = [
    { id: 'resumen', etiqueta: 'Resumen' },
    { id: 'colas', etiqueta: 'Colas' },
    { id: 'exchanges', etiqueta: 'Exchanges' },
    { id: 'bindings', etiqueta: 'Bindings' },
    { id: 'retencion', etiqueta: 'Retención y topología' },
    { id: 'publicar', etiqueta: 'Mensaje de prueba' },
  ];
  readonly pestana = signal<Pestana>('resumen');
  readonly auto = signal(true);
  readonly ultima = signal<string | null>(null);

  readonly nodos = signal<MqNodo[]>([]);
  readonly metricas = signal<MqMetricas | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly ayuda = AYUDA_METRICAS;
  readonly numero = numero;
  readonly bytes = bytes;
  readonly duracion = duracion;

  ngOnInit(): void {
    // Refresco automático cada 10 s, que se puede desactivar. Solo consulta en la pestaña de resumen.
    timer(0, 10_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.auto() && this.pestana() === 'resumen') this.cargar(true);
      });
  }

  cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);
    forkJoin({ nodos: this.bff.mqCluster(silencioso), metricas: this.bff.mqMetricas(silencioso) }).subscribe({
      next: ({ nodos, metricas }) => {
        this.nodos.set(nodos);
        this.metricas.set(metricas);
        this.error.set(null);
        this.cargando.set(false);
        this.ultima.set(new Date().toLocaleTimeString('es-CL'));
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }
}
