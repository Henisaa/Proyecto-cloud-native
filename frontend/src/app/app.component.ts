import { Component, OnInit, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MsalBroadcastService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';
import { NavbarComponent } from './layout/navbar.component';
import { ToastsComponent } from './shared/toasts.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, ToastsComponent],
  template: `
    @if (esLogin()) {
      <router-outlet />
    } @else {
      <a href="#contenido" class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:p-2">
        Saltar al contenido
      </a>
      <app-navbar />
      <main id="contenido" class="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <router-outlet />
      </main>
    }
    <app-toasts />
  `,
})
export class AppComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly broadcast = inject(MsalBroadcastService);

  readonly esLogin = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => {
        const url = event.urlAfterRedirects;
        return url === '/' || url.startsWith('/?') || url.startsWith('/login');
      })
    ),
    { initialValue: this.router.url === '/' || this.router.url.startsWith('/login') }
  );

  ngOnInit(): void {
    this.auth.init();

    // Red de seguridad: si MSAL terminó la interacción y hay cuenta (volviendo de Microsoft),
    // navegamos al panel aunque la pantalla de login no haya alcanzado a reaccionar.
    this.broadcast.inProgress$
      .pipe(filter((status: InteractionStatus) => status === InteractionStatus.None))
      .subscribe(() => {
        const url = this.router.url;
        if (this.auth.account() && (url === '/' || url.startsWith('/?') || url.startsWith('/login'))) {
          void this.router.navigate(['/dashboard'], { replaceUrl: true });
        }
      });
  }
}
