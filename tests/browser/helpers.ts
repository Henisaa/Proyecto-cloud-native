import { APIRequestContext, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

export const API = 'http://127.0.0.1:8088';
export const BFF = 'http://127.0.0.1:18080';
export const CATALOG = 'http://127.0.0.1:18081';
export const SHIPMENTS = 'http://127.0.0.1:18085';
export const MQ = 'http://127.0.0.1:15672';
export const rabbitHeaders = { Authorization: `Basic ${Buffer.from('ci-user:ci-disposable-rabbit').toString('base64')}` };

export async function session(request: APIRequestContext, role = 'ADMIN', extra: Record<string, string> = {}) {
  const response = await request.get(`${API}/fixture/session`, { params: { role, ...extra } });
  expect(response.ok()).toBeTruthy();
  return response.json() as Promise<{ accessToken: string; claims: Record<string, unknown> }>;
}

export function bearer(accessToken: string) { return { Authorization: `Bearer ${accessToken}` }; }

export async function service(request: APIRequestContext, capacity = 100) {
  const { accessToken } = await session(request);
  const headers = bearer(accessToken);
  const created = await request.post(`${CATALOG}/api/catalog/services`, { headers,
    data: { code: `CI-${randomUUID()}`, name: 'Servicio CI', category: 'EXPRESS', basePrice: 1000, estimatedDeliveryHours: 6 } });
  expect(created.status(), await created.text()).toBe(201);
  const data = (await created.json()).data;
  const cap = await request.post(`${CATALOG}/api/catalog/services/${data.id}/capacities`, { headers,
    data: { zone: 'URBAN_CENTER', vehicleType: 'VAN', maxDailyPackages: capacity, maxWeightKg: 1000, maxVolumeM3: 100 } });
  expect(cap.status(), await cap.text()).toBe(201);
  return { id: data.id as number, code: data.code as string, capacityId: (await cap.json()).data.id as number, accessToken };
}

export async function shipment(request: APIRequestContext, serviceId: number, accessToken: string, changes = {}) {
  return request.post(`${API}/api/bff/shipments`, { headers: bearer(accessToken), data: {
    serviceId, zone: 'URBAN_CENTER', packagesCount: 1, weightKg: 1.5, volumeM3: 0.01,
    recipientName: 'Cliente CI', recipientEmail: `ci-${randomUUID()}@example.com`,
    originAddress: 'Origen CI', destinationAddress: 'Destino CI', ...changes,
  } });
}

export async function state(request: APIRequestContext, id: string, accessToken: string) {
  const response = await request.get(`${API}/api/bff/shipments/${id}`, { headers: bearer(accessToken) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

export async function waitCapacity(request: APIRequestContext, id: string, accessToken: string, expected: string) {
  await expect.poll(async () => (await state(request, id, accessToken)).capacityStatus, { timeout: 60_000 }).toBe(expected);
}

export async function change(request: APIRequestContext, id: string, status: string, accessToken: string) {
  return request.put(`${API}/api/bff/shipments/${id}/status`, { headers: bearer(accessToken), data: { status } });
}

export async function queue(request: APIRequestContext, name: string) {
  const response = await request.get(`${MQ}/api/queues/%2F/${encodeURIComponent(name)}`, { headers: rabbitHeaders });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}
