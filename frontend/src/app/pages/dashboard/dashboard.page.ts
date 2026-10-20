import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { Dashboard, EstadoServicios, MqMetricas, ResumenEnvios } from '../../core/models';
import { esSoloCliente } from '../../core/roles';
import { etiquetaCapacidad, pillCapacidad, pillEstado } from '../../shared/estado-ui';
import { decimal, fechaHora, numero } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

const ESTADOS_TARJETA = ['CREADO', 'ACEPTADO', 'EN_BODEGA', 'EN_RUTA', 'ENTREGADO', 'CANCELADO'];

/** Panel por rol (FE-03): cada bloque tiene su propio estado de carga y de error y se refresca cada 30 s. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, VistaEstadoComponent],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">{{ titulo() }}</h1>
          <p class="mt-1 text-xs text-zinc-500 sm:text-sm">
            Bienvenido, {{ auth.name }}.
            @if (ultimaConsulta()) {
              Actualizado a las {{ ultimaConsulta() }}.
            }
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2.5">
          <button type="button" class="btn-secondary" (click)="pausado.set(!pausado())" [attr.aria-pressed]="pausado()">
            {{ pausado() ? 'Reanudar actualización' : 'Pausar actualización' }}
          </button>
          <button type="button" class="btn-secondary" (click)="cargar()">Actualizar ahora</button>
          @if (auth.hasRole('Admin', 'Despachador', 'Cliente')) {
            <a routerLink="/shipments/new" class="btn-primary">+ Nuevo envío</a>
          }
        </div>
      </header>

      <!-- Envíos: todos los roles (un cliente solo ve los suyos) -->
      <section aria-labelledby="titulo-envios" class="space-y-3">
        <h2 id="titulo-envios" class="text-sm font-bold text-zinc-800">{{ soloCliente() ? 'Mis envíos' : 'Envíos' }}</h2>
        <app-vista-estado [cargando]="cargandoEnvios()" [error]="errorEnvios()" (reintentar)="cargar()">
          @if (resumen(); as r) {
            <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
              @for (estado of estados; track estado) {
                <div class="rounded-2xl border border-zinc-200/80 bg-white p-4 shadow-2xs">
                  <span class="badge" [class]="pillEstado(estado)">{{ estado }}</span>
                  <p class="mt-3 font-mono text-2xl font-bold text-zinc-900">{{ cuenta(r, estado) }}</p>
                </div>
              }
            </div>
            <div class="mt-3 flex flex-wrap items-center gap-2 text-xs text-zinc-500">
              <span class="font-semibold text-zinc-700">Capacidad:</span>
              @for (par of capacidades(); track par[0]) {
                <span class="badge" [class]="pillCapacidad(par[0])">{{ etiquetaCapacidad(par[0]) }} · {{ par[1] }}</span>
              }
              @if (capacidades().length === 0) {
                <span>sin reservas todavía</span>
              }
            </div>

            <div class="card mt-3 overflow-x-auto !p-0">
              <table class="w-full min-w-[640px] text-left text-xs">
                <caption class="sr-only">Últimos envíos</caption>
                <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                  <tr>
                    <th scope="col" class="px-4 py-3">Seguimiento</th>
                    <th scope="col" class="px-4 py-3">Estado</th>
                    <th scope="col" class="px-4 py-3">Capacidad</th>
                    <th scope="col" class="px-4 py-3">Destino</th>
                    <th scope="col" class="px-4 py-3">Actualizado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (e of r.recent; track e.id) {
                    <tr class="border-b border-zinc-50 last:border-0">
                      <td class="px-4 py-3 font-mono font-semibold">
                        <a [routerLink]="['/shipments', e.id]" class="text-blue-700 hover:underline">{{ e.trackingCode }}</a>
                      </td>
                      <td class="px-4 py-3"><span class="badge" [class]="pillEstado(e.status)">{{ e.status }}</span></td>
                      <td class="px-4 py-3"><span class="badge" [class]="pillCapacidad(e.capacityStatus)">{{ etiquetaCapacidad(e.capacityStatus) }}</span></td>
                      <td class="px-4 py-3 text-zinc-600">{{ e.destinationAddress }}</td>
                      <td class="px-4 py-3 text-zinc-500">{{ fechaHora(e.updatedAt) }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5" class="px-4 py-8 text-center text-zinc-400">Aún no hay envíos.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </app-vista-estado>
      </section>

      @if (auth.hasRole('Admin')) {
        <section aria-labelledby="titulo-kpis" class="space-y-3">
          <h2 id="titulo-kpis" class="text-sm font-bold text-zinc-800">Indicadores (últimas 24 horas)</h2>
          <app-vista-estado [cargando]="cargandoBase()" [error]="errorKpis()" (reintentar)="cargar()">
            @if (panel()?.reportOverview?.data; as k) {
              <div class="grid gap-3 sm:grid-cols-3">
                <div class="card"><p class="label">Envíos totales</p><p class="font-mono text-2xl font-bold">{{ numero(k.totalShipments) }}</p></div>
                <div class="card"><p class="label">Envíos por hora</p><p class="font-mono text-2xl font-bold">{{ decimal(k.shipmentsPerHour) }}</p></div>
                <div class="card"><p class="label">Lead time promedio</p><p class="font-mono text-2xl font-bold">{{ decimal(k.avgLeadTimeMinutes) }} min</p></div>
              </div>
            }
          </app-vista-estado>
        </section>

        <section aria-labelledby="titulo-broker" class="space-y-3">
          <h2 id="titulo-broker" class="text-sm font-bold text-zinc-800">Mensajería (RabbitMQ)</h2>
          <app-vista-estado [cargando]="cargandoBroker()" [error]="errorBroker()" (reintentar)="cargar()">
            @if (broker(); as b) {
              <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                <div class="card"><p class="label">Colas activas</p><p class="font-mono text-2xl font-bold">{{ numero(b.activeQueues) }}</p></div>
                <div class="card"><p class="label">Mensajes listos</p><p class="font-mono text-2xl font-bold">{{ numero(b.messagesReady) }}</p></div>
                <div class="card"><p class="label">Consumidores</p><p class="font-mono text-2xl font-bold">{{ numero(b.consumers) }}</p></div>
                <div class="card"><p class="label">En DLQ ahora</p><p class="font-mono text-2xl font-bold">{{ numero(b.dlqMessages) }}</p></div>
                <div class="card"><p class="label">Llegados a DLQ</p><p class="font-mono text-2xl font-bold text-rose-600">{{ numero(b.dlqArrivedTotal) }}</p></div>
              </div>
              <p class="text-[11px] text-zinc-400">
                Una DLQ que se consume casi siempre está vacía: el acumulado de llegados es la métrica de fallos.
                <a routerLink="/admin/mq" class="font-semibold text-blue-700 hover:underline">Administrar RabbitMQ</a>
              </p>
            }
          </app-vista-estado>
        </section>

        <section aria-labelledby="titulo-servicios" class="space-y-3">
          <h2 id="titulo-servicios" class="text-sm font-bold text-zinc-800">Servicios</h2>
          <app-vista-estado [cargando]="cargandoServicios()" [error]="errorServicios()" (reintentar)="cargar()">
            @if (servicios(); as s) {
              <div class="flex flex-wrap gap-2">
                @for (par of listaServicios(); track par[0]) {
                  <span class="badge" [class]="par[1] === 'UP' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-rose-200 bg-rose-50 text-rose-700'">
                    {{ par[0] }} · {{ par[1] }}
                  </span>
                }
              </div>
              @if (s.status !== 'UP') {
                <p class="text-xs text-rose-600">El BFF reporta un estado distinto de UP.</p>
              }
            }
          </app-vista-estado>
        </section>
      }

      @if (auth.hasRole('Admin', 'Auditor') && panel()?.auditOverview; as bloque) {
        <section aria-labelledby="titulo-audit" class="space-y-3">
          <h2 id="titulo-audit" class="text-sm font-bold text-zinc-800">Auditoría reciente</h2>
          @if (bloque.available) {
            <ul class="card divide-y divide-zinc-100 !p-0 text-xs">
              @for (ev of bloque.data ?? []; track $index) {
                <li class="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5">
                  <span class="font-semibold text-zinc-700">{{ ev.tipoEvento }}</span>
                  <span class="text-zinc-500">{{ ev.usuarioResponsable ?? 'sistema' }} · {{ fechaHora(ev.fechaEvento) }}</span>
                </li>
              } @empty {
                <li class="px-4 py-6 text-center text-zinc-400">Sin eventos recientes.</li>
              }
            </ul>
          } @else {
            <p class="rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-700">
              La auditoría (Kafka) no está disponible. La línea de tiempo de cada envío sigue mostrando los eventos de RabbitMQ.
            </p>
          }
        </section>
      }
    </div>
  `,
})
export class DashboardPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly bff = inject(BffService);
  private readonly destroyRef = inject(DestroyRef);

  readonly estados = ESTADOS_TARJETA;
  readonly pausado = signal(false);
  readonly ultimaConsulta = signal<string | null>(null);

  readonly panel = signal<Dashboard | null>(null);
  readonly cargandoBase = signal(true);
  readonly errorBase = signal<string | null>(null);

  readonly broker = signal<MqMetricas | null>(null);
  readonly cargandoBroker = signal(true);
  readonly errorBroker = signal<string | null>(null);

  readonly servicios = signal<EstadoServicios | null>(null);
  readonly cargandoServicios = signal(true);
  readonly errorServicios = signal<string | null>(null);

  readonly soloCliente = computed(() => esSoloCliente(this.auth.roles));
  readonly resumen = computed<ResumenEnvios | null>(() => this.panel()?.shipmentsOverview?.data ?? null);
  readonly cargandoEnvios = computed(() => this.cargandoBase() && !this.panel());
  readonly errorEnvios = computed(() => {
    const bloque = this.panel()?.shipmentsOverview;
    if (bloque && !bloque.available) return bloque.message ?? 'Los envíos no están disponibles';
    return this.errorBase();
  });
  readonly errorKpis = computed(() => {
    const bloque = this.panel()?.reportOverview;
    return bloque && !bloque.available ? 'Los reportes no están disponibles (la EP1 puede estar apagada).' : null;
  });
  readonly capacidades = computed<[string, number][]>(() =>
    Object.entries(this.resumen()?.byCapacity ?? {}).filter(([clave, valor]) => clave !== 'NO_APLICA' && valor > 0)
  );
  readonly listaServicios = computed<[string, string][]>(() => Object.entries(this.servicios()?.services ?? {}));

  readonly titulo = computed(() => {
    if (this.auth.hasRole('Admin')) return 'Panel de administración';
    if (this.auth.hasRole('Despachador')) return 'Panel de operación';
    if (this.auth.hasRole('Auditor')) return 'Panel de auditoría';
    return 'Mis envíos';
  });

  cuenta(resumen: ResumenEnvios, estado: string): string {
    return numero(resumen.byStatus[estado] ?? 0);
  }

  readonly pillEstado = pillEstado;
  readonly pillCapacidad = pillCapacidad;
  readonly etiquetaCapacidad = etiquetaCapacidad;
  readonly fechaHora = fechaHora;
  readonly numero = numero;
  readonly decimal = decimal;

  ngOnInit(): void {
    // Cada 30 s (o al pulsar "Actualizar ahora"), salvo que el usuario haya pausado la actualización.
    timer(0, 30_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.pausado()) this.cargar();
      });
  }

  /** Carga cada bloque por separado: un servicio caído solo deja ese bloque en error. */
  cargar(): void {
    this.bff.dashboard().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (datos) => {
        this.panel.set(datos);
        this.errorBase.set(null);
        this.cargandoBase.set(false);
        this.ultimaConsulta.set(new Date().toLocaleTimeString('es-CL'));
      },
      error: (err: unknown) => {
        this.errorBase.set(interpretarError(err).mensaje);
        this.cargandoBase.set(false);
      },
    });

    if (this.auth.hasRole('Admin')) {
      this.bff.mqMetricas(true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (m) => {
          this.broker.set(m);
          this.errorBroker.set(null);
          this.cargandoBroker.set(false);
        },
        error: (err: unknown) => {
          this.errorBroker.set(interpretarError(err).mensaje);
          this.cargandoBroker.set(false);
        },
      });
      this.bff.estadoServicios().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (s) => {
          this.servicios.set(s);
          this.errorServicios.set(null);
          this.cargandoServicios.set(false);
        },
        error: (err: unknown) => {
          this.errorServicios.set(interpretarError(err).mensaje);
          this.cargandoServicios.set(false);
        },
      });
    }
  }
}

