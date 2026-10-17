export const environment = {
  production: false,
  /** Modo demo (solo en la copia frontend-rutaexpress-demo): bypass de login y datos mock. */
  demo: false,
  /** Vacío = misma-origen: Angular usa proxy.conf.json local y Vercel reescribe producción hacia API Gateway. */
  apiBaseUrl: '',
  msal: {
    clientId: 'f3136620-804c-4b15-b22d-b6fad957937e',
    authority: 'https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09',
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    scope: 'api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user',
  },
};
