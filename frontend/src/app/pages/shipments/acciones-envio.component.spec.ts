import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { Envio } from '../../core/models';
import { AccionesEnvioComponent } from './acciones-envio.component';

describe('Acciones según rol y resultado asíncrono', () => {
  const envio = { id: 's-1', trackingCode: 'RX-TEST', status: 'CREADO', capacityStatus: 'NO_APLICA', allowedTransitions: ['ACEPTADO', 'CANCELADO'], canRetryCapacity: false } as Envio;
  it('Cliente no ve botones para cambiar estado', () => {
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { hasRole: () => false } },
      { provide: BffService, useValue: {} },
    ] });
    const fixture = TestBed.createComponent(AccionesEnvioComponent);
    fixture.componentRef.setInput('envio', envio);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-accion]')).toBeNull();
  });
  it('Despachador solo ve allowedTransitions y un doble clic publica una sola operación', () => {
    const respuesta = new Subject<Envio>();
    const cambiar = jasmine.createSpy().and.returnValue(respuesta);
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { hasRole: () => true } },
      { provide: BffService, useValue: { cambiarEstado: cambiar } },
    ] });
    const fixture = TestBed.createComponent(AccionesEnvioComponent);
    fixture.componentRef.setInput('envio', envio);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-accion="ACEPTADO"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-accion="EN_BODEGA"]')).toBeNull();
    const page = fixture.componentInstance;
    page.aplicar('ACEPTADO');
    page.aplicar('ACEPTADO');
    expect(cambiar).toHaveBeenCalledTimes(1);
    expect(page.enCurso()).toBeTrue();
    respuesta.next({ ...envio, status: 'ACEPTADO', capacityStatus: 'PENDIENTE', allowedTransitions: ['CANCELADO'] });
    expect(page.enCurso()).toBeFalse();
  });
});
