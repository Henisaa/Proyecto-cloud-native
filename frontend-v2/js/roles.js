/* The four business roles in Caso 3. Scopes are never roles. */
(function (root) {
  'use strict';
  var roles = ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR'];
  function normalizar(valor) {
    if (typeof valor !== 'string') return null;
    var rol = valor.trim().toUpperCase().replace(/^ROLE_/, '');
    if (rol === 'OPERADOR' || rol === 'OPERADOR DEL DOMINIO') rol = 'DESPACHADOR';
    if (rol === 'CLIENTE DEL DOMINIO') rol = 'CLIENTE';
    return roles.indexOf(rol) < 0 ? null : rol;
  }
  root.RXRoles = { normalizar: normalizar, todos: roles };
})(typeof window === 'undefined' ? globalThis : window);
