import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isLoggedIn) {
      return router.createUrlTree(['/login']);
    }

    const allowed = allowedRoles.map((r) => r.toLowerCase());
    const hasAny = auth.roles.some((r) => allowed.includes(r.toLowerCase()));

    return hasAny ? true : router.createUrlTree(['/dashboard']);
  };
}
