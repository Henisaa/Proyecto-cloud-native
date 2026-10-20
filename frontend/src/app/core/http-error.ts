import { HttpErrorResponse } from '@angular/common/http';

export interface ErrorUi {
  status: number;
  mensaje: string;
  /** Errores por campo, si el backend los informó. */
  campos?: Record<string, string>;
}

/** Mensajes por código (FE-22). Para 400, 404 y 409 se prefiere el texto que envía el backend. */
export const MENSAJES_POR_CODIGO: Record<number, string> = {
  401: 'Vuelva a iniciar sesión',
  403: 'Sin permiso',
  404: 'No encontrado',
  429: 'Demasiadas solicitudes',
  503: 'Servicio o mensajería no disponible, reintente',
  504: 'Tiempo agotado',
};

function texto(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor.trim().length > 0 ? valor.trim() : undefined;
}

function comoObjeto(cuerpo: unknown): Record<string, unknown> | null {
  if (typeof cuerpo === 'string') {
    try {
      const parseado: unknown = JSON.parse(cuerpo);
      return parseado && typeof parseado === 'object' ? (parseado as Record<string, unknown>) : null;
    } catch {
      return null;
    }
  }
  return cuerpo && typeof cuerpo === 'object' ? (cuerpo as Record<string, unknown>) : null;
}

function comoCampos(valor: unknown): Record<string, string> | undefined {
  if (!valor || typeof valor !== 'object' || Array.isArray(valor)) return undefined;
  const campos: Record<string, string> = {};
  for (const [clave, mensaje] of Object.entries(valor as Record<string, unknown>)) {
    if (typeof mensaje === 'string') campos[clave] = mensaje;
  }
  return Object.keys(campos).length > 0 ? campos : undefined;
}

/**
 * Interpreta los dos formatos de error del sistema: ProblemDetail (detail, errors) y el formato de shipments y catalog
 * (message, validationErrors). Nunca devuelve un texto técnico genérico si el backend explicó el problema.
 */
export function interpretarError(error: unknown): ErrorUi {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, mensaje: MENSAJES_POR_CODIGO[504] };
  }
  const status = error.status;
  if (status === 0) {
    return { status, mensaje: MENSAJES_POR_CODIGO[504] };
  }
  const cuerpo = comoObjeto(error.error);
  const delBackend = texto(cuerpo?.['detail']) ?? texto(cuerpo?.['message']);
  const campos = comoCampos(cuerpo?.['errors']) ?? comoCampos(cuerpo?.['validationErrors']);

  let mensaje: string;
  if (status === 409) {
    mensaje = delBackend ?? 'La operación entra en conflicto con el estado actual';
  } else if (status === 400) {
    mensaje = delBackend ?? 'Solicitud inválida';
  } else if (status === 401 || status === 403 || status === 429 || status === 503 || status === 504) {
    mensaje = MENSAJES_POR_CODIGO[status];
  } else if (status === 404) {
    mensaje = delBackend ?? MENSAJES_POR_CODIGO[404];
  } else {
    mensaje = delBackend ?? 'Ocurrió un error inesperado';
  }
  return { status, mensaje, campos };
}
