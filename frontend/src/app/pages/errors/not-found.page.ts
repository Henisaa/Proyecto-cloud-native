import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="mx-auto mt-16 max-w-md rounded-2xl border border-zinc-200 bg-white p-8 text-center shadow-2xs">
      <p class="font-mono text-4xl font-bold text-zinc-300">404</p>
      <h1 class="mt-3 text-lg font-bold text-zinc-900">Página no encontrada</h1>
      <p class="mt-2 text-sm text-zinc-500">La dirección no existe o fue movida.</p>
      <a routerLink="/dashboard" class="btn-primary mt-6">Ir al panel</a>
    </section>
  `,
})
export class NotFoundPage {}
