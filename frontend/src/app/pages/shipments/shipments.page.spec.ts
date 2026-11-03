import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { ShipmentsPage } from './shipments.page';

describe('Listado de envíos', () => {
  it('permite reintentar después de un error sin destruir el flujo de filtros', () => {
    const listar = jasmine.createSpy().and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 503 })),
      of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    );
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { roles: ['ADMIN'] } },
      { provide: BffService, useValue: { listarEnvios: listar } },
    ] });
    const page = TestBed.runInInjectionContext(() => new ShipmentsPage());
    page.ngOnInit();
    expect(page.error()).not.toBeNull();
    page.recargar();
    expect(listar).toHaveBeenCalledTimes(2);
    expect(page.error()).toBeNull();
    expect(page.pagina()?.size).toBe(20);
  });
});
