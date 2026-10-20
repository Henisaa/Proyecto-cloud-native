import { MqColaRequest } from '../core/models';

/**
 * Validaciones del administrador RabbitMQ: replican las del backend (RabbitAdminService) para dar el mensaje antes
 * de llamar al servidor. El backend sigue siendo la fuente de verdad y vuelve a validar todo.
 */
export const NOMBRE_MQ = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
export const CLAVE_MQ = /^[A-Za-z0-9*#._-]*$/;
export const TREINTA_DIAS_MS = 2_592_000_000;

export function validarNombreMq(nombre: string, entidad: string): string | null {
  const valor = nombre.trim();
  if (valor.length === 0) return `El nombre de ${entidad} es obligatorio`;
  if (valor.length > 255) return `El nombre de ${entidad} no puede superar 255 caracteres`;
  if (valor.startsWith('amq.')) return "El prefijo 'amq.' está reservado por RabbitMQ";
  if (!NOMBRE_MQ.test(valor)) {
    return `El nombre de ${entidad} solo admite letras, números, '.', '-' y '_', y debe empezar con letra o número`;
  }
  return null;
}

export function validarClaveMq(clave: string): string | null {
  if (clave.length > 255) return 'La routing key no puede superar 255 caracteres';
  if (!CLAVE_MQ.test(clave)) return "La routing key solo admite letras, números, '.', '-', '_', '*' y '#'";
  return null;
}

function rango(valor: number | null | undefined, campo: string, minimo: number, maximo: number): string | null {
  if (valor === null || valor === undefined) return null;
  if (!Number.isInteger(valor) || valor < minimo || valor > maximo) return `${campo} debe ser un entero entre ${minimo} y ${maximo}`;
  return null;
}

/** Devuelve el primer error de la cola o null si es válida. */
export function validarCola(cola: MqColaRequest): string | null {
  return (
    validarNombreMq(cola.name, 'la cola') ??
    rango(cola.ttlMs, 'ttlMs', 1000, TREINTA_DIAS_MS) ??
    rango(cola.expiresMs, 'expiresMs', 1000, TREINTA_DIAS_MS) ??
    rango(cola.maxLength, 'maxLength', 1, 1_000_000) ??
    (cola.deadLetterRoutingKey && !cola.deadLetterExchange ? 'La routing key del DLX exige elegir un DLX' : null) ??
    (cola.deadLetterRoutingKey ? validarClaveMq(cola.deadLetterRoutingKey) : null)
  );
}

export function validarRetencion(commandTtlMs: number, dlqTtlMs: number, maxLength: number): string | null {
  return (
    rango(commandTtlMs, 'El TTL de comandos', 1000, TREINTA_DIAS_MS) ??
    rango(dlqTtlMs, 'El TTL de las DLQ', 1000, TREINTA_DIAS_MS) ??
    rango(maxLength, 'El máximo de mensajes', 1, 1_000_000)
  );
}
