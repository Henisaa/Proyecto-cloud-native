/**
 * Configuración de Microsoft Authentication Library (MSAL) para Microsoft Entra ID (Azure AD)
 * Proyecto Cloud Native - RutaExpress (EP1 DSY1107)
 */

export const msalConfig = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID || 'f3136620-804c-4b15-b22d-b6fad957937e',
    authority: import.meta.env.VITE_AZURE_AUTHORITY || 'https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09',
    redirectUri: import.meta.env.VITE_AZURE_REDIRECT_URI || window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    navigateToLoginRequestUrl: true,
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
};

/**
 * Scopes solicitados para autenticación inicial y obtención de Bearer token para el BFF
 */
export const loginRequest = {
  scopes: [
    'openid',
    'profile',
    'email',
    import.meta.env.VITE_AZURE_SCOPE || 'api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user',
  ],
};

/**
 * Endpoints del Backend For Frontend (BFF)
 */
export const apiConfig = {
  bffBaseUrl: import.meta.env.VITE_BFF_API_BASE_URL || 'http://localhost:8080',
  endpoints: {
    me: '/api/bff/auth/me',
    status: '/api/bff/status',
    dashboard: '/api/bff/dashboard',
    catalogServices: '/api/bff/catalog/services',
    reportKpis: '/api/bff/report/kpis',
    auditTimeline: (id) => `/api/bff/audit/timeline/${id}`,
  },
};

/**
 * App Roles corporativos definidos en Microsoft Entra ID
 */
export const APP_ROLES = {
  ADMIN: 'Admin',
  DESPACHADOR: 'Despachador',
  CLIENTE: 'Cliente',
  AUDITOR: 'Auditor',
};
