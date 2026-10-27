/**
 * Roles de la aplicación. Es la única definición de roles del frontend y aplica la misma normalización
 * que el backend (BFF y microservicios): "Operador" equivale a Despachador y "Cliente del dominio" a Cliente.
 */
export type Rol = 'ADMIN' | 'DESPACHADOR' | 'CLIENTE' | 'AUDITOR';

export const TODOS_LOS_ROLES: readonly Rol[] = ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR'];

export function normalizarRol(rol: string): string {
  const valor = rol.trim().replace(/^ROLE_/i, '').toUpperCase();
  if (valor === 'OPERADOR DEL DOMINIO' || valor === 'OPERADOR') return 'DESPACHADOR';
  if (valor === 'CLIENTE DEL DOMINIO') return 'CLIENTE';
  return TODOS_LOS_ROLES.includes(valor as Rol) ? valor : '';
}

/** true si el usuario tiene al menos uno de los roles requeridos (comparación normalizada). */
export function tieneAlgunRol(propios: readonly string[], requeridos: readonly string[]): boolean {
  const normalizados = propios.map(normalizarRol).filter(Boolean);
  return requeridos.some((rol) => normalizados.includes(normalizarRol(rol)));
}

/** Un cliente sin ningún rol interno solo ve sus propios envíos. */
export function esSoloCliente(propios: readonly string[]): boolean {
  const normalizados = propios.map(normalizarRol);
  return normalizados.includes('CLIENTE') && !normalizados.some((r) => r === 'ADMIN' || r === 'DESPACHADOR');
}
