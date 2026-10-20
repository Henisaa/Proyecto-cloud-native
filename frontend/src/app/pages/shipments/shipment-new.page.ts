import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { CrearEnvio, ServicioResumen, ZONAS } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Etiquetas legibles de las cuatro zonas que acepta el backend. */
export const ETIQUETA_ZONA: Record<string, string> = {
  URBAN_CENTER: 'Centro urbano',
  URBAN_PERIPHERY: 'Periferia urbana',
  RURAL: 'Rural',
  INTERURBAN: 'Interurbano',
};

/** Texto opcional vacío -> null (el backend distingue "" de un valor informado). */
export function textoONulo(valor: string | null | undefined): string | null {
  const limpio = (valor ?? '').trim();
  return limpio.length > 0 ? limpio : null;
}

type Campo =
  | 'serviceId'
  | 'zone'
  | 'packagesCount'
  | 'weightKg'
  | 'volumeM3'
  | 'recipientName'
  | 'recipientEmail'
  | 'recipientPhone'
  | 'originAddress'
  | 'destinationAddress';

/** Crear envío (FE-05): las validaciones replican las del backend (largos, rangos y formato). */
@Component({
  selector: 'app-shipment-new',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto max-w-3xl space-y-6">
      <header class="border-b border-zinc-200/60 pb-6">
        <a routerLink="/shipments" class="text-xs font-semibold text-blue-700 hover:underline">← Volver a envíos</a>
        <h1 class="mt-2 text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Nuevo envío</h1>
        <p class="mt-1 text-xs text-zinc-500">Se valida la capacidad disponible en el catálogo. La reserva se hace al aceptar el envío.</p>
      </header>

      <form class="card grid gap-4 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="enviar()" novalidate>
        <div>
          <label class="label" for="n-servicio">Servicio</label>
          <select id="n-servicio" class="input" formControlName="serviceId" aria-describedby="e-servicio">
            <option [ngValue]="null" disabled>Seleccione…</option>
            @for (s of servicios(); track s.id) {
              <option [ngValue]="s.id">{{ s.code }} · {{ s.name }}</option>
            }
          </select>
          <p id="e-servicio" class="mt-1 text-[11px] text-rose-600">{{ error('serviceId') }}</p>
        </div>
        <div>
          <label class="label" for="n-zona">Zona</label>
          <select id="n-zona" class="input" formControlName="zone" aria-describedby="e-zona">
            @for (z of zonas; track z) { <option [value]="z">{{ etiquetaZona(z) }}</option> }
          </select>
          <p id="e-zona" class="mt-1 text-[11px] text-rose-600">{{ error('zone') }}</p>
        </div>

        <div>
          <label class="label" for="n-nombre">Destinatario</label>
          <input id="n-nombre" class="input" formControlName="recipientName" maxlength="120" autocomplete="off" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('recipientName') }}</p>
        </div>
        <div>
          <label class="label" for="n-email">Correo del destinatario (opcional)</label>
          <input id="n-email" class="input" type="email" formControlName="recipientEmail" maxlength="120" autocomplete="off" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('recipientEmail') }}</p>
        </div>
        <div>
          <label class="label" for="n-tel">Teléfono (opcional)</label>
          <input id="n-tel" class="input" formControlName="recipientPhone" maxlength="30" autocomplete="off" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('recipientPhone') }}</p>
        </div>
        <div>
          <label class="label" for="n-paquetes">Paquetes</label>
          <input id="n-paquetes" class="input" type="number" formControlName="packagesCount" min="1" max="99999" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('packagesCount') }}</p>
        </div>

        <div class="sm:col-span-2">
          <label class="label" for="n-origen">Dirección de origen</label>
          <input id="n-origen" class="input" formControlName="originAddress" maxlength="250" autocomplete="off" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('originAddress') }}</p>
        </div>
        <div class="sm:col-span-2">
          <label class="label" for="n-destino">Dirección de destino</label>
          <input id="n-destino" class="input" formControlName="destinationAddress" maxlength="250" autocomplete="off" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('destinationAddress') }}</p>
        </div>

        <div>
          <label class="label" for="n-peso">Peso (kg)</label>
          <input id="n-peso" class="input" type="number" step="0.01" formControlName="weightKg" min="0.01" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('weightKg') }}</p>
        </div>
        <div>
          <label class="label" for="n-volumen">Volumen (m³)</label>
          <input id="n-volumen" class="input" type="number" step="0.001" formControlName="volumeM3" min="0.001" />
          <p class="mt-1 text-[11px] text-rose-600">{{ error('volumeM3') }}</p>
        </div>

        <div class="flex justify-end gap-2 sm:col-span-2">
          <a routerLink="/shipments" class="btn-secondary">Cancelar</a>
          <button type="submit" class="btn-primary" [disabled]="enviando()">{{ enviando() ? 'Creando…' : 'Crear envío' }}</button>
        </div>
      </form>
    </div>
  `,
})
export class ShipmentNewPage implements OnInit {
  private readonly bff = inject(BffService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  readonly zonas = ZONAS;
  readonly servicios = signal<ServicioResumen[]>([]);
  readonly enviando = signal(false);
  private erroresServidor: Record<string, string> = {};

  readonly form = this.fb.group({
    serviceId: this.fb.control<number | null>(null, [Validators.required]),
    zone: this.fb.nonNullable.control('URBAN_CENTER', [Validators.required]),
    packagesCount: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1), Validators.max(99999)]),
    weightKg: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01), Validators.max(99999999.99)]),
    volumeM3: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001), Validators.max(9999999.999)]),
    recipientName: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(120)]),
    recipientEmail: this.fb.nonNullable.control('', [Validators.email, Validators.maxLength(120)]),
    recipientPhone: this.fb.nonNullable.control('', [Validators.maxLength(30)]),
    originAddress: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(250)]),
    destinationAddress: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(250)]),
  });

  ngOnInit(): void {
    // El servicio se elige entre los servicios activos del catálogo.
    this.bff.servicios().subscribe({
      next: (lista) => this.servicios.set(lista.filter((s) => s.active && s.status === 'ACTIVE')),
    });
    this.form.valueChanges.subscribe(() => (this.erroresServidor = {}));
  }

  etiquetaZona(zona: string): string {
    return ETIQUETA_ZONA[zona] ?? zona;
  }

  error(campo: Campo): string {
    if (this.erroresServidor[campo]) return this.erroresServidor[campo];
    const control = this.form.controls[campo];
    if (!control.touched && !control.dirty) return '';
    const e = control.errors;
    if (!e) return '';
    if (e['required']) return 'Campo obligatorio';
    if (e['email']) return 'Ingrese un correo válido';
    if (e['maxlength']) return `Máximo ${e['maxlength'].requiredLength} caracteres`;
    if (e['min']) return `Debe ser al menos ${e['min'].min}`;
    if (e['max']) return `No puede superar ${e['max'].max}`;
    return 'Valor inválido';
  }

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const cuerpo: CrearEnvio = {
      serviceId: v.serviceId as number,
      zone: v.zone,
      packagesCount: v.packagesCount,
      weightKg: v.weightKg as number,
      volumeM3: v.volumeM3 as number,
      recipientName: v.recipientName.trim(),
      recipientEmail: textoONulo(v.recipientEmail),
      recipientPhone: textoONulo(v.recipientPhone),
      originAddress: v.originAddress.trim(),
      destinationAddress: v.destinationAddress.trim(),
    };
    this.enviando.set(true);
    this.bff.crearEnvio(cuerpo).subscribe({
      next: (envio) => {
        this.toasts.exito(`Envío ${envio.trackingCode} creado.`);
        void this.router.navigate(['/shipments'], { state: { resaltar: envio.id } });
      },
      error: (err: unknown) => {
        // El mensaje general ya lo mostró el interceptor; aquí se marcan los campos que el backend rechazó.
        this.erroresServidor = interpretarError(err).campos ?? {};
        this.enviando.set(false);
      },
    });
  }
}
