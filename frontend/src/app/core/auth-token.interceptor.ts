import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { catchError, from, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Adjunta el Bearer token de Azure AD a TODA llamada hacia /api/bff.
 * Refuerza al MsalInterceptor con matching propio (evita fallos de pattern matching).
 */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/bff') || environment.demo) {
    return next(req);
  }

  if (req.headers.has('Authorization')) {
    return next(req);
  }

  const msal = inject(MsalService);
  const cuenta = msal.instance.getActiveAccount() ?? msal.instance.getAllAccounts()[0] ?? null;

  if (!cuenta) {
    console.warn('authTokenInterceptor: sin cuenta activa, se envía sin token:', req.url);
    return next(req);
  }

  return from(
    msal.acquireTokenSilent({
      scopes: [environment.msal.scope],
      account: cuenta,
    })
  ).pipe(
    switchMap((resultado) => {
      console.info('authTokenInterceptor: token adjuntado a', req.url);
      return next(
        req.clone({
          setHeaders: { Authorization: `Bearer ${resultado.accessToken}` },
        })
      );
    }),
    catchError((err) => {
      console.error('authTokenInterceptor: no se pudo adquirir el token para', req.url, err);
      return next(req);
    })
  );
};
