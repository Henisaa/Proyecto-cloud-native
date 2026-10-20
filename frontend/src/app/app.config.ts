import { ApplicationConfig, importProvidersFrom, provideZoneChangeDetection } from '@angular/core';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { MsalInterceptor, MsalModule } from '@azure/msal-angular';
import { InteractionType, PublicClientApplication } from '@azure/msal-browser';
import { routes } from './app.routes';
import { errorInterceptor } from './core/error.interceptor';
import { crearLoginRequest, crearMsalConfig, crearProtectedResourceMap } from './core/msal.config';

/**
 * Este módulo se importa DESPUÉS de cargar config.json (ver main.ts), por eso la configuración de MSAL ya trae los
 * valores del entorno. Solo se usa MsalInterceptor para adjuntar el Bearer: si no hay token, la petición no sale
 * sin autorización por un fallo silencioso; MSAL redirige para obtenerlo.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([errorInterceptor]), withInterceptorsFromDi()),
    importProvidersFrom(
      MsalModule.forRoot(
        new PublicClientApplication(crearMsalConfig()),
        {
          interactionType: InteractionType.Redirect,
          authRequest: crearLoginRequest(),
        },
        {
          interactionType: InteractionType.Redirect,
          protectedResourceMap: crearProtectedResourceMap(),
        }
      )
    ),
    { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },
  ],
};
