import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { interpretarError } from './http-error';
import { ToastService } from './toast.service';

/** Las consultas periódicas y los errores que la pantalla muestra por su cuenta no abren un aviso emergente. */
export const SILENCIAR_ERROR = new HttpContextToken<boolean>(() => false);

/** Manejo central de errores HTTP (FE-22): muestra el mensaje por código y vuelve al login si el token venció. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toasts = inject(ToastService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !req.context.get(SILENCIAR_ERROR)) {
        const ui = interpretarError(err);
        if (ui.status === 401) {
          toasts.error('Sesión expirada. Vuelva a iniciar sesión.');
          void router.navigate(['/'], { queryParams: { expirada: 1 } });
        } else {
          toasts.error(ui.mensaje);
        }
      }
      return throwError(() => err);
    })
  );
};
