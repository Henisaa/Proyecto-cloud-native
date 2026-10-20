import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { Capacidad, ServicioDetalle, ServicioResumen } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { pillServicio } from '../../shared/estado-ui';
import { decimal, moneda, numero } from '../../shared/format';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/** Valida la edición de una capacidad: el máximo diario no puede ser menor a lo ya reservado. */
export function validarMaximoDiario(maximo: number, reservados: number): string | null {
  if (!Number.isInteger(maximo) || maximo < 1) return 'El máximo diario debe ser un entero de al menos 1';
  if (maximo < reservados) return `El máximo diario (${maximo}) no puede ser menor a lo reservado (${reservados})`;
  return null;
}

/**
 * Catálogo (FE-08). Todos los roles con acceso ven los servicios; Admin y Despachador ven además las capacidades
 * y las tarifas, y solo Admin edita el máximo diario y el estado activo. "Actualizar" muestra cómo bajan los cupos
 * disponibles después de una reserva: es la evidencia visible de que el consumidor de catalog funciona.
 */
@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Catálogo de servicios</h1>
          <p class="mt-1 text-xs text-zinc-500">Servicios, capacidad de flota por zona y tarifas.</p>
        </div>
        <button type="button" class="btn-secondary" (click)="recargar()">Actualizar</button>
      </header>

      <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="servicios().length === 0" mensajeVacio="No hay servicios en el catálogo." (reintentar)="recargar()">
        <div class="card overflow-x-auto !p-0">
          <table class="w-full min-w-[720px] text-left text-xs">
            <caption class="sr-only">Servicios del catálogo</caption>
            <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
              <tr>
                <th scope="col" class="px-4 py-3">Código</th>
                <th scope="col" class="px-4 py-3">Nombre</th>
                <th scope="col" class="px-4 py-3">Categoría</th>
                <th scope="col" class="px-4 py-3">Estado</th>
                <th scope="col" class="px-4 py-3">Precio base</th>
                <th scope="col" class="px-4 py-3">Horas estimadas</th>
                <th scope="col" class="px-4 py-3"><span class="sr-only">Detalle</span></th>
              </tr>
            </thead>
            <tbody>
              @for (s of servicios(); track s.id) {
                <tr class="border-b border-zinc-50 last:border-0" [class]="s.id === seleccionado()?.id ? 'bg-blue-50/50' : ''">
                  <td class="px-4 py-3 font-mono font-semibold">{{ s.code }}</td>
                  <td class="px-4 py-3">{{ s.name }}</td>
                  <td class="px-4 py-3 text-zinc-600">{{ s.category }}</td>
                  <td class="px-4 py-3"><span class="badge" [class]="pillServicio(s.status)">{{ s.status }}</span></td>
                  <td class="px-4 py-3">{{ moneda(s.basePrice) }}</td>
                  <td class="px-4 py-3">{{ s.estimatedDeliveryHours }} h</td>
                  <td class="px-4 py-3 text-right">
                    @if (veCapacidades()) {
                      <button type="button" class="btn-secondary !px-3 !py-1" (click)="ver(s)">Ver detalle</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-vista-estado>

      @if (veCapacidades() && seleccionado(); as d) {
        <section class="card space-y-4" aria-labelledby="titulo-detalle">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 id="titulo-detalle" class="text-sm font-bold text-zinc-800">{{ d.code }} · {{ d.name }}</h2>
              <p class="text-xs text-zinc-500">
                Capacidad total {{ numero(d.totalDailyCapacity) }} · reservados {{ numero(d.totalBookedPackages) }} ·
                utilización {{ decimal(d.overallUtilizationPercentage) }} %
              </p>
            </div>
            <button type="button" class="btn-secondary" (click)="ver(d)">Actualizar cupos</button>
          </div>

          <app-vista-estado [cargando]="cargandoDetalle()" [error]="errorDetalle()" [vacio]="(d.capacities?.length ?? 0) === 0" mensajeVacio="Este servicio no tiene capacidades definidas." (reintentar)="ver(d)">
            <div class="overflow-x-auto">
              <table class="w-full min-w-[820px] text-left text-xs">
                <caption class="mb-2 text-left text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Capacidad por zona y vehículo</caption>
                <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                  <tr>
                    <th scope="col" class="px-3 py-2">Zona</th>
                    <th scope="col" class="px-3 py-2">Vehículo</th>
                    <th scope="col" class="px-3 py-2">Máximo diario</th>
                    <th scope="col" class="px-3 py-2">Reservados</th>
                    <th scope="col" class="px-3 py-2">Disponibles</th>
                    <th scope="col" class="px-3 py-2">Peso máx.</th>
                    <th scope="col" class="px-3 py-2">Volumen máx.</th>
                    <th scope="col" class="px-3 py-2">Activo</th>
                    @if (esAdmin()) { <th scope="col" class="px-3 py-2"><span class="sr-only">Editar</span></th> }
                  </tr>
                </thead>
                <tbody>
                  @for (c of d.capacities ?? []; track c.id) {
                    <tr class="border-b border-zinc-50 last:border-0">
                      <td class="px-3 py-2">{{ c.zone }}</td>
                      <td class="px-3 py-2">{{ c.vehicleType }}</td>
                      <td class="px-3 py-2">
                        @if (editando()?.id === c.id) {
                          <input class="input !w-24 !py-1" type="number" min="1" [attr.aria-label]="'Máximo diario de ' + c.zone + ' ' + c.vehicleType" [ngModel]="maximoEditado()" (ngModelChange)="maximoEditado.set($event)" />
                        } @else {
                          {{ numero(c.maxDailyPackages) }}
                        }
                      </td>
                      <td class="px-3 py-2">{{ numero(c.currentBookedPackages) }}</td>
                      <td class="px-3 py-2 font-semibold" [class]="c.availablePackages === 0 ? 'text-rose-600' : 'text-emerald-700'" data-testid="disponibles">{{ numero(c.availablePackages) }}</td>
                      <td class="px-3 py-2">{{ decimal(c.maxWeightKg) }} kg</td>
                      <td class="px-3 py-2">{{ decimal(c.maxVolumeM3) }} m³</td>
                      <td class="px-3 py-2">
                        @if (esAdmin()) {
                          <input type="checkbox" [checked]="c.active" [attr.aria-label]="'Activo: ' + c.zone + ' ' + c.vehicleType" [disabled]="guardando()" (change)="cambiarActivo(d, c, $any($event.target).checked)" />
                        } @else {
                          {{ c.active ? 'Sí' : 'No' }}
                        }
                      </td>
                      @if (esAdmin()) {
                        <td class="px-3 py-2 text-right">
                          @if (editando()?.id === c.id) {
                            <button type="button" class="btn-primary !px-3 !py-1" [disabled]="guardando()" (click)="guardarMaximo(d, c)">Guardar</button>
                            <button type="button" class="btn-secondary !px-3 !py-1" (click)="editando.set(null)">Cancelar</button>
                          } @else {
                            <button type="button" class="btn-secondary !px-3 !py-1" (click)="editar(c)">Editar máximo</button>
                          }
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (errorEdicion()) {
              <p class="mt-2 text-xs font-semibold text-rose-600" role="alert">{{ errorEdicion() }}</p>
            }
          </app-vista-estado>

          @if ((d.tariffs?.length ?? 0) > 0) {
            <div class="overflow-x-auto">
              <table class="w-full min-w-[620px] text-left text-xs">
                <caption class="mb-2 text-left text-[11px] font-semibold uppercase tracking-wider text-zinc-400">Tarifas por zona</caption>
                <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                  <tr>
                    <th scope="col" class="px-3 py-2">Origen</th>
                    <th scope="col" class="px-3 py-2">Destino</th>
                    <th scope="col" class="px-3 py-2">Tarifa base</th>
                    <th scope="col" class="px-3 py-2">Por km</th>
                    <th scope="col" class="px-3 py-2">Por kg</th>
                    <th scope="col" class="px-3 py-2">Mínima</th>
                  </tr>
                </thead>
                <tbody>
                  @for (t of d.tariffs ?? []; track t.id) {
                    <tr class="border-b border-zinc-50 last:border-0">
                      <td class="px-3 py-2">{{ t.originZone }}</td>
                      <td class="px-3 py-2">{{ t.destinationZone }}</td>
                      <td class="px-3 py-2">{{ moneda(t.baseFare) }}</td>
                      <td class="px-3 py-2">{{ moneda(t.perKmRate) }}</td>
                      <td class="px-3 py-2">{{ moneda(t.perKgRate) }}</td>
                      <td class="px-3 py-2">{{ moneda(t.minFare) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class CatalogPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly servicios = signal<ServicioResumen[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly seleccionado = signal<ServicioDetalle | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal<string | null>(null);

  readonly editando = signal<Capacidad | null>(null);
  readonly maximoEditado = signal(0);
  readonly guardando = signal(false);
  readonly errorEdicion = signal<string | null>(null);

  readonly veCapacidades = computed(() => this.auth.hasRole('Admin', 'Despachador'));
  readonly esAdmin = computed(() => this.auth.hasRole('Admin'));

  readonly pillServicio = pillServicio;
  readonly moneda = moneda;
  readonly numero = numero;
  readonly decimal = decimal;

  ngOnInit(): void {
    this.recargar();
  }

  recargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.bff.servicios().subscribe({
      next: (lista) => {
        this.servicios.set(lista);
        this.cargando.set(false);
        const actual = this.seleccionado();
        if (actual) this.ver(actual);
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }

  ver(servicio: { id: number }): void {
    this.cargandoDetalle.set(true);
    this.errorDetalle.set(null);
    this.bff.servicio(servicio.id).subscribe({
      next: (detalle) => {
        this.seleccionado.set(detalle);
        this.cargandoDetalle.set(false);
      },
      error: (err: unknown) => {
        this.errorDetalle.set(interpretarError(err).mensaje);
        this.cargandoDetalle.set(false);
      },
    });
  }

  editar(capacidad: Capacidad): void {
    this.errorEdicion.set(null);
    this.maximoEditado.set(capacidad.maxDailyPackages);
    this.editando.set(capacidad);
  }

  guardarMaximo(servicio: ServicioDetalle, capacidad: Capacidad): void {
    const motivo = validarMaximoDiario(Number(this.maximoEditado()), capacidad.currentBookedPackages);
    if (motivo) {
      this.errorEdicion.set(motivo);
      return;
    }
    this.guardar(servicio, capacidad, { maxDailyPackages: Number(this.maximoEditado()) });
  }

  cambiarActivo(servicio: ServicioDetalle, capacidad: Capacidad, activo: boolean): void {
    this.guardar(servicio, capacidad, { active: activo });
  }

  private guardar(servicio: ServicioDetalle, capacidad: Capacidad, cambios: { maxDailyPackages?: number; active?: boolean }): void {
    this.guardando.set(true);
    this.errorEdicion.set(null);
    this.bff.editarCapacidad(servicio.id, capacidad.id, cambios).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(null);
        this.toasts.exito('Capacidad actualizada.');
        this.ver(servicio);
      },
      error: (err: unknown) => {
        // El mensaje del backend (por ejemplo, un conflicto de versión) ya salió como aviso; se recarga el dato vigente.
        this.errorEdicion.set(interpretarError(err).mensaje);
        this.guardando.set(false);
        this.ver(servicio);
      },
    });
  }
}
