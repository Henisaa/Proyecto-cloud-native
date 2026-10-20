import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, Subscription, catchError, debounceTime, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { ESTADOS_CAPACIDAD, ESTADOS_ENVIO, Envio, FiltrosEnvios, Pagina } from '../../core/models';
import { sondear } from '../../core/polling';
import { esSoloCliente } from '../../core/roles';
import { etiquetaCapacidad, hayReservaPendiente, pillCapacidad, pillEstado } from '../../shared/estado-ui';
import { decimal, fechaHora } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';
import { AccionesEnvioComponent } from './acciones-envio.component';

const TAMANO_PAGINA = 20;

/** Lista de envíos (FE-04): paginación en el servidor, filtros, capacidad visible y actualización automática. */
@Component({
  selector: 'app-shipments',
  standalone: true,
  imports: [FormsModule, RouterLink, VistaEstadoComponent, AccionesEnvioComponent],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">{{ soloCliente() ? 'Mis envíos' : 'Gestión de envíos' }}</h1>
          <p class="mt-1 text-xs text-zinc-500">
            {{ pagina()?.totalElements ?? 0 }} envío(s). El estado de la capacidad se actualiza solo mientras hay reservas en proceso.
          </p>
        </div>
        @if (auth.hasRole('Admin', 'Despachador', 'Cliente')) {
          <a routerLink="/shipments/new" class="btn-primary">+ Nuevo envío</a>
        }
      </header>

      <form class="grid gap-3 rounded-2xl border border-zinc-200/80 bg-white p-4 sm:grid-cols-2 lg:grid-cols-5" (ngSubmit)="aplicarFiltros()" aria-label="Filtros de envíos">
        <div>
          <label class="label" for="f-q">Seguimiento</label>
          <input id="f-q" class="input" name="q" type="search" placeholder="RX-…" [ngModel]="q()" (ngModelChange)="q.set($event); buscar$.next()" />
        </div>
        <div>
          <label class="label" for="f-estado">Estado</label>
          <select id="f-estado" class="input" name="estado" [ngModel]="estado()" (ngModelChange)="estado.set($event); aplicarFiltros()">
            <option value="">Todos</option>
            @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="f-capacidad">Capacidad</label>
          <select id="f-capacidad" class="input" name="capacidad" [ngModel]="capacidad()" (ngModelChange)="capacidad.set($event); aplicarFiltros()">
            <option value="">Todas</option>
            @for (c of capacidades; track c) { <option [value]="c">{{ c }}</option> }
          </select>
        </div>
        <div>
          <label class="label" for="f-desde">Desde</label>
          <input id="f-desde" class="input" name="desde" type="date" [ngModel]="desde()" (ngModelChange)="desde.set($event); aplicarFiltros()" />
        </div>
        <div>
          <label class="label" for="f-hasta">Hasta</label>
          <input id="f-hasta" class="input" name="hasta" type="date" [ngModel]="hasta()" (ngModelChange)="hasta.set($event); aplicarFiltros()" />
        </div>
      </form>

      <app-vista-estado
        [cargando]="cargando()"
        [error]="error()"
        [vacio]="!cargando() && (pagina()?.content?.length ?? 0) === 0"
        mensajeVacio="No hay envíos que coincidan con los filtros."
        (reintentar)="recargar()"
      >
        <div class="card overflow-x-auto !p-0" aria-live="polite">
          <table class="w-full min-w-[960px] text-left text-xs">
            <caption class="sr-only">Lista de envíos</caption>
            <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
              <tr>
                <th scope="col" class="px-4 py-3">Seguimiento</th>
                <th scope="col" class="px-4 py-3">Servicio</th>
                <th scope="col" class="px-4 py-3">Zona</th>
                <th scope="col" class="px-4 py-3">Estado</th>
                <th scope="col" class="px-4 py-3">Capacidad</th>
                <th scope="col" class="px-4 py-3">Destinatario</th>
                <th scope="col" class="px-4 py-3">Destino</th>
                <th scope="col" class="px-4 py-3">Carga</th>
                <th scope="col" class="px-4 py-3">Actualizado</th>
                <th scope="col" class="px-4 py-3"><span class="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              @for (e of pagina()?.content ?? []; track e.id) {
                <tr class="border-b border-zinc-50 align-top last:border-0" [class]="e.id === resaltado() ? 'bg-blue-50/60' : ''">
                  <td class="px-4 py-3 font-mono font-semibold">
                    <a [routerLink]="['/shipments', e.id]" class="text-blue-700 hover:underline">{{ e.trackingCode }}</a>
                  </td>
                  <td class="px-4 py-3 text-zinc-600">{{ e.serviceCode ?? ('#' + e.serviceId) }}</td>
                  <td class="px-4 py-3 text-zinc-600">{{ e.zone ?? '—' }}</td>
                  <td class="px-4 py-3"><span class="badge" [class]="pillEstado(e.status)">{{ e.status }}</span></td>
                  <td class="px-4 py-3">
                    <span class="badge" [class]="pillCapacidad(e.capacityStatus)" [attr.data-testid]="'capacidad-' + e.trackingCode">{{ etiquetaCapacidad(e.capacityStatus) }}</span>
                  </td>
                  <td class="px-4 py-3">{{ e.recipientName }}</td>
                  <td class="px-4 py-3 text-zinc-600">{{ e.destinationAddress }}</td>
                  <td class="px-4 py-3 text-zinc-600">{{ e.packagesCount }} pq · {{ decimal(e.weightKg) }} kg</td>
                  <td class="px-4 py-3 text-zinc-500">{{ fechaHora(e.updatedAt) }}</td>
                  <td class="px-4 py-3"><app-acciones-envio [envio]="e" (actualizado)="reemplazar($event)" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <nav class="mt-3 flex items-center justify-between text-xs text-zinc-500" aria-label="Paginación">
          <span>Página {{ (pagina()?.page ?? 0) + 1 }} de {{ pagina()?.totalPages || 1 }}</span>
          <div class="flex gap-2">
            <button type="button" class="btn-secondary" [disabled]="(pagina()?.page ?? 0) === 0" (click)="irAPagina((pagina()?.page ?? 0) - 1)">Anterior</button>
            <button type="button" class="btn-secondary" [disabled]="(pagina()?.page ?? 0) + 1 >= (pagina()?.totalPages ?? 1)" (click)="irAPagina((pagina()?.page ?? 0) + 1)">Siguiente</button>
          </div>
        </nav>
      </app-vista-estado>
    </div>
  `,
})
export class ShipmentsPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly bff = inject(BffService);
  private readonly destroyRef = inject(DestroyRef);

  readonly estados = ESTADOS_ENVIO;
  readonly capacidades = ESTADOS_CAPACIDAD;

  readonly q = signal('');
  readonly estado = signal('');
  readonly capacidad = signal('');
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly numeroPagina = signal(0);

  readonly pagina = signal<Pagina<Envio> | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly resaltado = signal<string | null>(null);
  readonly soloCliente = computed(() => esSoloCliente(this.auth.roles));

  /** Cada solicitud nueva cancela la anterior (switchMap), así una respuesta lenta no pisa a una más reciente. */
  private readonly consulta$ = new Subject<void>();
  readonly buscar$ = new Subject<void>();
  private sondeo: Subscription | null = null;

  readonly pillEstado = pillEstado;
  readonly pillCapacidad = pillCapacidad;
  readonly etiquetaCapacidad = etiquetaCapacidad;
  readonly fechaHora = fechaHora;
  readonly decimal = decimal;

  ngOnInit(): void {
    this.consulta$
      .pipe(
        switchMap(() => {
          this.cargando.set(true);
          this.detenerSondeo();
          return this.bff.listarEnvios(this.filtros()).pipe(catchError((err: unknown) => {
            this.error.set(interpretarError(err).mensaje);
            this.cargando.set(false);
            return EMPTY;
          }));
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (pagina) => this.mostrar(pagina),
        error: (err: unknown) => {
          this.error.set(interpretarError(err).mensaje);
          this.cargando.set(false);
        },
      });
    // El texto de búsqueda espera a que el usuario deje de escribir.
    this.buscar$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.aplicarFiltros());
    this.destroyRef.onDestroy(() => this.detenerSondeo());
    const resaltar = (history.state as Record<string, unknown> | null)?.['resaltar'];
    if (typeof resaltar === 'string') this.resaltado.set(resaltar);
    this.recargar();
  }

  filtros(): FiltrosEnvios {
    const f: FiltrosEnvios = { page: this.numeroPagina(), size: TAMANO_PAGINA };
    if (this.q().trim()) f.q = this.q().trim();
    if (this.estado()) f.status = this.estado();
    if (this.capacidad()) f.capacity = this.capacidad();
    if (this.desde()) f.from = `${this.desde()}T00:00:00Z`;
    if (this.hasta()) f.to = `${this.hasta()}T23:59:59Z`;
    return f;
  }

  aplicarFiltros(): void {
    this.numeroPagina.set(0);
    this.recargar();
  }

  irAPagina(n: number): void {
    this.numeroPagina.set(Math.max(0, n));
    this.recargar();
  }

  recargar(): void {
    this.error.set(null);
    this.consulta$.next();
  }

  reemplazar(actualizado: Envio): void {
    const actual = this.pagina();
    if (!actual) return;
    this.pagina.set({ ...actual, content: actual.content.map((e) => (e.id === actualizado.id ? actualizado : e)) });
    this.resaltado.set(actualizado.id);
    this.iniciarSondeoSiHaceFalta(this.pagina()?.content ?? []);
  }

  private mostrar(pagina: Pagina<Envio>): void {
    this.pagina.set(pagina);
    this.error.set(null);
    this.cargando.set(false);
    this.iniciarSondeoSiHaceFalta(pagina.content);
  }

  /** Si hay reservas PENDIENTE, se consulta cada 3 s hasta que se resuelvan, con un máximo de 30 s. */
  private iniciarSondeoSiHaceFalta(envios: Envio[]): void {
    this.detenerSondeo();
    if (!hayReservaPendiente(envios)) return;
    this.sondeo = sondear(() => this.bff.listarEnvios(this.filtros(), true), {
      cadaMs: 3000,
      maximoMs: 30000,
      hasta: (pagina) => !hayReservaPendiente(pagina.content),
    }).subscribe({ next: (pagina) => this.pagina.set(pagina), error: () => undefined });
  }

  private detenerSondeo(): void {
    this.sondeo?.unsubscribe();
    this.sondeo = null;
  }
}
