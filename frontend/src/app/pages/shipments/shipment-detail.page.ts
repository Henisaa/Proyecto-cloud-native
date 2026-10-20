import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { Envio, LineaTiempo } from '../../core/models';
import { sondear } from '../../core/polling';
import { explicacionCapacidad, etiquetaCapacidad, pillCapacidad, pillEstado } from '../../shared/estado-ui';
import { decimal, fechaHora, moneda } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';
import { AccionesEnvioComponent } from './acciones-envio.component';

/** Detalle de un envío (FE-06 y FE-07): datos, capacidad explicada, acciones y línea de tiempo unificada. */
@Component({
  selector: 'app-shipment-detail',
  standalone: true,
  imports: [RouterLink, VistaEstadoComponent, AccionesEnvioComponent],
  template: `
    <div class="space-y-6">
      <header class="border-b border-zinc-200/60 pb-6">
        <a routerLink="/shipments" class="text-xs font-semibold text-blue-700 hover:underline">← Volver a envíos</a>
      </header>

      <app-vista-estado [cargando]="cargando()" [error]="error()" (reintentar)="cargar()">
        @if (envio(); as e) {
          <section class="card space-y-4" aria-labelledby="titulo-envio">
            <div class="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
              <div>
                <h1 id="titulo-envio" class="font-mono text-xl font-bold text-zinc-900">{{ e.trackingCode }}</h1>
                <div class="mt-2 flex flex-wrap items-center gap-2">
                  <span class="badge" [class]="pillEstado(e.status)">{{ e.status }}</span>
                  <span class="badge" [class]="pillCapacidad(e.capacityStatus)" data-testid="capacidad">{{ etiquetaCapacidad(e.capacityStatus) }}</span>
                </div>
              </div>
              <app-acciones-envio [envio]="e" (actualizado)="alActualizar($event)" />
            </div>

            <!-- Región aria-live: anuncia el resultado asíncrono de la reserva sin que el usuario recargue. -->
            <p class="rounded-xl border p-3 text-xs" [class]="pillCapacidad(e.capacityStatus)" role="status" aria-live="polite">
              <strong>Capacidad:</strong> {{ explicacion(e.capacityStatus) }}
              @if (sondeando()) { <span class="ml-1 font-semibold">Consultando…</span> }
            </p>

            <dl class="grid gap-x-6 gap-y-3 text-xs sm:grid-cols-2 lg:grid-cols-3">
              <div><dt class="label">Destinatario</dt><dd>{{ e.recipientName }}</dd></div>
              <div><dt class="label">Correo</dt><dd>{{ e.recipientEmail ?? '—' }}</dd></div>
              <div><dt class="label">Teléfono</dt><dd>{{ e.recipientPhone ?? '—' }}</dd></div>
              <div><dt class="label">Origen</dt><dd>{{ e.originAddress }}</dd></div>
              <div><dt class="label">Destino</dt><dd>{{ e.destinationAddress }}</dd></div>
              <div><dt class="label">Servicio</dt><dd>{{ e.serviceCode ?? ('#' + e.serviceId) }}</dd></div>
              <div><dt class="label">Zona</dt><dd>{{ e.zone ?? '—' }}</dd></div>
              <div><dt class="label">Vehículo asignado</dt><dd>{{ e.vehicleType ?? '—' }}</dd></div>
              <div><dt class="label">Carga</dt><dd>{{ e.packagesCount }} paquete(s) · {{ decimal(e.weightKg) }} kg · {{ decimal(e.volumeM3) }} m³</dd></div>
              <div><dt class="label">Precio</dt><dd>{{ moneda(e.price) }}</dd></div>
              <div><dt class="label">Creado</dt><dd>{{ fechaHora(e.createdAt) }}</dd></div>
              <div><dt class="label">Actualizado</dt><dd>{{ fechaHora(e.updatedAt) }}</dd></div>
            </dl>
          </section>

          @if (veLineaDeTiempo()) {
            <section class="card" aria-labelledby="titulo-linea">
              <h2 id="titulo-linea" class="text-sm font-bold text-zinc-800">Línea de tiempo</h2>
              <app-vista-estado [cargando]="cargandoLinea()" [error]="errorLinea()" [vacio]="(linea()?.events?.length ?? 0) === 0" mensajeVacio="Aún no hay eventos." (reintentar)="cargarLinea()">
                @if (linea(); as l) {
                  @if (!l.kafkaAvailable) {
                    <p class="mb-3 rounded-lg border border-amber-200 bg-amber-50 p-2 text-[11px] text-amber-700">
                      La auditoría de Kafka no está disponible: se muestran solo los eventos de RabbitMQ.
                    </p>
                  }
                  <ol class="relative space-y-4 border-l border-zinc-200 pl-5">
                    @for (ev of l.events; track $index) {
                      <li class="relative text-xs">
                        <span class="absolute -left-[26px] top-1 h-2.5 w-2.5 rounded-full" [class]="ev.source === 'KAFKA' ? 'bg-violet-500' : 'bg-blue-500'"></span>
                        <p class="font-semibold text-zinc-800">{{ ev.eventType ?? 'evento' }}
                          <span class="ml-1 rounded bg-zinc-100 px-1.5 py-0.5 text-[10px] font-medium text-zinc-500">{{ ev.source }}</span>
                        </p>
                        <p class="text-zinc-500">
                          {{ fechaHora(ev.occurredAt) }} · {{ ev.actor ?? 'sistema' }}
                          @if (ev.routingKey) { · <span class="font-mono">{{ ev.routingKey }}</span> }
                        </p>
                        @if (ev.detail) { <p class="mt-0.5 break-words text-zinc-500">{{ ev.detail }}</p> }
                      </li>
                    }
                  </ol>
                }
              </app-vista-estado>
            </section>
          }
        }
      </app-vista-estado>
    </div>
  `,
})
export class ShipmentDetailPage implements OnInit {
  readonly id = input.required<string>();

  private readonly bff = inject(BffService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly envio = signal<Envio | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly sondeando = signal(false);

  readonly linea = signal<LineaTiempo | null>(null);
  readonly cargandoLinea = signal(false);
  readonly errorLinea = signal<string | null>(null);
  readonly veLineaDeTiempo = computed(() => this.auth.hasRole('Admin', 'Despachador', 'Auditor'));

  private sondeo: Subscription | null = null;

  readonly pillEstado = pillEstado;
  readonly pillCapacidad = pillCapacidad;
  readonly etiquetaCapacidad = etiquetaCapacidad;
  readonly explicacion = explicacionCapacidad;
  readonly fechaHora = fechaHora;
  readonly decimal = decimal;
  readonly moneda = moneda;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.detenerSondeo());
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.bff.obtenerEnvio(this.id()).subscribe({
      next: (envio) => {
        this.envio.set(envio);
        this.cargando.set(false);
        this.iniciarSondeoSiPendiente(envio);
        if (this.veLineaDeTiempo()) this.cargarLinea();
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }

  cargarLinea(): void {
    this.cargandoLinea.set(true);
    this.errorLinea.set(null);
    this.bff.lineaDeTiempo(this.id()).subscribe({
      next: (linea) => {
        this.linea.set(linea);
        this.cargandoLinea.set(false);
      },
      error: (err: unknown) => {
        this.errorLinea.set(interpretarError(err).mensaje);
        this.cargandoLinea.set(false);
      },
    });
  }

  alActualizar(envio: Envio): void {
    this.envio.set(envio);
    this.iniciarSondeoSiPendiente(envio);
    if (this.veLineaDeTiempo()) this.cargarLinea();
  }

  /** Tras aceptar se muestra "Reserva en proceso" y se consulta cada 3 s hasta RESERVADA o RECHAZADA (máximo 30 s). */
  private iniciarSondeoSiPendiente(envio: Envio): void {
    this.detenerSondeo();
    if (envio.capacityStatus !== 'PENDIENTE' && envio.capacityStatus !== 'LIBERACION_PENDIENTE') return;
    this.sondeando.set(true);
    this.sondeo = sondear(() => this.bff.obtenerEnvio(this.id(), true), {
      cadaMs: 3000,
      maximoMs: 30000,
      hasta: (e) => e.capacityStatus !== 'PENDIENTE' && e.capacityStatus !== 'LIBERACION_PENDIENTE',
    }).subscribe({
      next: (actualizado) => {
        this.envio.set(actualizado);
        if (actualizado.capacityStatus !== 'PENDIENTE' && actualizado.capacityStatus !== 'LIBERACION_PENDIENTE') {
          this.sondeando.set(false);
          if (this.veLineaDeTiempo()) this.cargarLinea();
        }
      },
      error: () => this.sondeando.set(false),
      complete: () => this.sondeando.set(false),
    });
  }

  private detenerSondeo(): void {
    this.sondeo?.unsubscribe();
    this.sondeo = null;
    this.sondeando.set(false);
  }
}
