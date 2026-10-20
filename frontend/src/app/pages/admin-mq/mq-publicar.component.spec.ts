import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { BffService } from '../../core/bff.service';
import { MqPublicarComponent } from './mq-publicar.component';

describe('Publicación para probar retención', () => {
  it('conserva confirmaciones anteriores cuando el sexto mensaje es rechazado', () => {
    let n = 0;
    TestBed.configureTestingModule({ providers: [{ provide: BffService, useValue: {
      publicarMensaje: () => ++n === 6
        ? throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Cola llena' } }))
        : of({ confirmed: true, routed: true }),
    } }] });
    const page = TestBed.runInInjectionContext(() => new MqPublicarComponent());
    page.exchange.set('ex.demo');
    page.veces.set(6);
    page.publicar();
    expect(page.resultados().length).toBe(5);
    expect(page.errorForm()).toBe('Cola llena');
    expect(page.enCurso()).toBeFalse();
  });
});
