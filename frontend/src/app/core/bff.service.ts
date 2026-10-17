import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface BrokerQueue {
  name: string;
  messages: number;
  consumers: number;
  alert: boolean;
}

export interface MqQueueRequest {
  name: string;
  durable?: boolean;
  ttlMs?: number;
  maxLength?: number;
  expiresMs?: number;
  overflow?: 'reject-publish' | 'drop-head';
  deadLetterExchange?: string;
  deadLetterRoutingKey?: string;
}

export interface MqExchangeRequest {
  name: string;
  type: 'direct' | 'topic' | 'fanout' | 'headers';
  durable?: boolean;
}

export interface MqBindingRequest {
  source: string;
  destination: string;
  destinationType?: 'queue' | 'exchange';
  routingKey?: string;
}

export interface Envio {
  id: string;
  trackingCode: string;
  serviceId: number;
  serviceCode?: string | null;
  serviceType?: string | null;
  status: string;
  zone?: string | null;
  vehicleType?: string | null;
  recipientName: string;
  recipientEmail?: string | null;
  originAddress: string;
  destinationAddress: string;
  weightKg: number;
  volumeM3: number;
  packagesCount: number;
  capacityReserved: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CrearEnvio {
  serviceId: number;
  zone: string;
  packagesCount: number;
  weightKg: number;
  volumeM3: number;
  recipientName: string;
  recipientEmail?: string | null;
  recipientPhone?: string | null;
  originAddress: string;
  destinationAddress: string;
}

@Injectable({ providedIn: 'root' })
export class BffService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  mqQueues(): Observable<BrokerQueue[]> {
    return this.http.get<BrokerQueue[]>(`${this.base}/api/bff/admin/mq/queues`);
  }

  mqMetrics(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/api/bff/admin/mq/metrics`);
  }

  crearCola(body: MqQueueRequest): Observable<unknown> {
    return this.http.post(`${this.base}/api/bff/admin/mq/queues`, body);
  }

  eliminarCola(name: string, force = false): Observable<unknown> {
    return this.http.delete(`${this.base}/api/bff/admin/mq/queues/${encodeURIComponent(name)}`, { params: { force } });
  }

  crearExchange(body: MqExchangeRequest): Observable<unknown> {
    return this.http.post(`${this.base}/api/bff/admin/mq/exchanges`, body);
  }

  eliminarExchange(name: string, force = false): Observable<unknown> {
    return this.http.delete(`${this.base}/api/bff/admin/mq/exchanges/${encodeURIComponent(name)}`, { params: { force } });
  }

  crearBinding(body: MqBindingRequest): Observable<unknown> {
    return this.http.post(`${this.base}/api/bff/admin/mq/bindings`, body);
  }

  eliminarBinding(source: string, destination: string, routingKey = '', destinationType: 'queue' | 'exchange' = 'queue', force = false): Observable<unknown> {
    return this.http.delete(`${this.base}/api/bff/admin/mq/bindings`, {
      params: { source, destination, destinationType, routingKey, force },
    });
  }

  kafkaTopics(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/api/bff/admin/kafka/topics`);
  }

  me(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/api/bff/auth/me`);
  }

  status(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/api/bff/status`);
  }

  dashboard(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/api/bff/dashboard`);
  }

  catalogServices(): Observable<unknown[]> {
    return this.http.get<unknown[]>(`${this.base}/api/bff/catalog/services`);
  }

  reportKpis(range = 'last24h'): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.base}/api/bff/report/kpis?range=${range}`);
  }

  auditTimeline(idEntidad: string): Observable<unknown[]> {
    return this.http.get<unknown[]>(`${this.base}/api/bff/audit/timeline/${idEntidad}`);
  }

  listarEnvios(status?: string): Observable<Envio[]> {
    const qs = status ? `?status=${encodeURIComponent(status)}` : '';
    return this.http.get<Envio[]>(`${this.base}/api/bff/shipments${qs}`);
  }

  crearEnvio(body: CrearEnvio): Observable<Envio> {
    return this.http.post<Envio>(`${this.base}/api/bff/shipments`, body);
  }

  cambiarEstado(id: string, status: string): Observable<Envio> {
    return this.http.put<Envio>(`${this.base}/api/bff/shipments/${id}/status`, { status });
  }
}
