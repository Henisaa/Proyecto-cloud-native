import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Middleware de sesión: si no hay cuenta MSAL activa, redirige SIEMPRE a la raíz (/),
 * donde está la pantalla de login. No dispara el redirect a Microsoft automáticamente;
 * eso solo ocurre cuando el usuario presiona "Acceder con Microsoft".
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.hasSession() ? true : router.createUrlTree(['/']);
};
