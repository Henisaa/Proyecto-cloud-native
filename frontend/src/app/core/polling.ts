import { Observable, switchMap, takeUntil, takeWhile, timer } from 'rxjs';

export interface OpcionesSondeo<T> {
  /** Cada cuántos milisegundos se repite la consulta. */
  cadaMs: number;
  /** Tiempo máximo total; al cumplirse, la consulta periódica termina sola. */
  maximoMs: number;
  /** Cuando devuelve true la consulta periódica termina (después de emitir ese último valor). */
  hasta: (valor: T) => boolean;
}

/**
 * Consulta periódica con límite: emite cada resultado y termina al cumplirse la condición o el tiempo máximo.
 * Cancelar la suscripción (al salir de la vista) detiene el ciclo; switchMap cancela la petición anterior.
 */
export function sondear<T>(consulta: () => Observable<T>, opciones: OpcionesSondeo<T>): Observable<T> {
  return timer(opciones.cadaMs, opciones.cadaMs).pipe(
    switchMap(() => consulta()),
    takeWhile((valor) => !opciones.hasta(valor), true),
    takeUntil(timer(opciones.maximoMs))
  );
}
