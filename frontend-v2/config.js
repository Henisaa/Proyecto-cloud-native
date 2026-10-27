/* Configuración pública de RutaExpress Console v2. Sin secretos aquí: es código que se publica en el navegador. */
window.RUTAEXPRESS_CONFIG = {
  /* Backend del BFF de producción. En local se llama directo por HTTP (CORS permite localhost). Publicada en Vercel
     la ruta relativa /api la reenvía vercel.json al BFF: un navegador https no puede llamar a un http directo. */
  apiBaseUrl: /^(localhost|127\.0\.0\.1)$/.test(window.location.hostname) ? 'http://localhost:8080' : '',

  /* 'none': sin seguridad (desarrollo, SECURITY_ENABLED=false). 'msal': Microsoft Entra ID (producción). */
  authMode: 'msal',

  /* Solo en modo 'none': rol con el que abre la consola. Se puede cambiar desde «Sesión». */
  devRole: 'ADMIN',

  /* Solo en modo 'msal'. Son los mismos valores que frontend/src/app/core/app-config.ts de la app Angular. */
  msal: {
    clientId: 'f3136620-804c-4b15-b22d-b6fad957937e',
    authority: 'https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09',
    scope: 'api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user',
    redirectUri: '', /* vacío = origen actual (debe estar registrado en Entra ID como SPA) */
  },

  /* Grupos de consumo Kafka que se muestran en el panel («Kafka Consumer Lag»). */
  kafkaGroups: ['ms-rutaexpress-report-group', 'ms-rutaexpress-audit-group'],
};
