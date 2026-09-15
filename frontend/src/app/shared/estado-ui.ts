/** Estilos de píldoras y puntos por estado de envío (diseño zinc/azul). */
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
    case 'INACTIVE':
      return 'border-zinc-200 bg-zinc-100 text-zinc-600';
    default:
      return 'border-zinc-200 bg-zinc-100 text-zinc-600';
  }
}
