import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqExchange } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmarComponent } from '../../shared/confirmar.component';
import { validarNombreMq } from '../../shared/mq-validators';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/** Exchanges nucleo de RutaExpress: el backend exige force=true para eliminarlos. */
export const EXCHANGES_PROTEGIDOS = ['ex.rutaexpress.cmd', 'ex.rutaexpress.events', 'ex.rutaexpress.dlx'];

/** Exchanges (FE-13): tabla, creación (direct, topic, fanout o headers) y borrado con confirmación. */
@Component({
  selector: 'app-mq-exchanges',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent, ConfirmarComponent],
  template: `
    <div class="space-y-6">
      <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="lista().length === 0" mensajeVacio="No hay exchanges." (reintentar)="cargar()">
        <div class="card overflow-x-auto !p-0">
          <table class="w-full min-w-[520px] text-left text-xs">
            <caption class="sr-only">Exchanges del vhost</caption>
            <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
              <tr>
                <th scope="col" class="px-4 py-3">Nombre</th>
                <th scope="col" class="px-4 py-3">Tipo</th>
                <th scope="col" class="px-4 py-3">Durable</th>
                <th scope="col" class="px-4 py-3">Protegido</th>
                <th scope="col" class="px-4 py-3"><span class="sr-only">Eliminar</span></th>
              </tr>
            </thead>
            <tbody>
              @for (e of lista(); track e.name) {
                <tr class="border-b border-zinc-50 last:border-0">
                  <td class="px-4 py-3 font-mono font-semibold">{{ e.name }}</td>
                  <td class="px-4 py-3">{{ e.type }}</td>
                  <td class="px-4 py-3">{{ e.durable ? 'Sí' : 'No' }}</td>
                  <td class="px-4 py-3">{{ esProtegido(e.name) ? 'Sí' : 'No' }}</td>
                  <td class="px-4 py-3 text-right">
                    <button type="button" class="btn-secondary !px-3 !py-1 !border-rose-200 !text-rose-700 hover:!bg-rose-600 hover:!text-white" (click)="aEliminar.set(e)">Eliminar</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-vista-estado>

      <form class="card grid gap-4 sm:grid-cols-3" (ngSubmit)="crear()" aria-label="Crear exchange">
        <h2 class="text-sm font-bold text-zinc-800 sm:col-span-3">Crear exchange</h2>
        <div>
          <label class="label" for="x-nombre">Nombre</label>
          <input id="x-nombre" class="input" name="nombre" [ngModel]="nombre()" (ngModelChange)="nombre.set($event)" maxlength="255" autocomplete="off" />
        </div>
        <div>
          <label class="label" for="x-tipo">Tipo</label>
          <select id="x-tipo" class="input" name="tipo" [ngModel]="tipo()" (ngModelChange)="tipo.set($event)">
            <option value="direct">direct</option>
            <option value="topic">topic</option>
            <option value="fanout">fanout</option>
            <option value="headers">headers</option>
          </select>
        </div>
        <label class="flex items-end gap-2 pb-2 text-xs text-zinc-700">
          <input type="checkbox" name="durable" [ngModel]="durable()" (ngModelChange)="durable.set($event)" /> Durable
        </label>
        @if (errorForm()) { <p class="text-xs font-semibold text-rose-600 sm:col-span-3" role="alert">{{ errorForm() }}</p> }
        <div class="sm:col-span-3"><button type="submit" class="btn-primary" [disabled]="enCurso()">Crear exchange</button></div>
      </form>
    </div>

    <app-confirmar
      [abierto]="aEliminar() !== null"
      titulo="Eliminar exchange"
      [mensaje]="'¿Eliminar el exchange ' + (aEliminar()?.name ?? '') + '?'"
      [advertencia]="esProtegido(aEliminar()?.name ?? '') ? 'Es parte de la topología protegida: eliminarlo detiene la mensajería del sistema.' : null"
      [textoConfirmar]="esProtegido(aEliminar()?.name ?? '') ? 'Forzar eliminación' : 'Eliminar'"
      [peligroso]="true"
      (cancelar)="aEliminar.set(null)"
      (confirmar)="eliminar()"
    />
  `,
})
export class MqExchangesComponent implements OnInit {
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly lista = signal<MqExchange[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly aEliminar = signal<MqExchange | null>(null);
  readonly nombre = signal('');
  readonly tipo = signal<'direct' | 'topic' | 'fanout' | 'headers'>('direct');
  readonly durable = signal(true);
  readonly enCurso = signal(false);
  readonly errorForm = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  esProtegido(nombre: string): boolean {
    return EXCHANGES_PROTEGIDOS.includes(nombre);
  }

  cargar(): void {
    this.cargando.set(true);
    this.bff.mqExchanges().subscribe({
      next: (lista) => {
        this.lista.set(lista);
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
    const motivo = validarNombreMq(this.nombre(), 'el exchange');
    if (motivo) {
      this.errorForm.set(motivo);
      return;
    }
    this.errorForm.set(null);
    this.enCurso.set(true);
    const nombre = this.nombre().trim();
    this.bff.crearExchange({ name: nombre, type: this.tipo(), durable: this.durable() }).subscribe({
      next: () => {
        this.enCurso.set(false);
        this.nombre.set('');
        this.toasts.exito(`Exchange ${nombre} creado.`);
        this.cargar();
      },
      error: (err: unknown) => {
        this.enCurso.set(false);
        this.errorForm.set(interpretarError(err).mensaje);
      },
    });
  }

  eliminar(): void {
    const exchange = this.aEliminar();
    if (!exchange) return;
    this.aEliminar.set(null);
    this.bff.eliminarExchange(exchange.name, this.esProtegido(exchange.name)).subscribe({
      next: () => {
        this.toasts.exito(`Exchange ${exchange.name} eliminado.`);
        this.cargar();
      },
    });
  }
}
