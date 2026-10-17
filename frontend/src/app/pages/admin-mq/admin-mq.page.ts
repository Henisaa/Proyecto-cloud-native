import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { BffService, MqBindingRequest, MqExchangeRequest, MqQueueRequest } from '../../core/bff.service';

@Component({
  selector: 'app-admin-mq',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-6">
      <header class="flex flex-col justify-between gap-4 border-b border-zinc-200/60 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="text-xl font-bold tracking-tight text-zinc-900 sm:text-2xl">Administración RabbitMQ</h1>
          <p class="mt-1 text-sm text-zinc-500">Administra colas, exchanges y bindings. Los errores del servicio se muestran aquí.</p>
        </div>
        <button type="button" class="btn-secondary" (click)="cargarMetricas()" [disabled]="cargando">Actualizar métricas</button>
      </header>

      @if (error) { <p class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800" role="alert">{{ error }}</p> }
      @if (exito) { <p class="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status">{{ exito }}</p> }

      <section class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" aria-label="Métricas RabbitMQ">
        @for (item of metricas; track item.key) {
          <article class="card">
            <p class="label">{{ item.label }}</p>
            <p class="font-mono text-2xl font-bold text-zinc-900">{{ valor(item.key) }}</p>
          </article>
        }
      </section>

      <div class="grid gap-5 lg:grid-cols-2">
        <section class="card space-y-4">
          <h2 class="font-semibold text-zinc-900">Crear cola</h2>
          <form class="space-y-3" (ngSubmit)="crearCola()">
            <label class="block"><span class="label">Nombre</span><input class="input" name="nombreCola" [(ngModel)]="nombreCola" required maxlength="255" /></label>
            <button class="btn-primary" type="submit" [disabled]="cargando">Crear cola</button>
          </form>
          <form class="space-y-3 border-t border-zinc-100 pt-4" (ngSubmit)="eliminarCola()">
            <label class="block"><span class="label">Cola a eliminar</span><input class="input" name="colaEliminar" [(ngModel)]="colaEliminar" required maxlength="255" /></label>
            <label class="flex items-center gap-2 text-sm text-zinc-600"><input type="checkbox" name="forzarCola" [(ngModel)]="forzarCola" /> Forzar eliminación</label>
            <button class="btn-secondary" type="submit" [disabled]="cargando">Eliminar cola</button>
          </form>
        </section>

        <section class="card space-y-4">
          <h2 class="font-semibold text-zinc-900">Crear exchange</h2>
          <form class="space-y-3" (ngSubmit)="crearExchange()">
            <label class="block"><span class="label">Nombre</span><input class="input" name="nombreExchange" [(ngModel)]="nombreExchange" required maxlength="255" /></label>
            <label class="block"><span class="label">Tipo</span><select class="input" name="tipoExchange" [(ngModel)]="tipoExchange"><option value="direct">direct</option><option value="topic">topic</option><option value="fanout">fanout</option><option value="headers">headers</option></select></label>
            <button class="btn-primary" type="submit" [disabled]="cargando">Crear exchange</button>
          </form>
          <form class="space-y-3 border-t border-zinc-100 pt-4" (ngSubmit)="eliminarExchange()">
            <label class="block"><span class="label">Exchange a eliminar</span><input class="input" name="exchangeEliminar" [(ngModel)]="exchangeEliminar" required maxlength="255" /></label>
            <label class="flex items-center gap-2 text-sm text-zinc-600"><input type="checkbox" name="forzarExchange" [(ngModel)]="forzarExchange" /> Forzar eliminación</label>
            <button class="btn-secondary" type="submit" [disabled]="cargando">Eliminar exchange</button>
          </form>
        </section>

        <section class="card space-y-4 lg:col-span-2">
          <h2 class="font-semibold text-zinc-900">Bindings</h2>
          <form class="grid gap-3 sm:grid-cols-2" (ngSubmit)="crearBinding()">
            <label class="block"><span class="label">Exchange origen</span><input class="input" name="source" [(ngModel)]="source" required /></label>
            <label class="block"><span class="label">Cola destino</span><input class="input" name="destination" [(ngModel)]="destination" required /></label>
            <label class="block"><span class="label">Routing key</span><input class="input" name="routingKey" [(ngModel)]="routingKey" /></label>
            <div class="flex items-end"><button class="btn-primary" type="submit" [disabled]="cargando">Crear binding</button></div>
          </form>
          <form class="grid gap-3 border-t border-zinc-100 pt-4 sm:grid-cols-2" (ngSubmit)="eliminarBinding()">
            <label class="block"><span class="label">Exchange origen</span><input class="input" name="sourceEliminar" [(ngModel)]="sourceEliminar" required /></label>
            <label class="block"><span class="label">Destino</span><input class="input" name="destinationEliminar" [(ngModel)]="destinationEliminar" required /></label>
            <label class="block"><span class="label">Routing key</span><input class="input" name="routingKeyEliminar" [(ngModel)]="routingKeyEliminar" /></label>
            <div class="flex items-end"><button class="btn-secondary" type="submit" [disabled]="cargando">Eliminar binding</button></div>
          </form>
        </section>
      </div>
    </div>
  `,
})
export class AdminMqPage implements OnInit {
  private readonly bff = inject(BffService);

  readonly metricas = [
    { key: 'activeQueues', label: 'Colas activas' },
    { key: 'messagesReady', label: 'Mensajes pendientes' },
    { key: 'consumers', label: 'Consumidores' },
    { key: 'dlqMessages', label: 'Mensajes en DLQ' },
  ];
  estado: Record<string, unknown> | null = null;
  error = '';
  exito = '';
  cargando = false;
  nombreCola = '';
  colaEliminar = '';
  forzarCola = false;
  nombreExchange = '';
  tipoExchange: MqExchangeRequest['type'] = 'direct';
  exchangeEliminar = '';
  forzarExchange = false;
  source = '';
  destination = '';
  routingKey = '';
  sourceEliminar = '';
  destinationEliminar = '';
  routingKeyEliminar = '';

  ngOnInit(): void {
    this.cargarMetricas();
  }

  valor(key: string): number | string {
    const value = this.estado?.[key];
    return typeof value === 'number' ? value : '—';
  }

  cargarMetricas(): void {
    this.bff.mqMetrics().subscribe({ next: (value) => (this.estado = value) });
  }

  crearCola(): void {
    const solicitud: MqQueueRequest = { name: this.nombreCola.trim(), durable: true };
    this.ejecutar(this.bff.crearCola(solicitud), 'Cola creada.');
  }

  eliminarCola(): void {
    this.ejecutar(this.bff.eliminarCola(this.colaEliminar.trim(), this.forzarCola), 'Cola eliminada.');
  }

  crearExchange(): void {
    const solicitud: MqExchangeRequest = { name: this.nombreExchange.trim(), type: this.tipoExchange, durable: true };
    this.ejecutar(this.bff.crearExchange(solicitud), 'Exchange creado.');
  }

  eliminarExchange(): void {
    this.ejecutar(this.bff.eliminarExchange(this.exchangeEliminar.trim(), this.forzarExchange), 'Exchange eliminado.');
  }

  crearBinding(): void {
    const solicitud: MqBindingRequest = {
      source: this.source.trim(),
      destination: this.destination.trim(),
      destinationType: 'queue',
      routingKey: this.routingKey,
    };
    this.ejecutar(this.bff.crearBinding(solicitud), 'Binding creado.');
  }

  eliminarBinding(): void {
    this.ejecutar(
      this.bff.eliminarBinding(this.sourceEliminar.trim(), this.destinationEliminar.trim(), this.routingKeyEliminar),
      'Binding eliminado.',
    );
  }

  private ejecutar(request: Observable<unknown>, mensajeExito: string): void {
    this.error = '';
    this.exito = '';
    this.cargando = true;
    request.subscribe({
      next: () => {
        this.exito = mensajeExito;
        this.cargando = false;
        this.cargarMetricas();
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as { detail?: string; message?: string } | string | null;
        this.error = typeof body === 'string'
          ? this.detalle(body) || body
          : body?.detail || body?.message || error.message || 'No se pudo completar la operación.';
        this.cargando = false;
      },
    });
  }

  private detalle(body: string): string | undefined {
    try {
      return (JSON.parse(body) as { detail?: string }).detail;
    } catch {
      return undefined;
    }
  }
}
