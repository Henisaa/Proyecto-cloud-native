const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

// Exercise the actual application boot and event handlers against a minimal DOM.
// API calls are recorded so unauthorized dashboard requests cannot silently return.
async function session(roles, hash = '#/panel') {
  const nodes = new Map();
  const calls = [];
  const posts = [];
  const puts = [];
  const dels = [];
  const documentEvents = {};
  function node(id) {
    if (!nodes.has(id)) {
      const classes = new Set();
      const events = {};
      nodes.set(id, {
        value: '', innerHTML: '', textContent: '', style: {}, events, dataset: {},
        classList: { add: (...x) => x.forEach(v => classes.add(v)), remove: (...x) => x.forEach(v => classes.delete(v)),
          contains: x => classes.has(x), toggle: (x, on) => (on === undefined ? !classes.has(x) : on) ? classes.add(x) : classes.delete(x) },
        addEventListener: (event, handler) => { events[event] = handler; },
        getAttribute: () => '', setAttribute: () => {},
      });
    }
    return nodes.get(id);
  }
  const appWindow = {
    RUTAEXPRESS_CONFIG: { authMode: 'msal', kafkaGroups: [] },
    RXAuth: { iniciarAutenticacion: async () => ({ redirigiendo: false }) },
    RX: {
      get: async path => {
        calls.push(path);
        if (path === '/api/bff/auth/me') return { roles, name: 'Prueba' };
        if (path.startsWith('/api/bff/audit/resolve/')) return { id: 'shipment-1', trackingCode: 'RX-TEST' };
        if (path.endsWith('/timeline')) return { kafkaAvailable: true, events: [] };
        if (path.endsWith('/shipments/summary')) return { total: 0, byStatus: {}, byCapacity: {}, recent: [] };
        if (path.endsWith('/shipments')) return { content: [], totalPages: 0, totalElements: 0 };
        if (path === '/api/admin/kafka/topics' || path === '/api/admin/kafka/messages' || path === '/api/admin/kafka/groups') return [];
        if (path.startsWith('/api/admin/kafka/groups/')) return { groupId: 'ms-rutaexpress-audit-group', state: 'STABLE', partitionAssignor: 'CooperativeStickyAssignor', members: [], partitions: [], totalLag: 0 };
        return {};
      },
      post: async (path, body, query) => {
        posts.push({ path, body, query });
        return {};
      },
      put: async (path, body) => {
        puts.push({ path, body });
        return { name: 'rutaexpress.demo.ep5', protected: false, partitions: [], configs: { 'retention.ms': '300000' } };
      },
      del: async (path, query) => {
        dels.push({ path, query });
        return { deleted: true };
      },
    },
    addEventListener: () => {},
  };
  const context = vm.createContext({ window: appWindow,
    document: { querySelector: node, querySelectorAll: () => [], getElementById: id => node('#' + id), addEventListener: (event, handler) => { documentEvents[event] = handler; } },
    location: { hash }, sessionStorage: { getItem: () => null },
    setInterval: () => 1, clearInterval: () => {}, setTimeout: () => 1, clearTimeout: () => {}, console,
  });
  vm.runInContext(readFileSync(join(__dirname, '../js/roles.js'), 'utf8'), context);
  context.RXRoles = appWindow.RXRoles;
  vm.runInContext(readFileSync(join(__dirname, '../js/app.js'), 'utf8'), context);
  await new Promise(resolve => setImmediate(resolve));
  if (roles.some(r => appWindow.RXRoles.normalizar(r))) {
    assert.equal(node('#aviso-global').textContent, '', 'application should boot without exceptions');
  }
  return { calls, posts, puts, dels, node, documentEvents };
}

test('Auditor boots without requesting operational shipment data', async () => {
  const { calls, node, documentEvents } = await session(['AUDITOR'], '#/auditoria');
  assert.deepEqual(calls, ['/api/bff/auth/me']);
  node('#auditoria-q').value = 'RX-TEST';
  documentEvents.submit({ target: { id: 'form-auditoria' }, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(calls, ['/api/bff/auth/me', '/api/bff/audit/resolve/RX-TEST', '/api/bff/shipments/shipment-1/timeline']);
  assert.match(node('#auditoria-resumen').innerHTML, /RX-TEST/);
});

test('documented Cliente alias loads only its shipment dashboard', async () => {
  const { calls } = await session(['Cliente del dominio']);
  assert.deepEqual(calls, ['/api/bff/auth/me', '/api/bff/shipments/summary']);
});

test('Cliente cannot enter the catalog through a direct hash URL', async () => {
  const { calls } = await session(['CLIENTE'], '#/catalogo');
  assert.deepEqual(calls, ['/api/bff/auth/me', '/api/bff/shipments/summary']);
});

test('unknown roles do not fall back to Cliente', async () => {
  const { calls, node } = await session(['SUPERADMIN']);
  assert.deepEqual(calls, ['/api/bff/auth/me']);
  assert.match(node('#aviso-global').textContent, /no tiene un rol válido/);
});

test('Admin can trigger Kafka tuning preset and sustained load forms', async () => {
  const { posts, node, documentEvents } = await session(['ADMIN'], '#/kafka');
  const presetBtn = {
    tagName: 'BUTTON',
    dataset: { accion: 'ktPreset', count: '100000' },
    closest: sel => (sel === '[data-accion]' ? presetBtn : null),
  };
  documentEvents.click({ target: presetBtn, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));

  node('#kt-sustained-rate').value = '500';
  node('#kt-sustained-sec').value = '10';
  documentEvents.submit({ target: { id: 'form-kt-sustained' }, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(posts[0].path, '/api/admin/kafka/tuning/load-test');
  assert.equal(posts[0].query.count, 100000);
  assert.equal(posts[1].path, '/api/admin/kafka/tuning/sustained');
  assert.equal(posts[1].query.messagesPerSecond, 500);
  assert.equal(posts[1].query.seconds, 10);
});

test('Admin can create, configure, delete Kafka topics and inspect consumer groups', async () => {
  const { calls, posts, puts, dels, node, documentEvents } = await session(['ADMIN'], '#/kafka');

  node('#ktp-name').value = 'rutaexpress.demo.ep5';
  node('#ktp-partitions').value = '3';
  node('#ktp-replicas').value = '3';
  node('#ktp-cleanup').value = 'delete';
  node('#ktp-retention-ms').value = '300000';
  documentEvents.submit({ target: { id: 'form-kafka-topic' }, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts[0].path, '/api/admin/kafka/topics');
  assert.equal(posts[0].body.name, 'rutaexpress.demo.ep5');

  const inspectBtn = {
    tagName: 'BUTTON',
    dataset: { accion: 'kafkaInspeccionarTopico', topic: 'rutaexpress.demo.ep5' },
    closest: sel => (sel === '[data-accion]' ? inspectBtn : null),
  };
  documentEvents.click({ target: inspectBtn, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));

  node('#kcfg-key').value = 'retention.ms';
  node('#kcfg-value').value = '600000';
  documentEvents.submit({ target: { id: 'form-kafka-config' }, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(puts[0].path, '/api/admin/kafka/topics/rutaexpress.demo.ep5/configs');
  assert.deepEqual(puts[0].body, { configs: { 'retention.ms': '600000' } });

  const delBtn = {
    tagName: 'BUTTON',
    dataset: { accion: 'kafkaEliminarTopico', topic: 'rutaexpress.demo.ep5' },
    closest: sel => (sel === '[data-accion]' ? delBtn : null),
  };
  documentEvents.click({ target: delBtn, preventDefault() {} });
  await node('#confirmar-si').events.click();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(dels[0].path, '/api/admin/kafka/topics/rutaexpress.demo.ep5');

  const groupBtn = {
    tagName: 'BUTTON',
    dataset: { accion: 'kafkaInspeccionarGrupo', group: 'ms-rutaexpress-audit-group' },
    closest: sel => (sel === '[data-accion]' ? groupBtn : null),
  };
  documentEvents.click({ target: groupBtn, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.ok(calls.includes('/api/admin/kafka/groups/ms-rutaexpress-audit-group'));

  node('#kgo-topic').value = 'tutorial-particiones';
  node('#kgo-strategy').value = 'earliest';
  documentEvents.submit({ target: { id: 'form-kafka-reset-offsets' }, preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts[1].path, '/api/admin/kafka/groups/ms-rutaexpress-audit-group/reset-offsets');
  assert.deepEqual(posts[1].body, { topic: 'tutorial-particiones', strategy: 'earliest' });

  const delGroupBtn = {
    tagName: 'BUTTON',
    dataset: { accion: 'kafkaEliminarGrupo', group: 'mi-grupo-educativo' },
    closest: sel => (sel === '[data-accion]' ? delGroupBtn : null),
  };
  documentEvents.click({ target: delGroupBtn, preventDefault() {} });
  await node('#confirmar-si').events.click();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(dels[1].path, '/api/admin/kafka/groups/mi-grupo-educativo');
});
