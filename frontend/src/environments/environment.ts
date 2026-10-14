export const environment = {
  production: false,
  /** Modo demo (solo en la copia frontend-rutaexpress-demo): bypass de login y datos mock. */
  demo: false,
  /**
   * Vacío = misma-origen: en desarrollo el proxy de Angular (proxy.conf.json) envía /api al BFF
   * en la EC2, y en producción nginx (contenedor frontend) proxya /api hacia el servicio bff:8080.
   * Así no hay CORS ni dominios cruzados.
   */
  apiBaseUrl: '',
  msal: {
    clientId: 'f3136620-804c-4b15-b22d-b6fad957937e',
    authority: 'https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09',
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    scope: 'api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user',
  },
};
