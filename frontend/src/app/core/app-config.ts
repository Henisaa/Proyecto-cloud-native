/**
 * Configuración en tiempo de ejecución (FE-19). La app lee /config.json antes de arrancar; el contenedor lo genera
 * desde variables de entorno, así la misma imagen sirve en cualquier entorno sin recompilar.
 */
export interface AppConfig {
  /** URL del API Gateway (https://api.<dominio>). Vacío = mismo origen (proxy de desarrollo). */
  apiBaseUrl: string;
  /** Nombre del entorno que se muestra como distintivo (desarrollo, produccion...). */
  environmentName: string;
  clientId: string;
  authority: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
  scope: string;
}

export const DEFAULT_CONFIG: AppConfig = {
  apiBaseUrl: '',
  environmentName: 'desarrollo',
  clientId: 'f3136620-804c-4b15-b22d-b6fad957937e',
  authority: 'https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09',
  redirectUri: '',
  postLogoutRedirectUri: '',
  scope: 'api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user',
};

/** Objeto compartido: se rellena una vez, antes de bootstrapApplication. */
export const runtimeConfig: AppConfig = { ...DEFAULT_CONFIG };

/** Combina lo leído de config.json con los valores por defecto; ignora campos desconocidos o vacíos. */
export function combinarConfig(base: AppConfig, leido: Partial<Record<keyof AppConfig, unknown>>): AppConfig {
  const resultado: AppConfig = { ...base };
  (Object.keys(base) as (keyof AppConfig)[]).forEach((clave) => {
    const valor = leido[clave];
    if (typeof valor === 'string' && (valor !== '' || clave === 'apiBaseUrl')) {
      resultado[clave] = valor;
    }
  });
  return resultado;
}

/** URI de redirección efectiva: la configurada o, si falta, el origen actual. */
export function origenApp(): string {
  return runtimeConfig.redirectUri || window.location.origin;
}

export async function loadRuntimeConfig(url = '/config.json'): Promise<void> {
  try {
    const respuesta = await fetch(url, { cache: 'no-store' });
    if (respuesta.ok) {
      Object.assign(runtimeConfig, combinarConfig(DEFAULT_CONFIG, (await respuesta.json()) as Partial<Record<keyof AppConfig, unknown>>));
    }
  } catch {
    // Sin config.json se usan los valores por defecto (desarrollo local).
  }
}
