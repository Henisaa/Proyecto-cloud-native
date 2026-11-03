const { test } = require('node:test');
const assert = require('node:assert/strict');
require('../js/roles.js');

test('only documented business roles and aliases are accepted', () => {
  for (const [raw, expected] of Object.entries({
    Admin: 'ADMIN', ' operador ': 'DESPACHADOR', 'Operador del dominio': 'DESPACHADOR',
    'Cliente del dominio': 'CLIENTE', 'role_auditor': 'AUDITOR',
  })) assert.equal(RXRoles.normalizar(raw), expected);
  for (const raw of ['SUPERADMIN', 'SCOPE_ADMIN', 'access_as_user', '', null, 1]) {
    assert.equal(RXRoles.normalizar(raw), null);
  }
});
