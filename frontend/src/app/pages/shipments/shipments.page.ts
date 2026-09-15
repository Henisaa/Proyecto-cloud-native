import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService, CrearEnvio, Envio } from '../../core/bff.service';
import { AuthService } from '../../core/auth.service';
import { FadeInDirective } from '../../shared/fade-in.directive';
import { pillEstado, puntoEstado } from '../../shared/estado-ui';

const TRANSICIONES: Record<string, string[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_BODEGA', 'CANCELADO'],
  EN_BODEGA: ['EN_RUTA', 'CANCELADO'],
  EN_RUTA: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
};

const ZONAS = ['URBAN_CENTER', 'URBAN_PERIPHERY', 'RURAL', 'INTERURBAN'];

const FLUJO = ['CREADO', 'ACEPTADO', 'EN_BODEGA', 'EN_RUTA', 'ENTREGADO'];

@Component({
  selector: 'app-shipments',
  standalone: true,
  imports: [FormsModule, FadeInDirective],
  template: `
    <div class="space-y-6" appFadeIn>
      <!-- HEADER -->
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Gestión de envíos</h1>
          <div class="mt-2 flex flex-wrap items-center gap-1.5">
            @for (estado of flujo; track estado; let ultimo = $last) {
              <span class="rounded border px-1.5 py-0.5 text-[10px] font-medium" [class]="chipFlujo(estado)">
                {{ estado }}
              </span>
              @if (!ultimo) {
                <span class="text-zinc-300">→</span>
              }
            }
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <div class="relative">
            <select
              class="cursor-pointer appearance-none rounded-full border border-zinc-300 bg-white py-2 pl-4 pr-9 text-xs font-medium text-zinc-700 shadow-2xs transition-colors hover:border-zinc-400 focus:border-zinc-900 focus:outline-none"
              [(ngModel)]="filtro"
              (ngModelChange)="cargar()"
            >
              <option value="">Todos los estados</option>
              @for (estado of estados; track estado) {
                <option [value]="estado">{{ estado }}</option>
              }
            </select>
            <div class="pointer-events-none absolute inset-y-0 right-3 flex items-center text-zinc-400">
              <svg class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
              </svg>
            </div>
          </div>

          <button type="button" class="btn-primary" (click)="abrirFormulario()">+ Nuevo envío</button>
        </div>
      </header>

      @if (mensaje) {
        <div class="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-medium text-emerald-700">
          {{ mensaje }}
        </div>
      }
      @if (error) {
        <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-medium text-red-700">
          {{ error }}
        </div>
      }

      <!-- FORMULARIO -->
      @if (mostrarFormulario) {
        <div class="rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-2xs">
          <h2 class="mb-4 text-xs font-semibold uppercase tracking-wider text-zinc-500">Crear envío</h2>
          <div class="grid gap-4 md:grid-cols-3">
            <label>
              <span class="label">Servicio (ID catálogo)</span>
              <input class="input" type="number" [(ngModel)]="nuevo.serviceId" />
            </label>
            <label>
              <span class="label">Zona</span>
              <select class="input" [(ngModel)]="nuevo.zone">
                @for (z of zonas; track z) {
                  <option [value]="z">{{ z }}</option>
                }
              </select>
            </label>
            <label>
              <span class="label">Paquetes</span>
              <input class="input" type="number" min="1" [(ngModel)]="nuevo.packagesCount" />
            </label>
            <label>
              <span class="label">Peso (kg)</span>
              <input class="input" type="number" step="0.1" [(ngModel)]="nuevo.weightKg" />
            </label>
            <label>
              <span class="label">Volumen (m³)</span>
              <input class="input" type="number" step="0.001" [(ngModel)]="nuevo.volumeM3" />
            </label>
            <label>
              <span class="label">Destinatario</span>
              <input class="input" [(ngModel)]="nuevo.recipientName" />
            </label>
            <label>
              <span class="label">Correo destinatario</span>
              <input class="input" type="email" [(ngModel)]="nuevo.recipientEmail" />
            </label>
            <label>
              <span class="label">Origen</span>
              <input class="input" [(ngModel)]="nuevo.originAddress" />
            </label>
            <label>
              <span class="label">Destino</span>
              <input class="input" [(ngModel)]="nuevo.destinationAddress" />
            </label>
          </div>
          <div class="mt-4 flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="mostrarFormulario = false">Cancelar</button>
            <button type="button" class="btn-primary" [disabled]="creando" (click)="crear()">
              {{ creando ? 'Creando…' : 'Crear envío' }}
            </button>
          </div>
        </div>
      }

      <!-- TABLA -->
      <div class="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-2xs">
        <div class="overflow-x-auto">
          <table class="min-w-full border-collapse text-left">
            <thead>
              <tr class="border-b border-zinc-100 bg-zinc-50/50 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                <th class="px-4 py-3 sm:px-6">Tracking</th>
                <th class="px-4 py-3">Servicio</th>
                <th class="px-4 py-3">Estado</th>
                <th class="px-4 py-3">Destinatario</th>
                <th class="px-4 py-3">Destino</th>
                <th class="px-4 py-3">Carga</th>
                <th class="px-4 py-3 text-right sm:px-6">Actualizar estado</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-100 text-xs">
              @if (cargando) {
                <tr>
                  <td class="px-4 py-6 text-zinc-400" colspan="7">Cargando envíos…</td>
                </tr>
              }

              @for (envio of envios; track envio.id) {
                <tr class="transition-colors hover:bg-zinc-50/70">
                  <td class="px-4 py-3.5 sm:px-6">
                    <span class="cursor-pointer font-mono font-semibold" [class]="envio.status === 'EN_RUTA' ? 'text-blue-600' : 'text-zinc-900'">
                      {{ envio.trackingCode }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="rounded border border-zinc-200/60 bg-zinc-100/70 px-2 py-0.5 font-mono text-[11px] text-zinc-500">
                      {{ envio.serviceCode ?? envio.serviceId }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <span class="badge" [class]="pill(envio.status)">
                      <span class="h-1.5 w-1.5 rounded-full" [class]="punto(envio.status)"></span>
                      {{ envio.status }}
                    </span>
                  </td>
                  <td class="px-4 py-3.5">
                    <p class="font-medium text-zinc-800">{{ envio.recipientName }}</p>
                    <p class="font-mono text-[11px] text-zinc-400">{{ envio.recipientEmail }}</p>
                  </td>
                  <td class="max-w-[220px] px-4 py-3.5">
                    <p class="truncate text-zinc-600" [title]="envio.destinationAddress">{{ envio.destinationAddress }}</p>
                  </td>
                  <td class="px-4 py-3.5 font-mono text-[11px] text-zinc-500">
                    {{ envio.weightKg }} kg · {{ envio.volumeM3 }} m³
                  </td>
                  <td class="px-4 py-3.5 text-right sm:px-6">
                    @if (siguientes(envio.status).length > 0) {
                      <div class="inline-flex items-center gap-1.5">
                        <select
                          class="cursor-pointer rounded-lg border border-zinc-200 bg-white px-2 py-1 text-[11px] text-zinc-700 focus:border-zinc-900 focus:outline-none"
                          [(ngModel)]="seleccion[envio.id]"
                        >
                          <option [ngValue]="null">Avanzar...</option>
                          @for (s of siguientes(envio.status); track s) {
                            <option [value]="s">{{ s }}</option>
                          }
                        </select>
                        <button
                          type="button"
                          class="cursor-pointer rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-1 text-[11px] font-semibold text-zinc-700 transition-all hover:border-zinc-900 hover:bg-zinc-900 hover:text-white active:scale-95 disabled:opacity-40"
                          [disabled]="!seleccion[envio.id]"
                          (click)="cambiarEstado(envio)"
                        >
                          Aplicar
                        </button>
                      </div>
                    } @else {
                      <span class="text-[11px] italic text-zinc-400">Estado final</span>
                    }
                  </td>
                </tr>
              }

              @if (!cargando && envios.length === 0) {
                <tr>
                  <td class="px-4 py-6 text-zinc-400" colspan="7">No hay envíos para mostrar.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between border-t border-zinc-100 px-4 py-3 text-[11px] text-zinc-400 sm:px-6">
          <span>Mostrando {{ envios.length }} envío(s){{ filtro ? ' en estado ' + filtro : '' }}</span>
          <span class="font-mono">Página 1 de 1</span>
        </div>
      </div>
    </div>
  `,
})
export class ShipmentsPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly bff = inject(BffService);

  readonly estados = ['CREADO', 'ACEPTADO', 'EN_BODEGA', 'EN_RUTA', 'ENTREGADO', 'CANCELADO'];
  readonly zonas = ZONAS;
  readonly flujo = FLUJO;

  envios: Envio[] = [];
  filtro = '';
  cargando = true;
  creando = false;
  mostrarFormulario = false;
  mensaje = '';
  error = '';
  seleccion: Record<string, string | null> = {};

  nuevo: CrearEnvio = this.formularioVacio();

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.bff.listarEnvios(this.filtro || undefined).subscribe({
      next: (envios) => {
        this.envios = envios;
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.mensajeDeError(err);
      },
    });
  }

  abrirFormulario(): void {
    this.nuevo = this.formularioVacio();
    this.mostrarFormulario = true;
    this.mensaje = '';
    this.error = '';
  }

  crear(): void {
    this.creando = true;
    this.error = '';
    this.bff.crearEnvio(this.nuevo).subscribe({
      next: (envio) => {
        this.creando = false;
        this.mostrarFormulario = false;
        this.mensaje = `Envío ${envio.trackingCode} creado correctamente`;
        this.cargar();
      },
      error: (err) => {
        this.creando = false;
        this.error = this.mensajeDeError(err);
      },
    });
  }

  cambiarEstado(envio: Envio): void {
    const nuevoEstado = this.seleccion[envio.id];
    if (!nuevoEstado) return;
    this.error = '';
    this.mensaje = '';
    this.bff.cambiarEstado(envio.id, nuevoEstado).subscribe({
      next: (actualizado) => {
        this.mensaje = `Envío ${actualizado.trackingCode} ahora está en estado ${actualizado.status}`;
        this.seleccion[envio.id] = null;
        this.cargar();
      },
      error: (err) => (this.error = this.mensajeDeError(err)),
    });
  }

  siguientes(status: string): string[] {
    return TRANSICIONES[status] ?? [];
  }

  chipFlujo(estado: string): string {
    switch (estado) {
      case 'CREADO':
        return 'border-zinc-200 bg-zinc-100 text-zinc-600';
      case 'ACEPTADO':
        return 'border-indigo-200 bg-indigo-50 text-indigo-700';
      case 'EN_BODEGA':
        return 'border-amber-200 bg-amber-50 text-amber-700';
      case 'EN_RUTA':
        return 'border-blue-200 bg-blue-50 text-blue-700';
      default:
        return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    }
  }

  pill(status: string): string {
    return pillEstado(status);
  }

  punto(status: string): string {
    return puntoEstado(status);
  }

  private formularioVacio(): CrearEnvio {
    return {
      serviceId: 1,
      zone: 'URBAN_CENTER',
      packagesCount: 1,
      weightKg: 1,
      volumeM3: 0.01,
      recipientName: '',
      recipientEmail: '',
      recipientPhone: '',
      originAddress: '',
      destinationAddress: '',
    };
  }

  private mensajeDeError(err: unknown): string {
    const e = err as { error?: { message?: string }; message?: string; status?: number };
    if (e?.status === 401) {
      return this.auth.hasSession()
        ? 'No autorizado (401): tu token fue rechazado por el backend. Revisa el valor "aud" que aparece en el Dashboard.'
        : 'No autorizado (401): no hay token adjunto. Vuelve a iniciar sesión.';
    }
    if (e?.status === 403) return 'Tu usuario no tiene el rol necesario para esta acción (403).';
    if (e?.status === 404) return 'La ruta de envíos no está disponible en el BFF (404).';
    if (e?.status === 409) return e?.error?.message ?? 'Conflicto: sin capacidad disponible o transición inválida (409).';
    return e?.error?.message ?? e?.message ?? 'Error inesperado al llamar al BFF.';
  }
}
