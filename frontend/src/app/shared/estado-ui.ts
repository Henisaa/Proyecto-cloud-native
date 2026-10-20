/** Estilos de píldoras y puntos por estado de envío y de capacidad (diseño zinc/azul). */
export function pillEstado(estado: string): string {
  switch (estado) {
    case 'ENTREGADO':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    case 'EN_RUTA':
      return 'border-blue-200 bg-blue-50 text-blue-700';
    case 'EN_BODEGA':
      return 'border-amber-200 bg-amber-50 text-amber-700';
    case 'ACEPTADO':
      return 'border-indigo-200 bg-indigo-50 text-indigo-700';
    case 'CANCELADO':
      return 'border-rose-200 bg-rose-50 text-rose-700';
    default:
      return 'border-zinc-200 bg-zinc-100 text-zinc-600';
  }
}

export function puntoEstado(estado: string): string {
  switch (estado) {
    case 'ENTREGADO':
      return 'bg-emerald-500';
    case 'EN_RUTA':
      return 'bg-blue-500 animate-pulse';
    case 'EN_BODEGA':
      return 'bg-amber-500';
    case 'ACEPTADO':
      return 'bg-indigo-500';
    case 'CANCELADO':
      return 'bg-rose-500';
    default:
      return 'bg-zinc-400';
  }
}

export function pillServicio(estado: string): string {
  switch (estado) {
    case 'ACTIVE':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    case 'SUSPENDED':
      return 'border-rose-200 bg-rose-50 text-rose-700';
    default:
      return 'border-zinc-200 bg-zinc-100 text-zinc-600';
  }
}

/** Insignia de la capacidad: el resultado asíncrono de la reserva (PENDIENTE hasta que catalog responde). */
export function pillCapacidad(capacidad: string): string {
  switch (capacidad) {
    case 'RESERVADA':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    case 'PENDIENTE':
      return 'border-amber-200 bg-amber-50 text-amber-700';
    case 'RECHAZADA':
      return 'border-rose-200 bg-rose-50 text-rose-700';
    case 'LIBERACION_PENDIENTE':
      return 'border-orange-200 bg-orange-50 text-orange-700';
    case 'LIBERADA':
      return 'border-sky-200 bg-sky-50 text-sky-700';
    default:
      return 'border-zinc-200 bg-zinc-100 text-zinc-500';
  }
}

/** Explicación en lenguaje simple de cada estado de capacidad. */
export function explicacionCapacidad(capacidad: string): string {
  switch (capacidad) {
    case 'RESERVADA':
      return 'El catálogo confirmó el cupo: el envío puede pasar a bodega.';
    case 'PENDIENTE':
      return 'Reserva en proceso: el catálogo aún no responde. Se actualiza sola.';
    case 'RECHAZADA':
      return 'No hay capacidad disponible. Reintente la reserva o cancele el envío.';
    case 'LIBERACION_PENDIENTE':
      return 'Envío cancelado: se está devolviendo el cupo al catálogo.';
    case 'LIBERADA':
      return 'El cupo fue devuelto al catálogo.';
    default:
      return 'Este envío no usa reserva de capacidad.';
  }
}

export function etiquetaCapacidad(capacidad: string): string {
  return capacidad === 'LIBERACION_PENDIENTE' ? 'LIBERANDO' : capacidad.replace('_', ' ');
}

/** Un envío con la reserva PENDIENTE se actualiza solo hasta que el catálogo responda. */
export function hayReservaPendiente(envios: readonly { capacityStatus: string }[]): boolean {
  return envios.some((e) => e.capacityStatus === 'PENDIENTE' || e.capacityStatus === 'LIBERACION_PENDIENTE');
}
