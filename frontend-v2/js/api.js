/* Cliente del BFF y autenticación de RutaExpress Console v2. Sin dependencias: solo fetch y, en modo msal, MSAL.js. */
(function () {
  'use strict';

  var cfg = window.RUTAEXPRESS_CONFIG;
  var MSAL_URL = 'https://cdn.jsdelivr.net/npm/@azure/msal-browser@5.21.0/lib/msal-browser.min.js';

  function ApiError(status, message, cuerpo) {
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
    this.cuerpo = cuerpo;
  }
  ApiError.prototype = Object.create(Error.prototype);

  /* Mensaje legible. Los 500 del BFF traen el código de soporte en `detail`: se muestra tal cual. */
  function mensajeDe(status, cuerpo) {
    var detalle = cuerpo && typeof cuerpo === 'object' ? (cuerpo.detail || cuerpo.message || cuerpo.error) : null;
    if (status === 401) return 'Sesión no válida o vencida. Vuelve a iniciar sesión.';
    if (status === 403) return 'Tu rol no tiene permiso para esta acción.';
    if (status === 404) return detalle || 'No encontrado.';
    if (status === 409) return detalle || 'Conflicto: la operación no se puede aplicar en este momento.';
    if (status === 503) return detalle || 'Servicio no disponible. Inténtalo de nuevo.';
    if (status >= 500) return detalle || 'Error interno del servidor.';
    return detalle || ('Error ' + status + '.');
  }

  var proveedorToken = function () { return Promise.resolve(null); };

  function peticion(metodo, ruta, opciones) {
    opciones = opciones || {};
    /* Con apiBaseUrl vacío (Vercel) la base es el origen: new URL necesita una URL absoluta. */
    var base = String(cfg.apiBaseUrl || window.location.origin).replace(/\/+$/, '');
    var url = new URL(base + ruta);
    var query = opciones.query || {};
    Object.keys(query).forEach(function (k) {
      var v = query[k];
      if (v !== undefined && v !== null && String(v).trim() !== '') url.searchParams.set(k, String(v));
    });

    var cabeceras = { Accept: 'application/json' };
    var tieneCuerpo = opciones.cuerpo !== undefined;
    if (tieneCuerpo) cabeceras['Content-Type'] = 'application/json';

    return proveedorToken().then(function (token) {
      if (token) cabeceras.Authorization = 'Bearer ' + token;
      var control = new AbortController();
      var reloj = setTimeout(function () { control.abort(); }, opciones.tiempoMs || 20000);
      return fetch(url.toString(), {
        method: metodo,
        headers: cabeceras,
        body: tieneCuerpo ? JSON.stringify(opciones.cuerpo) : undefined,
        signal: control.signal,
        mode: 'cors',
        credentials: 'omit',
      }).then(function (respuesta) {
        return respuesta.text().then(function (texto) {
          clearTimeout(reloj);
          var datos = null;
          if (texto) {
            try { datos = JSON.parse(texto); } catch (_) { datos = texto; }
          }
          if (!respuesta.ok) throw new ApiError(respuesta.status, mensajeDe(respuesta.status, datos), datos);
          return datos;
        });
      }, function (e) {
        clearTimeout(reloj);
        if (e && e.name === 'AbortError') throw new ApiError(0, 'Tiempo agotado: el servicio no respondió.');
        /* Un fallo de red o de CORS llega aquí: el navegador no deja leer la respuesta. */
        throw new ApiError(0, 'No se pudo conectar con el API. Revisa apiBaseUrl y la configuración de CORS.');
      });
    });
  }

  window.RX = {
    ApiError: ApiError,
    get: function (ruta, query) { return peticion('GET', ruta, { query: query }); },
    post: function (ruta, cuerpo, query) { return peticion('POST', ruta, { cuerpo: cuerpo === undefined ? {} : cuerpo, query: query }); },
    put: function (ruta, cuerpo) { return peticion('PUT', ruta, { cuerpo: cuerpo }); },
    del: function (ruta, query) { return peticion('DELETE', ruta, { query: query }); },
  };

  /* ------------------------------------------------------------------ Autenticación */

  var msal = null;

  function cuentaActiva() {
    return msal ? (msal.getActiveAccount() || msal.getAllAccounts()[0] || null) : null;
  }

  function cargarScript(url) {
    return new Promise(function (resolver, rechazar) {
      var s = document.createElement('script');
      s.src = url;
      s.onload = resolver;
      s.onerror = function () { rechazar(new Error('No se pudo cargar MSAL desde el CDN.')); };
      document.head.appendChild(s);
    });
  }

  /* Devuelve { modo, redirigiendo }. En modo msal, si no hay sesión, redirige a Microsoft y no vuelve aquí. */
  function iniciarAutenticacion() {
    if (cfg.authMode !== 'msal') return Promise.resolve({ modo: 'none', redirigiendo: false });

    var m = cfg.msal;
    if (!m.clientId || !m.authority || !m.scope) {
      return Promise.reject(new Error('Falta configurar msal (clientId, authority y scope) en config.js.'));
    }
    var origen = m.redirectUri || window.location.origin;

    return cargarScript(MSAL_URL).then(function () {
      msal = new window.msal.PublicClientApplication({
        auth: { clientId: m.clientId, authority: m.authority, redirectUri: origen, postLogoutRedirectUri: origen },
        cache: { cacheLocation: 'sessionStorage' },
      });
      return msal.initialize();
    }).then(function () {
      return msal.handleRedirectPromise();
    }).then(function (resultado) {
      if (resultado && resultado.account) msal.setActiveAccount(resultado.account);
      if (!cuentaActiva()) {
        return msal.loginRedirect({ scopes: [m.scope] }).then(function () { return { modo: 'msal', redirigiendo: true }; });
      }
      msal.setActiveAccount(cuentaActiva());
      proveedorToken = function () {
        var cuenta = cuentaActiva();
        if (!cuenta) return Promise.resolve(null);
        return msal.acquireTokenSilent({ scopes: [m.scope], account: cuenta }).then(
          function (r) { return r.accessToken; },
          function () { return msal.acquireTokenRedirect({ scopes: [m.scope], account: cuenta }).then(function () { return null; }); }
        );
      };
      return { modo: 'msal', redirigiendo: false };
    });
  }

  /* Vencimiento del token (solo modo msal): se lee del payload del access token, sin validar la firma en el navegador. */
  function vencimientoToken() {
    return proveedorToken().then(function (token) {
      if (!token) return null;
      try {
        var payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        return payload.exp ? new Date(payload.exp * 1000) : null;
      } catch (_) { return null; }
    });
  }

  function cerrarSesion() {
    if (msal) msal.logoutRedirect({ account: cuentaActiva() });
  }

  window.RXAuth = {
    iniciarAutenticacion: iniciarAutenticacion,
    vencimientoToken: vencimientoToken,
    cerrarSesion: cerrarSesion,
  };
})();
