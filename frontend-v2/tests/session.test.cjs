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
    RX: { get: async path => {
      calls.push(path);
      if (path === '/api/bff/auth/me') return { roles, name: 'Prueba' };
      if (path.startsWith('/api/bff/audit/resolve/')) return { id: 'shipment-1', trackingCode: 'RX-TEST' };
      if (path.endsWith('/timeline')) return { kafkaAvailable: true, events: [] };
      if (path.endsWith('/shipments/summary')) return { total: 0, byStatus: {}, byCapacity: {}, recent: [] };
      if (path.endsWith('/shipments')) return { content: [], totalPages: 0, totalElements: 0 };
      return {};
    } },
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
  return { calls, node, documentEvents };
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
