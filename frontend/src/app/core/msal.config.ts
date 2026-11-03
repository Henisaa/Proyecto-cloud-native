import { Configuration, PopupRequest } from '@azure/msal-browser';
import { origenApp, runtimeConfig } from './app-config';

/** Se construye a partir de la configuración en tiempo de ejecución (ya cargada antes de importar app.config). */
export function crearMsalConfig(): Configuration {
  const origen = origenApp();
  return {
    auth: {
      clientId: runtimeConfig.clientId,
      authority: runtimeConfig.authority,
      redirectUri: origen,
      postLogoutRedirectUri: runtimeConfig.postLogoutRedirectUri || origen,
    },
    cache: {
      // sessionStorage: menos exposición a XSS, a cambio de no compartir la sesión entre pestañas (decisión D8).
      cacheLocation: 'sessionStorage',
    },
  };
}

export function crearLoginRequest(): PopupRequest {
  return { scopes: ['openid', 'profile', 'email', runtimeConfig.scope] };
}

/** El MsalInterceptor adjunta el Bearer con este scope a todo lo que vaya al API (Gateway). */
export function crearProtectedResourceMap(): Map<string, string[]> {
  const base = runtimeConfig.apiBaseUrl || window.location.origin;
  return new Map<string, string[]>([[`${base}/api/*`, [runtimeConfig.scope]]]);
}
