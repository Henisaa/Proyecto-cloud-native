import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import gsap from 'gsap';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div
      class="flex min-h-[100dvh] w-full items-center justify-center overflow-x-hidden bg-white p-6 text-slate-900 selection:bg-blue-600 selection:text-white"
    >
      <div class="z-10 flex flex-col items-center justify-center">
        <div class="flex flex-col items-center gap-8">
          <div class="flex cursor-default flex-col items-center">
            <div #logoIcon class="flex items-center justify-center">
              <img
                src="logo.webp"
                alt="Logo RutaExpress"
                class="h-auto w-[240px] max-w-[80vw] object-contain"
              />
            </div>
          </div>

          <div class="flex justify-center">
            <button
              #msBtn
              type="button"
              (click)="onMsClick()"
              class="group relative flex h-11 cursor-pointer items-center justify-center overflow-hidden whitespace-nowrap rounded-full border border-black bg-white px-5 text-black shadow-xs transition-colors duration-200 hover:bg-black hover:text-white hover:shadow-md"
            >
              <div #btnContent class="flex items-center justify-center gap-2.5">
                <div class="relative flex h-5 w-5 shrink-0 items-center justify-center">
                  <svg
                    class="absolute inset-0 transition-opacity duration-200 group-hover:opacity-0"
                    width="20"
                    height="20"
                    viewBox="0 0 20 20"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M6.50508 0H3C1.34315 0 0 1.34315 0 3V6.50522C0 8.16207 1.34315 9.50522 3 9.50522H6.50508C8.16193 9.50522 9.50508 8.16207 9.50508 6.50522V3C9.50508 1.34315 8.16193 0 6.50508 0Z"
                      fill="#F25022"
                    />
                    <path
                      d="M17.0002 0H13.4951C11.8383 0 10.4951 1.34315 10.4951 3V6.50522C10.4951 8.16207 11.8383 9.50522 13.4951 9.50522H17.0002C18.657 9.50522 20.0002 8.16207 20.0002 6.50522V3C20.0002 1.34315 18.657 0 17.0002 0Z"
                      fill="#7FBA00"
                    />
                    <path
                      d="M6.50508 10.4948H3C1.34315 10.4948 0 11.8379 0 13.4948V17C0 18.6568 1.34315 20 3 20H6.50508C8.16193 20 9.50508 18.6568 9.50508 17V13.4948C9.50508 11.8379 8.16193 10.4948 6.50508 10.4948Z"
                      fill="#00A4EF"
                    />
                    <path
                      d="M17.0002 10.4948H13.4951C11.8383 10.4948 10.4951 11.8379 10.4951 13.4948V17C10.4951 18.6568 11.8383 20 13.4951 20H17.0002C18.657 20 20.0002 18.6568 20.0002 17V13.4948C20.0002 11.8379 18.657 10.4948 17.0002 10.4948Z"
                      fill="#FFB900"
                    />
                  </svg>

                  <svg
                    class="absolute inset-0 opacity-0 transition-opacity duration-200 group-hover:opacity-100"
                    width="20"
                    height="20"
                    viewBox="0 0 20 20"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M6.50508 0H3C1.34315 0 0 1.34315 0 3V6.50522C0 8.16207 1.34315 9.50522 3 9.50522H6.50508C8.16193 9.50522 9.50508 8.16207 9.50508 6.50522V3C9.50508 1.34315 8.16193 0 6.50508 0Z"
                      fill="white"
                    />
                    <path
                      d="M17.0002 0H13.4951C11.8383 0 10.4951 1.34315 10.4951 3V6.50522C10.4951 8.16207 11.8383 9.50522 13.4951 9.50522H17.0002C18.657 9.50522 20.0002 8.16207 20.0002 6.50522V3C20.0002 1.34315 18.657 0 17.0002 0Z"
                      fill="white"
                    />
                    <path
                      d="M6.50508 10.4948H3C1.34315 10.4948 0 11.8379 0 13.4948V17C0 18.6568 1.34315 20 3 20H6.50508C8.16193 20 9.50508 18.6568 9.50508 17V13.4948C9.50508 11.8379 8.16193 10.4948 6.50508 10.4948Z"
                      fill="white"
                    />
                    <path
                      d="M17.0002 10.4948H13.4951C11.8383 10.4948 10.4951 11.8379 10.4951 13.4948V17C10.4951 18.6568 11.8383 20 13.4951 20H17.0002C18.657 20 20.0002 18.6568 20.0002 17V13.4948C20.0002 11.8379 18.657 10.4948 17.0002 10.4948Z"
                      fill="white"
                    />
                  </svg>
                </div>

                <span class="text-[1.15rem] font-semibold tracking-normal">Acceder con Microsoft</span>
              </div>

              <div
                #btnLoader
                class="pointer-events-none absolute inset-0 flex scale-75 items-center justify-center opacity-0"
              >
                <svg
                  class="h-5 w-5 animate-spin text-black"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    class="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    stroke-width="3.5"
                  ></circle>
                  <path
                    class="opacity-90"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  ></path>
                </svg>
              </div>
            </button>
          </div>

          @if (auth.enProceso()) {
            <p class="mt-6 text-xs font-medium text-zinc-400">Procesando inicio de sesión con Microsoft…</p>
          }

          @if (auth.error()) {
            <div class="mt-6 max-w-sm rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-left">
              <p class="text-xs font-semibold text-red-700">No se pudo completar el inicio de sesión</p>
              <p class="mt-1 break-words text-[11px] text-red-600">{{ auth.error() }}</p>
              <p class="mt-2 text-[11px] text-red-400">Más detalle en F12 → Console.</p>
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class LoginPage implements AfterViewInit, OnDestroy {
  @ViewChild('logoIcon') private readonly logoIcon!: ElementRef<HTMLElement>;
  @ViewChild('msBtn') private readonly msBtn!: ElementRef<HTMLButtonElement>;
  @ViewChild('btnContent') private readonly btnContent!: ElementRef<HTMLElement>;
  @ViewChild('btnLoader') private readonly btnLoader!: ElementRef<HTMLElement>;

  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private submitting = false;

  constructor() {
    if (this.auth.isLoggedIn) {
      void this.router.navigate(['/dashboard']);
    }

    // Al volver del redirect de Microsoft, la cuenta aparece y entramos al dashboard.
    effect(() => {
      if (this.auth.account()) {
        void this.router.navigate(['/dashboard']);
      }
    });

    document.body.classList.add('login-theme');
  }

  ngOnDestroy(): void {
    document.body.classList.remove('login-theme');
  }

  ngAfterViewInit(): void {
    gsap.set(this.msBtn.nativeElement, { width: '44px', opacity: 0, scale: 0.8 });
    gsap.set(this.btnContent.nativeElement, { opacity: 0, scale: 0.85 });
    gsap.set(this.btnLoader.nativeElement, { opacity: 0, scale: 0.75 });

    const tl = gsap.timeline({ defaults: { ease: 'power3.out' } });

    tl.from(this.logoIcon.nativeElement, { scale: 0.5, opacity: 0, duration: 0.8, ease: 'back.out(1.7)' })
      .to(this.msBtn.nativeElement, { opacity: 1, scale: 1, duration: 0.35, ease: 'power2.out' }, '-=0.2')
      .to(this.msBtn.nativeElement, { width: '120px', duration: 0.3, ease: 'power2.inOut' })
      .to(this.msBtn.nativeElement, { width: '300px', duration: 0.5, ease: 'elastic.out(1, 0.75)' })
      .to(this.btnContent.nativeElement, { opacity: 1, scale: 1, duration: 0.35, ease: 'power2.out' }, '-=0.35');
  }

  onMsClick(): void {
    if (this.submitting) return;
    this.submitting = true;

    const btn = this.msBtn.nativeElement;
    btn.classList.remove('hover:bg-black', 'hover:text-white');
    btn.classList.add('cursor-wait');

    gsap
      .timeline()
      .to(btn, { scale: 0.92, duration: 0.12, ease: 'power2.in' })
      .to(btn, { scale: 1, duration: 0.2, ease: 'back.out(2)' })
      .to(this.btnContent.nativeElement, { opacity: 0, scale: 0.8, duration: 0.2, ease: 'power2.in' }, '-=0.15')
      .to(this.btnLoader.nativeElement, { opacity: 1, scale: 1, duration: 0.3, ease: 'back.out(1.5)' }, '-=0.05')
      .add(() => this.auth.login());
  }
}
