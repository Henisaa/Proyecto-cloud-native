import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqBinding, MqExchange, MqMetricas } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmarComponent } from '../../shared/confirmar.component';
import { validarClaveMq } from '../../shared/mq-validators';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/** Bindings núcleo del sistema: el backend exige force=true para eliminarlos. */
const ORIGENES_PROTEGIDOS = ['ex.rutaexpress.cmd', 'ex.rutaexpress.events', 'ex.rutaexpress.dlx'];

/** Bindings (FE-14): se crean eligiendo exchanges y colas existentes y se eliminan con su routing key exacta. */
@Component({
  selector: 'app-mq-bindings',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent, ConfirmarComponent],
  template: `
    <div class="space-y-6">
      <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="lista().length === 0" mensajeVacio="No hay bindings." (reintentar)="cargar()">
        <div class="card overflow-x-auto !p-0">
          <table class="w-full min-w-[640px] text-left text-xs">
            <caption class="sr-only">Bindings del vhost</caption>
            <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
              <tr>
                <th scope="col" class="px-4 py-3">Origen</th>
                <th scope="col" class="px-4 py-3">Destino</th>
                <th scope="col" class="px-4 py-3">Tipo</th>
                <th scope="col" class="px-4 py-3">Routing key</th>
                <th scope="col" class="px-4 py-3"><span class="sr-only">Eliminar</span></th>
              </tr>
            </thead>
            <tbody>
              @for (b of lista(); track $index) {
                <tr class="border-b border-zinc-50 last:border-0">
                  <td class="px-4 py-3 font-mono">{{ b.source }}</td>
                  <td class="px-4 py-3 font-mono">{{ b.destination }}</td>
                  <td class="px-4 py-3">{{ b.destinationType }}</td>
                  <td class="px-4 py-3 font-mono">{{ b.routingKey || '(vacía)' }}</td>
                  <td class="px-4 py-3 text-right">
                    <button type="button" class="btn-secondary !px-3 !py-1 !border-rose-200 !text-rose-700 hover:!bg-rose-600 hover:!text-white" (click)="aEliminar.set(b)">Eliminar</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-vista-estado>

      <form class="card grid gap-4 sm:grid-cols-2 lg:grid-cols-4" (ngSubmit)="crear()" aria-label="Crear binding">
        <h2 class="text-sm font-bold text-zinc-800 sm:col-span-2 lg:col-span-4">Crear binding</h2>
        <div>
          <label class="label" for="b-origen">Exchange de origen</label>
          <select id="b-origen" class="input" name="origen" [ngModel]="origen()" (ngModelChange)="origen.set($event)">
            <option value="">Seleccione…</option>
            @for (e of exchanges(); track e.name) { <option [value]="e.name">{{ e.name }} ({{ e.type }})</option> }
          </select>
        </div>
        <div>
          <label class="label" for="b-tipo">Tipo de destino</label>
          <select id="b-tipo" class="input" name="tipoDestino" [ngModel]="tipoDestino()" (ngModelChange)="tipoDestino.set($event); destino.set('')">
            <option value="queue">Cola</option>
            <option value="exchange">Exchange</option>
          </select>
        </div>
        <div>
          <label class="label" for="b-destino">Destino</label>
          <select id="b-destino" class="input" name="destino" [ngModel]="destino()" (ngModelChange)="destino.set($event)">
            <option value="">Seleccione…</option>
            @if (tipoDestino() === 'queue') {
              @for (c of colas(); track c) { <option [value]="c">{{ c }}</option> }
            } @else {
              @for (e of exchanges(); track e.name) { <option [value]="e.name">{{ e.name }}</option> }
            }
          </select>
        </div>
        <div>
          <label class="label" for="b-clave">Routing key</label>
          <input id="b-clave" class="input" name="clave" [ngModel]="clave()" (ngModelChange)="clave.set($event)" placeholder="demo.#" autocomplete="off" />
        </div>
        @if (errorForm()) { <p class="text-xs font-semibold text-rose-600 sm:col-span-2 lg:col-span-4" role="alert">{{ errorForm() }}</p> }
        <div class="sm:col-span-2 lg:col-span-4"><button type="submit" class="btn-primary" [disabled]="enCurso()">Crear binding</button></div>
      </form>
    </div>

    <app-confirmar
      [abierto]="aEliminar() !== null"
      titulo="Eliminar binding"
      [mensaje]="'¿Eliminar ' + (aEliminar()?.source ?? '') + ' → ' + (aEliminar()?.destination ?? '') + ' (' + (aEliminar()?.routingKey ?? '') + ')?'"
      [advertencia]="esProtegido(aEliminar()) ? 'Es parte de la topología protegida: eliminarlo corta el enrutamiento de mensajes.' : null"
      [textoConfirmar]="esProtegido(aEliminar()) ? 'Forzar eliminación' : 'Eliminar'"
      [peligroso]="true"
      (cancelar)="aEliminar.set(null)"
      (confirmar)="eliminar()"
    />
  `,
})
export class MqBindingsComponent implements OnInit {
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly lista = signal<MqBinding[]>([]);
  readonly exchanges = signal<MqExchange[]>([]);
  readonly colas = signal<string[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly aEliminar = signal<MqBinding | null>(null);

  readonly origen = signal('');
  readonly tipoDestino = signal<'queue' | 'exchange'>('queue');
  readonly destino = signal('');
  readonly clave = signal('');
  readonly enCurso = signal(false);
  readonly errorForm = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  esProtegido(b: MqBinding | null): boolean {
    return b !== null && ORIGENES_PROTEGIDOS.includes(b.source);
  }

  cargar(): void {
    this.cargando.set(true);
    forkJoin({ bindings: this.bff.mqBindings(), exchanges: this.bff.mqExchanges(), metricas: this.bff.mqMetricas() }).subscribe({
      next: ({ bindings, exchanges, metricas }: { bindings: MqBinding[]; exchanges: MqExchange[]; metricas: MqMetricas }) => {
        this.lista.set(bindings);
        this.exchanges.set(exchanges);
        this.colas.set(metricas.queues.map((q) => q.name));
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (!this.origen() || !this.destino()) {
      this.errorForm.set('Elija el exchange de origen y el destino');
      return;
    }
    const motivo = validarClaveMq(this.clave());
    if (motivo) {
      this.errorForm.set(motivo);
      return;
    }
    this.errorForm.set(null);
    this.enCurso.set(true);
    this.bff
      .crearBinding({ source: this.origen(), destination: this.destino(), destinationType: this.tipoDestino(), routingKey: this.clave() })
      .subscribe({
        next: () => {
          this.enCurso.set(false);
          this.toasts.exito('Binding creado.');
          this.clave.set('');
          this.cargar();
        },
        error: (err: unknown) => {
          this.enCurso.set(false);
          this.errorForm.set(interpretarError(err).mensaje);
        },
      });
  }

  eliminar(): void {
    const binding = this.aEliminar();
    if (!binding) return;
    this.aEliminar.set(null);
    this.bff.eliminarBinding(binding, this.esProtegido(binding)).subscribe({
      next: () => {
        this.toasts.exito('Binding eliminado.');
        this.cargar();
      },
    });
  }
}
