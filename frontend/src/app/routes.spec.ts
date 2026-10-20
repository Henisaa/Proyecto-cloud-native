import { isStandalone, Type } from '@angular/core';
import { routes } from './app.routes';
import { TODOS_LOS_ROLES } from './core/roles';

describe('Contrato de navegación completo', () => {
  it('todas las rutas cargan un componente standalone existente', async () => {
    for (const route of routes) {
      if (route.loadComponent) {
        const component = await route.loadComponent();
        expect(isStandalone(component as Type<unknown>)).withContext(`Ruta ${route.path}`).toBeTrue();
      }
    }
  });
  it('las entradas de menú declaran roles válidos y guard', () => {
    for (const route of routes.filter(r => r.data?.['menu'])) {
      const roles = route.data?.['roles'] as string[];
      expect(roles.length).withContext(`Ruta ${route.path}`).toBeGreaterThan(0);
      expect(roles.every(r => (TODOS_LOS_ROLES as readonly string[]).includes(r))).toBeTrue();
      expect(route.canActivate?.length ?? 0).toBeGreaterThan(0);
    }
  });
});
