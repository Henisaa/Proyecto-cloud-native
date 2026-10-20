import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { API, BFF, CATALOG, MQ, SHIPMENTS, bearer, change, queue, rabbitHeaders, service, session, shipment, state, waitCapacity } from './helpers';

test.describe.configure({ mode: 'default' });

test('dos nodos RabbitMQ y todas las aplicaciones listas', async ({ request }) => {
  const nodes = await request.get(`${MQ}/api/nodes`, { headers: rabbitHeaders });
  const running = (await nodes.json()).filter((n: any) => n.running).map((n: any) => n.name);
  expect(running).toEqual(expect.arrayContaining(['rabbit@rabbit1', 'rabbit@rabbit2']));
  const { accessToken } = await session(request);
  const result = await request.get(`${API}/api/bff/status`, { headers: bearer(accessToken) });
  expect(result.status()).toBe(200);
  const services = (await result.json()).services;
  expect(Object.keys(services)).toHaveLength(7);
  expect(Object.values(services)).toEqual(Array(7).fill('UP'));
});

test('sin token y con firma alterada: Gateway y BFF rechazan', async ({ request }) => {
  for (const base of [API, BFF]) {
    expect((await request.get(`${base}/api/bff/shipments`)).status()).toBe(401);
    const { accessToken } = await session(request);
    const parts = accessToken.split('.');
    parts[2] = `${parts[2][0] === 'A' ? 'B' : 'A'}${parts[2].slice(1)}`;
    expect((await request.get(`${base}/api/bff/shipments`, { headers: bearer(parts.join('.')) })).status()).toBe(401);
  }
});

for (const [name, options] of Object.entries({ expired: { expired: '1' }, issuer: { iss: 'https://wrong.invalid' }, audience: { aud: 'wrong-audience' } })) {
  test(`JWT ${name} invalido se rechaza en cada capa`, async ({ request }) => {
    const { accessToken } = await session(request, 'ADMIN', options);
    for (const url of [`${API}/api/bff/shipments`, `${BFF}/api/bff/shipments`, `${CATALOG}/api/catalog/services`, `${SHIPMENTS}/api/shipments`]) {
      expect((await request.get(url, { headers: bearer(accessToken) })).status()).toBe(401);
    }
  });
}

test('preflight CORS responde sin token y restringe el origen', async ({ request }) => {
  const response = await request.fetch(`${API}/api/admin/mq/queues`, { method: 'OPTIONS', headers: {
    Origin: 'http://127.0.0.1:8089', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'Authorization, Content-Type',
  } });
  expect(response.status()).toBe(204);
  expect(response.headers()['access-control-allow-origin']).toBe('http://127.0.0.1:8089');
  expect(response.headers()['access-control-allow-origin']).not.toBe('*');
});

test('matriz de autorización con cinco actores', async ({ request }) => {
  for (const role of ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR', 'NONE']) {
    const { accessToken } = await session(request, role);
    const response = await request.post(`${API}/api/admin/mq/queues`, { headers: bearer(accessToken), data: { name: `q.ci.${randomUUID()}`, durable: true } });
    expect(response.status()).toBe(role === 'ADMIN' ? 201 : 403);
    const status = await request.get(`${API}/api/bff/status`, { headers: bearer(accessToken) });
    expect(status.status()).toBe(role === 'ADMIN' ? 200 : 403);
  }
});

test('cliente A nunca lista ni obtiene envíos de cliente B', async ({ request }) => {
  const svc = await service(request);
  const a = await session(request, 'CLIENTE', { oid: 'cliente-a' });
  const b = await session(request, 'CLIENTE', { oid: 'cliente-b' });
  const response = await shipment(request, svc.id, a.accessToken);
  expect(response.status()).toBe(201);
  const created = await response.json();
  expect((await request.get(`${API}/api/bff/shipments/${created.id}`, { headers: bearer(b.accessToken) })).status()).toBe(404);
  const listing = await request.get(`${API}/api/bff/shipments?q=${created.trackingCode}`, { headers: bearer(b.accessToken) });
  expect((await listing.json()).totalElements).toBe(0);
  expect((await state(request, created.id, a.accessToken)).id).toBe(created.id);
});

test('flujo completo reserva, correo, auditoría, bodega, ruta y entrega', async ({ request }) => {
  const svc = await service(request);
  const created = await (await shipment(request, svc.id, svc.accessToken)).json();
  expect((await change(request, created.id, 'EN_RUTA', svc.accessToken)).status()).toBe(409);
  const accepted = await change(request, created.id, 'ACEPTADO', svc.accessToken);
  expect(accepted.status()).toBe(200);
  expect((await accepted.json()).capacityStatus).toBe('PENDIENTE');
  await waitCapacity(request, created.id, svc.accessToken, 'RESERVADA');
  await expect.poll(async () => {
    const response = await request.get(`${API}/api/bff/shipments/${created.id}/timeline`, { headers: bearer(svc.accessToken) });
    return (await response.json()).events.filter((e: any) => e.source === 'RABBITMQ').length;
  }).toBeGreaterThan(1);
  await expect.poll(async () => {
    const response = await request.get('http://127.0.0.1:8025/api/v1/search', { params: { query: `to:${created.recipientEmail}` } });
    return (await response.json()).messages?.length || 0;
  }, { timeout: 60_000 }).toBeGreaterThan(0);
  for (const status of ['EN_BODEGA', 'EN_RUTA', 'ENTREGADO']) expect((await change(request, created.id, status, svc.accessToken)).status()).toBe(200);
  expect((await state(request, created.id, svc.accessToken)).allowedTransitions).toEqual([]);
});

test('JSON y datos inválidos no crean envíos ni devuelven 500', async ({ request }) => {
  const svc = await service(request);
  const variants = [{ zone: 'MARTE' }, { packagesCount: 0 }, { packagesCount: 100000 }, { weightKg: -1 }, { recipientName: 'x'.repeat(121) }, { recipientEmail: 'not-email' }];
  for (const body of variants) expect((await shipment(request, svc.id, svc.accessToken, body)).status()).toBe(400);
  const bad = await request.post(`${API}/api/bff/shipments`, { headers: { ...bearer(svc.accessToken), 'Content-Type': 'application/json' }, data: '{' });
  expect(bad.status()).toBe(400);
});

test('capacidad agotada bloquea despacho y permite reintentar al ampliar cupo', async ({ request }) => {
  const svc = await service(request, 1);
  // Ambos se crean antes de reservar: la comprobación inicial no equivale a una reserva.
  const first = await (await shipment(request, svc.id, svc.accessToken)).json();
  const second = await (await shipment(request, svc.id, svc.accessToken)).json();
  expect((await change(request, first.id, 'ACEPTADO', svc.accessToken)).status()).toBe(200);
  await waitCapacity(request, first.id, svc.accessToken, 'RESERVADA');
  expect((await change(request, second.id, 'ACEPTADO', svc.accessToken)).status()).toBe(200);
  await waitCapacity(request, second.id, svc.accessToken, 'RECHAZADA');
  expect((await change(request, second.id, 'EN_BODEGA', svc.accessToken)).status()).toBe(409);
  const update = await request.put(`${API}/api/bff/catalog/manage/services/${svc.id}/capacities/${svc.capacityId}`, { headers: bearer(svc.accessToken), data: { maxDailyPackages: 2 } });
  expect(update.status()).toBe(200);
  expect((await request.post(`${API}/api/bff/shipments/${second.id}/capacity/retry`, { headers: bearer(svc.accessToken) })).status()).toBe(200);
  await waitCapacity(request, second.id, svc.accessToken, 'RESERVADA');
});

test('carga concurrente acotada y cancelación restituyen exactamente el cupo', async ({ request }) => {
  const svc = await service(request, 20);
  const created = await Promise.all(Array.from({ length: 12 }, async () => {
    const response = await shipment(request, svc.id, svc.accessToken);
    expect(response.status()).toBe(201);
    return response.json();
  }));
  await Promise.all(created.map(async e => expect((await change(request, e.id, 'ACEPTADO', svc.accessToken)).status()).toBe(200)));
  await Promise.all(created.map(e => waitCapacity(request, e.id, svc.accessToken, 'RESERVADA')));
  let detail = await request.get(`${CATALOG}/api/catalog/services/${svc.id}`, { headers: bearer(svc.accessToken) });
  expect((await detail.json()).data.capacities[0].currentBookedPackages).toBe(12);
  await Promise.all(created.map(async e => expect((await change(request, e.id, 'CANCELADO', svc.accessToken)).status()).toBe(200)));
  await Promise.all(created.map(e => waitCapacity(request, e.id, svc.accessToken, 'LIBERADA')));
  detail = await request.get(`${CATALOG}/api/catalog/services/${svc.id}`, { headers: bearer(svc.accessToken) });
  expect((await detail.json()).data.capacities[0].currentBookedPackages).toBe(0);
});

test('administrador valida DLX, protege núcleo y conserva # en bindings', async ({ request }) => {
  const { accessToken } = await session(request);
  const headers = bearer(accessToken);
  const exchange = `ex.ci.${randomUUID()}`, name = `q.ci.${randomUUID()}`;
  expect((await request.post(`${API}/api/admin/mq/queues`, { headers, data: { name, deadLetterExchange: 'ex.inexistente' } })).status()).toBe(400);
  expect((await request.delete(`${API}/api/admin/mq/queues/q.rutaexpress.command`, { headers })).status()).toBe(409);
  expect((await request.post(`${API}/api/admin/mq/exchanges`, { headers, data: { name: exchange, type: 'topic', durable: true } })).status()).toBe(201);
  expect((await request.post(`${API}/api/admin/mq/queues`, { headers, data: { name, durable: true } })).status()).toBe(201);
  const binding = { source: exchange, destination: name, destinationType: 'queue', routingKey: 'ci.#.*' };
  expect((await request.post(`${API}/api/admin/mq/bindings`, { headers, data: binding })).status()).toBe(201);
  expect((await request.delete(`${API}/api/admin/mq/bindings`, { headers, params: binding })).status()).toBe(200);
  expect((await request.delete(`${API}/api/admin/mq/queues/${name}`, { headers })).status()).toBe(200);
  expect((await request.delete(`${API}/api/admin/mq/exchanges/${exchange}`, { headers })).status()).toBe(200);
});

test('TTL y overflow reject-publish tienen efecto real en RabbitMQ', async ({ request }) => {
  const { accessToken } = await session(request);
  const headers = bearer(accessToken);
  const exchange = `ex.ci.${randomUUID()}`, dlx = `${exchange}.dlx`, name = `q.ci.${randomUUID()}`, dlq = `${name}.dlq`;
  for (const name of [exchange, dlx]) expect((await request.post(`${API}/api/admin/mq/exchanges`, { headers, data: { name, type: 'direct', durable: true } })).status()).toBe(201);
  for (const data of [{ name: dlq, durable: true }, { name, durable: true, ttlMs: 5000, maxLength: 2, overflow: 'reject-publish', deadLetterExchange: dlx, deadLetterRoutingKey: 'dead' }]) {
    expect((await request.post(`${API}/api/admin/mq/queues`, { headers, data })).status()).toBe(201);
  }
  for (const data of [{ source: exchange, destination: name, destinationType: 'queue', routingKey: 'work' }, { source: dlx, destination: dlq, destinationType: 'queue', routingKey: 'dead' }]) {
    expect((await request.post(`${API}/api/admin/mq/bindings`, { headers, data })).status()).toBe(201);
  }
  for (let i = 0; i < 3; i++) {
    const response = await request.post(`${API}/api/admin/mq/exchanges/${exchange}/messages`, { headers, data: { routingKey: 'work', body: JSON.stringify({ eventId: randomUUID() }) } });
    expect(response.status()).toBe(i < 2 ? 200 : 409);
  }
  await expect.poll(async () => (await queue(request, dlq)).messages, { timeout: 30_000 }).toBe(2);
  const received = await request.post(`${MQ}/api/queues/%2F/${dlq}/get`, { headers: rabbitHeaders, data: { count: 2, ackmode: 'ack_requeue_false', encoding: 'auto' } });
  for (const message of await received.json()) expect(message.properties.headers['x-death'][0].reason).toBe('expired');
});

test('mensaje venenoso llega a DLQ aunque su consumidor la vacíe', async ({ request }) => {
  const { accessToken } = await session(request);
  const headers = bearer(accessToken);
  const metrics = async () => (await (await request.get(`${API}/api/admin/mq/metrics`, { headers })).json()).dlqArrivedTotal;
  const before = await metrics();
  const response = await request.post(`${API}/api/admin/mq/exchanges/ex.rutaexpress.events/messages`, { headers,
    data: { routingKey: 'notification.email.send', body: JSON.stringify({ type: 'notification.requested', payload: {} }) } });
  expect(response.status()).toBe(200);
  await expect.poll(metrics, { timeout: 30_000 }).toBeGreaterThan(before);
});

test('notificación recuperable reintenta y termina en DLQ con SMTP detenido', async ({ request }) => {
  const { accessToken } = await session(request);
  const headers = bearer(accessToken);
  const mailpit = execFileSync('docker', ['ps', '-q', '--filter', 'label=com.docker.compose.service=mailpit']).toString().trim();
  expect(mailpit).not.toBe('');
  const before = (await queue(request, 'q.rutaexpress.notification.dlq')).message_stats?.ack || 0;
  execFileSync('docker', ['stop', mailpit]);
  try {
    const response = await request.post(`${API}/api/admin/mq/exchanges/ex.rutaexpress.events/messages`, { headers, data: {
      routingKey: 'notification.email.send', body: JSON.stringify({ eventId: randomUUID(), type: 'notification.requested', timestamp: new Date().toISOString(), payload: { shipmentId: randomUUID(), recipientEmail: 'ci@example.com', recipientName: 'CI', subject: 'SMTP detenido', message: 'retry' } }),
    } });
    expect(response.status()).toBe(200);
    await expect.poll(async () => (await queue(request, 'q.rutaexpress.notification.dlq')).message_stats?.ack || 0, { timeout: 60_000 }).toBeGreaterThan(before);
  } finally { execFileSync('docker', ['start', mailpit]); }
});
