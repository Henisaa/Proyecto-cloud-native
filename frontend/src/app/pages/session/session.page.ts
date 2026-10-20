import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/auth.service';

@Component({
  standalone: true,
  template: `<section class="card space-y-4"><h1 class="text-xl font-bold">Diagnóstico de sesión</h1>
    <button class="btn-secondary" (click)="actualizar()">Actualizar</button>
    @if (auth.infoToken(); as t) {
      <dl class="space-y-3 break-all text-sm"><dt>Audiencia</dt><dd>{{ t.audiencia }}</dd>
        <dt>Emisor</dt><dd>{{ t.emisor }}</dd><dt>Vencimiento</dt><dd>{{ t.vence }}</dd>
        <dt>Roles</dt><dd>{{ t.roles.join(', ') || 'Sin roles' }}</dd>
        <dt>Scopes</dt><dd>{{ t.scopes.join(', ') || 'Sin scopes' }}</dd></dl>
    } @else { <p role="status">No se pudo leer la sesión. Actualice o vuelva a iniciar sesión.</p> }
  </section>`,
})
export class SessionPage implements OnInit {
  readonly auth = inject(AuthService);
  ngOnInit(): void { this.actualizar(); }
  actualizar(): void { void this.auth.inspeccionarToken(); }
}
