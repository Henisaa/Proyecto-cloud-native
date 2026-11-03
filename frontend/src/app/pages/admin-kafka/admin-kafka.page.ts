import { Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BffService } from '../../core/bff.service';
import { interpretarError } from '../../core/http-error';

@Component({ standalone: true, imports: [JsonPipe, FormsModule], template: `
  <section class="card space-y-4"><h1 class="text-xl font-bold">Kafka (EP1)</h1>
    <button class="btn-secondary" (click)="topics()">Consultar tópicos</button>
    <form (ngSubmit)="consultarLag()"><label class="label" for="grupo">Grupo de consumo</label>
      <input id="grupo" name="grupo" class="input" [(ngModel)]="grupo" required />
      <button class="btn-primary mt-2" [disabled]="!grupo.trim()">Consultar retraso</button></form>
    @if (error()) { <p role="alert">{{ error() }}</p> }
    @if (datos() === null) {
    <p class="text-xs text-zinc-500">Pulsa «Consultar tópicos» o escribe un grupo para ver su retraso.</p>
  } @else {
    <pre class="overflow-x-auto text-xs" aria-live="polite">{{ datos() | json }}</pre>
  }
  </section>` })
export class AdminKafkaPage {
  private readonly bff = inject(BffService);
  grupo = '';
  readonly datos = signal<unknown>(null);
  readonly error = signal('');
  topics(): void { this.error.set(''); this.bff.kafkaTopics().subscribe({ next: x => this.datos.set(x), error: e => this.error.set(interpretarError(e).mensaje) }); }
  consultarLag(): void { if (!this.grupo.trim()) return; this.error.set(''); this.bff.kafkaLag(this.grupo.trim()).subscribe({ next: x => this.datos.set(x), error: e => this.error.set(interpretarError(e).mensaje) }); }
}
