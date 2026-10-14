import { Configuration, PopupRequest } from '@azure/msal-browser';
import { environment } from '../../environments/environment';

export const msalConfig: Configuration = {
  auth: {
    clientId: environment.msal.clientId,
    authority: environment.msal.authority,
    redirectUri: environment.msal.redirectUri,
    postLogoutRedirectUri: environment.msal.postLogoutRedirectUri,
  },
  cache: {
    cacheLocation: 'localStorage',
  },
};

export const loginRequest: PopupRequest = {
  scopes: ['openid', 'profile', 'email', environment.msal.scope],
};

export const protectedResourceMap = new Map<string, string[]>([
  [`${window.location.origin}/api/bff*`, [environment.msal.scope]],
  ['/api/bff*', [environment.msal.scope]],
  ['*/api/bff*', [environment.msal.scope]],
]);
