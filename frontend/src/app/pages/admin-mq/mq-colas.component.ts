import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqColaRequest, MqExchange, MqMetricas } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmarComponent } from '../../shared/confirmar.component';
import { numero } from '../../shared/format';
import { validarCola } from '../../shared/mq-validators';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

type FilaCola = MqMetricas['queues'][number] & { protegida: boolean };

/** Colas (FE-12): tabla con todas las opciones de creación que acepta el backend y borrado con confirmación. */
@Component({
  selector: 'app-mq-colas',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent, ConfirmarComponent],
  template: `
    <div class="space-y-6">
      <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="filas().length === 0" mensajeVacio="No hay colas." (reintentar)="cargar()">
        <div class="card overflow-x-auto !p-0">
          <table class="w-full min-w-[840px] text-left text-xs">
            <caption class="sr-only">Colas del vhost</caption>
            <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
              <tr>
                <th scope="col" class="px-4 py-3">Nombre</th>
                <th scope="col" class="px-4 py-3">Durable</th>
                <th scope="col" class="px-4 py-3">Listos</th>
                <th scope="col" class="px-4 py-3">Sin confirmar</th>
                <th scope="col" class="px-4 py-3">Consumidores</th>
                <th scope="col" class="px-4 py-3">Política</th>
                <th scope="col" class="px-4 py-3">DLX</th>
                <th scope="col" class="px-4 py-3"><span class="sr-only">Eliminar</span></th>
              </tr>
            </thead>
            <tbody>
              @for (c of filas(); track c.name) {
                <tr class="border-b border-zinc-50 last:border-0">
                  <td class="px-4 py-3 font-mono font-semibold">
                    {{ c.name }}
                    @if (c.protegida) { <span class="badge ml-1 border-amber-200 bg-amber-50 text-amber-700">protegida</span> }
                  </td>
                  <td class="px-4 py-3">{{ c.durable === false ? 'No' : 'Sí' }}</td>
                  <td class="px-4 py-3">{{ numero(c.messagesReady ?? c.messages) }}</td>
                  <td class="px-4 py-3">{{ numero(c.messagesUnacknowledged ?? 0) }}</td>
                  <td class="px-4 py-3">{{ numero(c.consumers) }}</td>
                  <td class="px-4 py-3 text-zinc-600">{{ c.policy ?? '—' }}</td>
                  <td class="px-4 py-3 font-mono text-[11px] text-zinc-600">{{ c.deadLetterExchange ?? '—' }}</td>
                  <td class="px-4 py-3 text-right">
                    <button type="button" class="btn-secondary !px-3 !py-1 !border-rose-200 !text-rose-700 hover:!bg-rose-600 hover:!text-white" (click)="pedirEliminar(c)">Eliminar</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-vista-estado>

      <form class="card grid gap-4 sm:grid-cols-2 lg:grid-cols-3" (ngSubmit)="crear()" aria-label="Crear cola">
        <h2 class="text-sm font-bold text-zinc-800 sm:col-span-2 lg:col-span-3">Crear cola</h2>
        <div class="lg:col-span-2">
          <label class="label" for="c-nombre">Nombre</label>
          <input id="c-nombre" class="input" name="nombre" [ngModel]="nueva().name" (ngModelChange)="cambiar({ name: $event })" maxlength="255" autocomplete="off" />
        </div>
        <label class="flex items-end gap-2 pb-2 text-xs text-zinc-700">
          <input type="checkbox" name="durable" [ngModel]="nueva().durable" (ngModelChange)="cambiar({ durable: $event })" /> Durable
        </label>
        <div>
          <label class="label" for="c-ttl">TTL de mensajes (ms)</label>
          <input id="c-ttl" class="input" name="ttl" type="number" min="1000" [ngModel]="nueva().ttlMs" (ngModelChange)="cambiar({ ttlMs: numeroONulo($event) })" />
        </div>
        <div>
          <label class="label" for="c-max">Máximo de mensajes</label>
          <input id="c-max" class="input" name="max" type="number" min="1" max="1000000" [ngModel]="nueva().maxLength" (ngModelChange)="cambiar({ maxLength: numeroONulo($event) })" />
        </div>
        <div>
          <label class="label" for="c-overflow">Al desbordar</label>
          <select id="c-overflow" class="input" name="overflow" [ngModel]="nueva().overflow ?? ''" (ngModelChange)="cambiar({ overflow: $event || null })">
            <option value="">(por defecto)</option>
            <option value="reject-publish">reject-publish</option>
            <option value="drop-head">drop-head</option>
          </select>
        </div>
        <div>
          <label class="label" for="c-exp">Expiración sin uso (ms)</label>
          <input id="c-exp" class="input" name="exp" type="number" min="1000" [ngModel]="nueva().expiresMs" (ngModelChange)="cambiar({ expiresMs: numeroONulo($event) })" />
        </div>
        <div>
          <label class="label" for="c-dlx">Exchange dead-letter (DLX)</label>
          <select id="c-dlx" class="input" name="dlx" [ngModel]="nueva().deadLetterExchange ?? ''" (ngModelChange)="cambiar({ deadLetterExchange: $event || null })">
            <option value="">(ninguno)</option>
            @for (e of exchanges(); track e.name) { <option [value]="e.name">{{ e.name }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="c-dlk">Routing key del DLX</label>
          <input id="c-dlk" class="input" name="dlk" [ngModel]="nueva().deadLetterRoutingKey ?? ''" (ngModelChange)="cambiar({ deadLetterRoutingKey: $event || null })" />
        </div>
        @if (errorForm()) {
          <p class="text-xs font-semibold text-rose-600 sm:col-span-2 lg:col-span-3" role="alert">{{ errorForm() }}</p>
        }
        <div class="sm:col-span-2 lg:col-span-3">
          <button type="submit" class="btn-primary" [disabled]="enCurso()">Crear cola</button>
        </div>
      </form>
    </div>

    <app-confirmar
      [abierto]="aEliminar() !== null"
      titulo="Eliminar cola"
      [mensaje]="'¿Eliminar la cola ' + (aEliminar()?.name ?? '') + '?'"
      [advertencia]="aEliminar()?.protegida ? 'Es parte de la topología protegida del sistema. Eliminarla detiene el flujo de mensajes hasta recrearla.' : null"
      [textoConfirmar]="aEliminar()?.protegida ? 'Forzar eliminación' : 'Eliminar'"
      [peligroso]="true"
      (cancelar)="aEliminar.set(null)"
      (confirmar)="eliminar()"
    />
  `,
})
export class MqColasComponent implements OnInit {
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly filas = signal<FilaCola[]>([]);
  readonly exchanges = signal<MqExchange[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly aEliminar = signal<FilaCola | null>(null);
  readonly enCurso = signal(false);
  readonly errorForm = signal<string | null>(null);
  readonly nueva = signal<MqColaRequest>({ name: '', durable: true });

  readonly numero = numero;

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    forkJoin({ metricas: this.bff.mqMetricas(), protegidas: this.bff.mqColas(), exchanges: this.bff.mqExchanges() }).subscribe({
      next: ({ metricas, protegidas, exchanges }) => {
        const nombresProtegidos = new Set(protegidas.map((c) => c.name));
        this.filas.set(metricas.queues.map((q) => ({ ...q, protegida: nombresProtegidos.has(q.name) })));
        this.exchanges.set(exchanges);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }

  cambiar(cambios: Partial<MqColaRequest>): void {
    this.nueva.update((actual) => ({ ...actual, ...cambios }));
  }

  numeroONulo(valor: unknown): number | null {
    return valor === null || valor === undefined || valor === '' ? null : Number(valor);
  }

  crear(): void {
    const cola = this.nueva();
    const motivo = validarCola(cola);
    if (motivo) {
      this.errorForm.set(motivo);
      return;
    }
    this.errorForm.set(null);
    this.enCurso.set(true);
    this.bff.crearCola({ ...cola, name: cola.name.trim() }).subscribe({
      next: () => {
        this.enCurso.set(false);
        this.toasts.exito(`Cola ${cola.name.trim()} creada.`);
        this.nueva.set({ name: '', durable: true });
        this.cargar();
      },
      error: (err: unknown) => {
        this.enCurso.set(false);
        this.errorForm.set(interpretarError(err).mensaje);
      },
    });
  }

  pedirEliminar(fila: FilaCola): void {
    this.aEliminar.set(fila);
  }

  eliminar(): void {
    const fila = this.aEliminar();
    if (!fila) return;
    this.aEliminar.set(null);
    this.bff.eliminarCola(fila.name, fila.protegida).subscribe({
      next: () => {
        this.toasts.exito(`Cola ${fila.name} eliminada.`);
        this.cargar();
      },
    });
  }
}
