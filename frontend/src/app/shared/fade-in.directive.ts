import { AfterViewInit, Directive, ElementRef, Input, inject } from '@angular/core';
import gsap from 'gsap';

/** Animación de entrada (GSAP) reutilizable para páginas, secciones y listas. */
@Directive({
  selector: '[appFadeIn]',
  standalone: true,
})
export class FadeInDirective implements AfterViewInit {
  @Input() appFadeInDelay = 0;
  /** Selector opcional de hijos a animar en cascada (ej: ".metric-card"). */
  @Input() appFadeInStagger = '';

  private readonly el = inject(ElementRef<HTMLElement>);

  ngAfterViewInit(): void {
    const host = this.el.nativeElement;

    if (this.appFadeInStagger) {
      const hijos = host.querySelectorAll(this.appFadeInStagger);
      if (hijos.length > 0) {
        gsap.from(hijos, {
          opacity: 0,
          y: 16,
          duration: 0.4,
          stagger: 0.07,
          delay: this.appFadeInDelay,
          ease: 'power2.out',
          clearProps: 'transform',
        });
        gsap.from(host, { opacity: 0, duration: 0.4, delay: this.appFadeInDelay, ease: 'power2.out' });
        return;
      }
    }

    gsap.from(host, {
      y: 18,
      opacity: 0,
      duration: 0.55,
      delay: this.appFadeInDelay,
      ease: 'power3.out',
      clearProps: 'transform',
    });
  }
}
