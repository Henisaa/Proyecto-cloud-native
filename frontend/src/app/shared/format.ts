/** Formatos es-CL de fechas y números. */
const FECHA_HORA = new Intl.DateTimeFormat('es-CL', { dateStyle: 'short', timeStyle: 'short' });
const NUMERO = new Intl.NumberFormat('es-CL');
const DECIMAL = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 2 });

export function fechaHora(valor: string | null | undefined): string {
  if (!valor) return '—';
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? '—' : FECHA_HORA.format(fecha);
}

export function numero(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : NUMERO.format(valor);
}

export function decimal(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : DECIMAL.format(valor);
}

export function moneda(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : `$${NUMERO.format(Math.round(valor))}`;
}

export function bytes(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—';
  const unidades = ['B', 'KB', 'MB', 'GB'];
  let v = valor;
  let i = 0;
  while (v >= 1024 && i < unidades.length - 1) {
    v /= 1024;
    i++;
  }
  return `${DECIMAL.format(v)} ${unidades[i]}`;
}

export function duracion(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  const minutos = Math.floor(ms / 60000);
  const horas = Math.floor(minutos / 60);
  const dias = Math.floor(horas / 24);
  if (dias > 0) return `${dias} d ${horas % 24} h`;
  if (horas > 0) return `${horas} h ${minutos % 60} min`;
  return `${minutos} min`;
}
