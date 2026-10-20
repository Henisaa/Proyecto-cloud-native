import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { runtimeConfig } from './app-config';
import { SILENCIAR_ERROR } from './error.interceptor';
import {
  Capacidad,
  CrearEnvio,
  Dashboard,
  EditarCapacidad,
  EstadoServicios,
  Envio,
  EventoAuditoriaKafka,
  FiltrosEnvios,
  Kpis,
  LineaTiempo,
  MqBinding,
  MqBindingRequest,
  MqCola,
  MqColaRequest,
  MqExchange,
  MqExchangeRequest,
  MqMetricas,
  MqNodo,
  MqPolitica,
  MqPublicarRequest,
  MqPublicarResultado,
  MqRetencionRequest,
  Pagina,
  ResumenEnvios,
  ServicioDetalle,
  ServicioResumen,
  TopServicio,
  UserProfile,
} from './models';

/**
 * Único punto de acceso al backend. Todo pasa por el API Gateway: las rutas generales usan /api/bff/ y las pantallas
 * de administración usan /api/admin/ (el Gateway la reescribe a /api/bff/admin/ del BFF; en desarrollo lo hace el proxy).
 */
@Injectable({ providedIn: 'root' })
export class BffService {
  private readonly http = inject(HttpClient);

  private get bff(): string {
    return `${runtimeConfig.apiBaseUrl}/api/bff`;
  }

  private get admin(): string {
    return `${runtimeConfig.apiBaseUrl}/api/admin`;
  }

  /** Contexto para consultas periódicas: un fallo puntual no abre un aviso emergente. */
  private silencioso(): HttpContext {
    return new HttpContext().set(SILENCIAR_ERROR, true);
  }

  /* ---------------------------------------------------------------- Sesión y panel */

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.bff}/auth/me`);
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.bff}/dashboard`, { context: this.silencioso() });
  }

  estadoServicios(): Observable<EstadoServicios> {
    return this.http.get<EstadoServicios>(`${this.bff}/status`, { context: this.silencioso() });
  }

  /* ---------------------------------------------------------------- Envíos */

  listarEnvios(filtros: FiltrosEnvios = {}, silencioso = false): Observable<Pagina<Envio>> {
    let params = new HttpParams();
    (Object.entries(filtros) as [string, string | number | undefined][]).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && `${valor}`.trim() !== '') {
        params = params.set(clave, `${valor}`);
      }
    });
    return this.http.get<Pagina<Envio>>(`${this.bff}/shipments`, {
      params,
      context: silencioso ? this.silencioso() : undefined,
    });
  }

  resumenEnvios(): Observable<ResumenEnvios> {
    return this.http.get<ResumenEnvios>(`${this.bff}/shipments/summary`, { context: this.silencioso() });
  }

  obtenerEnvio(id: string, silencioso = false): Observable<Envio> {
    return this.http.get<Envio>(`${this.bff}/shipments/${encodeURIComponent(id)}`, {
      context: silencioso ? this.silencioso() : undefined,
    });
  }

  lineaDeTiempo(id: string): Observable<LineaTiempo> {
    return this.http.get<LineaTiempo>(`${this.bff}/shipments/${encodeURIComponent(id)}/timeline`);
  }

  crearEnvio(body: CrearEnvio): Observable<Envio> {
    return this.http.post<Envio>(`${this.bff}/shipments`, body);
  }

  cambiarEstado(id: string, status: string): Observable<Envio> {
    return this.http.put<Envio>(`${this.bff}/shipments/${encodeURIComponent(id)}/status`, { status });
  }

  reintentarReserva(id: string): Observable<Envio> {
    return this.http.post<Envio>(`${this.bff}/shipments/${encodeURIComponent(id)}/capacity/retry`, {});
  }

  /* ---------------------------------------------------------------- Catálogo, reportes y auditoría */

  servicios(): Observable<ServicioResumen[]> {
    return this.http.get<ServicioResumen[]>(`${this.bff}/catalog/services`);
  }

  servicio(id: number): Observable<ServicioDetalle> {
    return this.http.get<ServicioDetalle>(`${this.bff}/catalog/services/${id}`);
  }

  editarCapacidad(servicioId: number, capacidadId: number, cambios: EditarCapacidad): Observable<Capacidad> {
    return this.http.put<Capacidad>(`${this.bff}/catalog/manage/services/${servicioId}/capacities/${capacidadId}`, cambios);
  }

  kpis(rango: string): Observable<Kpis> {
    return this.http.get<Kpis>(`${this.bff}/report/kpis`, { params: { range: rango } });
  }

  topServicios(rango: string): Observable<TopServicio[]> {
    return this.http.get<TopServicio[]>(`${this.bff}/report/top-services`, { params: { range: rango } });
  }

  auditoriaKafka(idEntidad: string): Observable<EventoAuditoriaKafka[]> {
    return this.http.get<EventoAuditoriaKafka[]>(`${this.bff}/audit/timeline/${encodeURIComponent(idEntidad)}`);
  }

  /* ---------------------------------------------------------------- Administración RabbitMQ (/api/admin/mq) */

  mqCluster(silencioso = false): Observable<MqNodo[]> {
    return this.http.get<MqNodo[]>(`${this.admin}/mq/cluster`, { context: silencioso ? this.silencioso() : undefined });
  }

  mqMetricas(silencioso = false): Observable<MqMetricas> {
    return this.http.get<MqMetricas>(`${this.admin}/mq/metrics`, { context: silencioso ? this.silencioso() : undefined });
  }

  mqColas(): Observable<MqCola[]> {
    return this.http.get<MqCola[]>(`${this.admin}/mq/queues`);
  }

  mqExchanges(): Observable<MqExchange[]> {
    return this.http.get<MqExchange[]>(`${this.admin}/mq/exchanges`);
  }

  mqBindings(): Observable<MqBinding[]> {
    return this.http.get<MqBinding[]>(`${this.admin}/mq/bindings`);
  }

  mqPoliticas(): Observable<MqPolitica[]> {
    return this.http.get<MqPolitica[]>(`${this.admin}/mq/policies`);
  }

  crearCola(body: MqColaRequest): Observable<unknown> {
    return this.http.post(`${this.admin}/mq/queues`, body);
  }

  eliminarCola(nombre: string, forzar = false): Observable<unknown> {
    return this.http.delete(`${this.admin}/mq/queues/${encodeURIComponent(nombre)}`, { params: { force: forzar } });
  }

  crearExchange(body: MqExchangeRequest): Observable<unknown> {
    return this.http.post(`${this.admin}/mq/exchanges`, body);
  }

  eliminarExchange(nombre: string, forzar = false): Observable<unknown> {
    return this.http.delete(`${this.admin}/mq/exchanges/${encodeURIComponent(nombre)}`, { params: { force: forzar } });
  }

  crearBinding(body: MqBindingRequest): Observable<unknown> {
    return this.http.post(`${this.admin}/mq/bindings`, body);
  }

  /** La routing key viaja como parámetro de consulta, así `#` y `*` quedan bien codificados. */
  eliminarBinding(b: MqBinding, forzar = false): Observable<unknown> {
    return this.http.delete(`${this.admin}/mq/bindings`, {
      params: new HttpParams()
        .set('source', b.source)
        .set('destination', b.destination)
        .set('destinationType', b.destinationType)
        .set('routingKey', b.routingKey)
        .set('force', forzar),
    });
  }

  aplicarTopologia(): Observable<unknown> {
    return this.http.post(`${this.admin}/mq/topology`, {});
  }

  aplicarRetencion(body: MqRetencionRequest): Observable<unknown> {
    return this.http.put(`${this.admin}/mq/retention`, body);
  }

  publicarMensaje(exchange: string, body: MqPublicarRequest): Observable<MqPublicarResultado> {
    return this.http.post<MqPublicarResultado>(`${this.admin}/mq/exchanges/${encodeURIComponent(exchange)}/messages`, body);
  }

  /* ---------------------------------------------------------------- Kafka (EP1, opcional) */

  kafkaTopics(): Observable<unknown> {
    return this.http.get(`${this.admin}/kafka/topics`);
  }

  kafkaLag(grupo: string): Observable<unknown> {
    return this.http.get(`${this.admin}/kafka/groups/${encodeURIComponent(grupo)}/lag`);
  }
}
