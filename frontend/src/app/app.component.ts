import { Component, OnInit, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MsalBroadcastService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';
import { NavbarComponent } from './layout/navbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    @if (esLogin()) {
      <router-outlet />
    } @else {
      <app-navbar />
      <main class="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <router-outlet />
      </main>
    }
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
        return url === '/' || url.startsWith('/login');
      })
    ),
    { initialValue: this.router.url === '/' || this.router.url.startsWith('/login') }
  );

  ngOnInit(): void {
    this.auth.init();

    // Red de seguridad: si MSAL terminó la interacción y hay cuenta (volviendo de Microsoft),
    // navegamos al dashboard aunque la pantalla de login no haya alcanzado a reaccionar.
    this.broadcast.inProgress$
      .pipe(filter((status: InteractionStatus) => status === InteractionStatus.None))
      .subscribe(() => {
        const url = this.router.url;
        if (this.auth.account() && (url === '/' || url.startsWith('/login'))) {
          void this.router.navigate(['/dashboard'], { replaceUrl: true });
        }
      });
  }
}
