import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';
import { MqPolitica } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { validarRetencion } from '../../shared/mq-validators';
import { VistaEstadoComponent } from '../../shared/vista-estado.component';

/** Retención y topología (FE-15): políticas vigentes, TTL y límite editables y "Aplicar" idempotente. */
@Component({
  selector: 'app-mq-retencion',
  standalone: true,
  imports: [FormsModule, VistaEstadoComponent],
  template: `
    <div class="space-y-6">
      <section aria-labelledby="titulo-politicas" class="space-y-2">
        <h2 id="titulo-politicas" class="text-sm font-bold text-zinc-800">Políticas vigentes</h2>
        <app-vista-estado [cargando]="cargando()" [error]="error()" [vacio]="politicas().length === 0" mensajeVacio="No hay políticas aplicadas. Use «Aplicar topología y retención»." (reintentar)="cargar()">
          <div class="card overflow-x-auto !p-0">
            <table class="w-full min-w-[640px] text-left text-xs">
              <caption class="sr-only">Políticas de retención</caption>
              <thead class="border-b border-zinc-100 text-[10px] uppercase tracking-wider text-zinc-400">
                <tr>
                  <th scope="col" class="px-4 py-3">Nombre</th>
                  <th scope="col" class="px-4 py-3">Patrón</th>
                  <th scope="col" class="px-4 py-3">Definición</th>
                </tr>
              </thead>
              <tbody>
                @for (p of politicas(); track p.name) {
                  <tr class="border-b border-zinc-50 align-top last:border-0">
                    <td class="px-4 py-3 font-mono font-semibold">{{ p.name }}</td>
                    <td class="px-4 py-3 font-mono text-[11px] break-all">{{ p.pattern }}</td>
                    <td class="px-4 py-3 font-mono text-[11px]">{{ definicion(p) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </app-vista-estado>
      </section>

      <form class="card grid gap-4 sm:grid-cols-3" (ngSubmit)="aplicar()" aria-label="Retención">
        <h2 class="text-sm font-bold text-zinc-800 sm:col-span-3">Retención y límites</h2>
        <div>
          <label class="label" for="r-cmd">TTL de comandos (ms)</label>
          <input id="r-cmd" class="input" name="cmd" type="number" min="1000" [ngModel]="comandos()" (ngModelChange)="comandos.set($event)" />
          <p class="mt-1 text-[10px] text-zinc-400">De 1 s a 30 días. Lo vencido pasa a la DLQ.</p>
        </div>
        <div>
          <label class="label" for="r-dlq">TTL de las DLQ (ms)</label>
          <input id="r-dlq" class="input" name="dlq" type="number" min="1000" [ngModel]="dlq()" (ngModelChange)="dlq.set($event)" />
          <p class="mt-1 text-[10px] text-zinc-400">De 1 s a 30 días. Las DLQ descartan lo más antiguo.</p>
        </div>
        <div>
          <label class="label" for="r-max">Máximo de mensajes por cola</label>
          <input id="r-max" class="input" name="max" type="number" min="1" max="1000000" [ngModel]="maximo()" (ngModelChange)="maximo.set($event)" />
          <p class="mt-1 text-[10px] text-zinc-400">Entre 1 y 1.000.000. En las colas de trabajo rechaza lo que no cabe.</p>
        </div>
        @if (errorForm()) { <p class="text-xs font-semibold text-rose-600 sm:col-span-3" role="alert">{{ errorForm() }}</p> }
        <div class="flex flex-wrap gap-2 sm:col-span-3">
          <button type="submit" class="btn-primary" [disabled]="enCurso()">Guardar retención</button>
          <button type="button" class="btn-secondary" [disabled]="enCurso()" (click)="topologia()">Aplicar topología y retención</button>
        </div>
        @if (resultado()) {
          <pre class="rounded-lg bg-zinc-50 p-3 text-[11px] text-zinc-600 sm:col-span-3" role="status">{{ resultado() }}</pre>
        }
      </form>
    </div>
  `,
})
export class MqRetencionComponent implements OnInit {
  private readonly bff = inject(BffService);
  private readonly toasts = inject(ToastService);

  readonly politicas = signal<MqPolitica[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly comandos = signal(86_400_000);
  readonly dlq = signal(604_800_000);
  readonly maximo = signal(10_000);
  readonly enCurso = signal(false);
  readonly errorForm = signal<string | null>(null);
  readonly resultado = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  definicion(p: MqPolitica): string {
    return JSON.stringify(p.definition);
  }

  cargar(): void {
    this.cargando.set(true);
    this.bff.mqPoliticas().subscribe({
      next: (lista) => {
        this.politicas.set(lista);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.error.set(interpretarError(err).mensaje);
        this.cargando.set(false);
      },
    });
  }

  aplicar(): void {
    const motivo = validarRetencion(Number(this.comandos()), Number(this.dlq()), Number(this.maximo()));
    if (motivo) {
      this.errorForm.set(motivo);
      return;
    }
    this.errorForm.set(null);
    this.enCurso.set(true);
    this.bff.aplicarRetencion({ commandTtlMs: Number(this.comandos()), dlqTtlMs: Number(this.dlq()), maxLength: Number(this.maximo()) }).subscribe({
      next: () => {
        this.enCurso.set(false);
        this.toasts.exito('Retención actualizada.');
        this.cargar();
      },
      error: (err: unknown) => {
        this.enCurso.set(false);
        this.errorForm.set(interpretarError(err).mensaje);
      },
    });
  }

  /** Declara de nuevo la topología base y las políticas: es idempotente, se puede repetir sin efecto. */
  topologia(): void {
    this.enCurso.set(true);
    this.bff.aplicarTopologia().subscribe({
      next: (r) => {
        this.enCurso.set(false);
        this.resultado.set(JSON.stringify(r, null, 2));
        this.toasts.exito('Topología y retención aplicadas.');
        this.cargar();
      },
      error: () => this.enCurso.set(false),
    });
  }
}
