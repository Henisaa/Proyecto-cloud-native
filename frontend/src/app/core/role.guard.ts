import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { tieneAlgunRol } from './roles';

/**
 * Los roles se declaran una sola vez, en `data.roles` de cada ruta. Este guard, el menú y el backend comparten la
 * misma normalización (Operador = Despachador, Cliente del dominio = Cliente). Sin permiso se muestra la página 403.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const requeridos = (route.data['roles'] as string[] | undefined) ?? [];

  if (!auth.hasSession()) {
    return router.createUrlTree(['/']);
  }
  if (requeridos.length === 0 || tieneAlgunRol(auth.roles, requeridos)) {
    return true;
  }
  return router.createUrlTree(['/forbidden']);
};
