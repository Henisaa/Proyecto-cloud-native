/* RutaExpress Console v2: interfaz sin frameworks. Usa api.js (RX, RXAuth) y config.js. */
(function () {
  'use strict';

  var cfg = window.RUTAEXPRESS_CONFIG;
  var RX = window.RX;
  var RXAuth = window.RXAuth;

  /* ------------------------------------------------------------------ Utilidades */

  function $(sel) { return document.querySelector(sel); }
  function $$(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

  var ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  function esc(v) {
    return String(v === undefined || v === null ? '' : v).replace(/[&<>"']/g, function (c) { return ESC[c]; });
  }
  function num(v) { return Number(v || 0).toLocaleString('es-CL'); }
  function dec(v, d) {
    d = d === undefined ? 2 : d;
    return Number(v || 0).toLocaleString('es-CL', { minimumFractionDigits: d, maximumFractionDigits: d });
  }
  function dinero(v) { return v === null || v === undefined ? '—' : '$' + Math.round(Number(v)).toLocaleString('es-CL'); }
  function fecha(iso) {
    if (!iso) return '—';
    var d = new Date(String(iso).replace(/(\.\d{3})\d+/, '$1'));
    if (isNaN(d.getTime())) return String(iso);
    return d.toLocaleString('es-CL', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
  function tamano(bytes) {
    if (bytes === null || bytes === undefined) return '—';
    var unidades = ['B', 'KB', 'MB', 'GB', 'TB'];
    var n = Number(bytes);
    var i = 0;
    while (n >= 1024 && i < unidades.length - 1) { n /= 1024; i++; }
    return dec(n, i > 1 ? 1 : 0) + ' ' + unidades[i];
  }
  function duracion(ms) {
    if (ms === null || ms === undefined) return '—';
    var s = Math.floor(ms / 1000);
    var d = Math.floor(s / 86400);
    var h = Math.floor((s % 86400) / 3600);
    var m = Math.floor((s % 3600) / 60);
    return (d ? d + 'd ' : '') + (d || h ? h + 'h ' : '') + m + 'm';
  }
  function insignia(texto, clases) {
    return '<span class="text-[10px] font-mono uppercase px-2 py-0.5 rounded-xs border whitespace-nowrap ' + clases + '">' + esc(texto) + '</span>';
  }
  function boton(accion, datos, texto, clases) {
    var attrs = Object.keys(datos).map(function (k) { return ' data-' + k + '="' + esc(datos[k]) + '"'; }).join('');
    return '<button type="button" data-accion="' + accion + '"' + attrs + ' class="text-xs px-2.5 py-1 rounded-xs whitespace-nowrap transition-colors ' + clases + '">' + esc(texto) + '</button>';
  }
  function vacioFila(columnas, texto) {
    return '<tr><td colspan="' + columnas + '" class="px-4 py-6 text-center text-zinc-500">' + esc(texto || 'Sin elementos.') + '</td></tr>';
  }
  function uuid() {
    if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = Math.random() * 16 | 0;
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
  }
  function debounce(fn, ms) {
    var t = null;
    return function () {
      clearTimeout(t);
      t = setTimeout(fn, ms);
    };
  }

  /* ------------------------------------------------------------------ Textos y permisos */

  var ESTADO_TEXTO = { CREADO: 'Creado', ACEPTADO: 'Aceptado', EN_BODEGA: 'En bodega', EN_RUTA: 'En ruta', ENTREGADO: 'Entregado', CANCELADO: 'Cancelado' };
  var ACCION_TEXTO = { ACEPTADO: 'Aceptar', EN_BODEGA: 'Enviar a bodega', EN_RUTA: 'Despachar a ruta', ENTREGADO: 'Marcar entregado', CANCELADO: 'Cancelar envío' };
  var ZONAS = ['URBAN_CENTER', 'URBAN_PERIPHERY', 'RURAL', 'INTERURBAN'];
  var ROLES = ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR'];
  var VISTAS = ['panel', 'envios', 'catalogo', 'reportes', 'auditoria', 'rabbitmq', 'kafka'];
  var RTABS = ['resumen', 'colas', 'exchanges', 'bindings', 'retencion', 'pruebas'];

  /* Misma matriz que el BFF (plan, §1.3). En modo sin seguridad solo decide lo que se muestra. */
  var PERMISOS = {
    panel: ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR'],
    envios: ['ADMIN', 'DESPACHADOR', 'CLIENTE'],
    crearEnvio: ['ADMIN', 'DESPACHADOR', 'CLIENTE'],
    cambiarEstado: ['ADMIN', 'DESPACHADOR'],
    timeline: ['ADMIN', 'AUDITOR'],
    catalogo: ['ADMIN', 'DESPACHADOR'],
    editarCapacidad: ['ADMIN'],
    reportes: ['ADMIN'],
    auditoria: ['ADMIN', 'AUDITOR'],
    rabbitmq: ['ADMIN'],
    kafka: ['ADMIN'],
    estadoServicios: ['ADMIN'],
  };

  function puede(permiso) {
    return (PERMISOS[permiso] || []).some(function (r) { return estado.roles.indexOf(r) !== -1; });
  }

  function estiloEstado(s) {
    if (s === 'ENTREGADO') return 'bg-white text-black border-white font-semibold';
    if (s === 'CANCELADO') return 'border-dashed border-zinc-700 text-zinc-500';
    return 'bg-white/[0.05] border-white/[0.1] text-zinc-300';
  }
  function estiloCapacidad(c) {
    if (c === 'RESERVADA' || c === 'LIBERADA') return 'border-white/[0.1] bg-white/[0.05] text-zinc-200';
    if (c === 'RECHAZADA') return 'border-rose-900 text-rose-300';
    return 'border-transparent text-zinc-500';
  }

  /* ------------------------------------------------------------------ Estado */

  var estado = {
    perfil: null,
    roles: [],
    vista: 'panel',
    timers: {},
    servicios: [],
    envios: { page: 0, size: 10, totalPages: 0, totalElements: 0, items: [], q: '', estado: '', capacidad: '' },
    detalle: null,
    capacidadEditandoId: null,
    auditoria: null,
    mq: { tab: 'resumen', exchanges: [], colas: [], bindings: [], politicas: [], muestras: [] },
  };

  /* ------------------------------------------------------------------ Mensajes, modales y botones */

  var toastTimer = null;
  function toast(texto, error) {
    var el = $('#toast');
    $('#toast-texto').textContent = texto;
    $('#toast-icono').className = 'ti ' + (error ? 'ti-alert-circle' : 'ti-check');
    el.className = 'fixed bottom-5 right-5 z-50 bg-[#1A1A1A] border text-xs px-4 py-2.5 rounded-sm shadow-2xl flex items-center gap-2 max-w-md ' +
      (error ? 'border-rose-900 text-rose-200' : 'border-white/[0.1] text-white');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.add('hidden'); }, error ? 6000 : 2500);
  }
  function abrirModal(id) { $('#' + id).classList.remove('hidden'); }
  function cerrarModal(id) { $('#' + id).classList.add('hidden'); }
  function mostrarAviso(sel, texto) { var el = $(sel); el.textContent = texto; el.classList.remove('hidden'); }
  function ocultarAviso(sel) { $(sel).classList.add('hidden'); }

  var accionConfirmada = null;
  function confirmar(texto, fn, etiqueta) {
    $('#confirmar-texto').textContent = texto;
    $('#confirmar-si').textContent = etiqueta || 'Sí';
    accionConfirmada = fn;
    abrirModal('modal-confirmar');
  }

  /* Ejecuta una acción con el botón deshabilitado. Nunca lanza: devuelve { ok, valor } o { ok: false, error }. */
  function ejecutar(boton, fn) {
    if (boton) boton.disabled = true;
    return Promise.resolve().then(fn).then(
      function (valor) { if (boton) boton.disabled = false; return { ok: true, valor: valor }; },
      function (error) { if (boton) boton.disabled = false; return { ok: false, error: error }; }
    );
  }

  function limpiarTimers() {
    Object.keys(estado.timers).forEach(function (k) { clearInterval(estado.timers[k]); delete estado.timers[k]; });
  }

  /* ------------------------------------------------------------------ Sesión y navegación */

  function devRol() {
    var guardado = null;
    try { guardado = sessionStorage.getItem('rx-dev-rol'); } catch (_) { guardado = null; }
    if (ROLES.indexOf(guardado) !== -1) return guardado;
    return ROLES.indexOf(cfg.devRole) !== -1 ? cfg.devRole : 'ADMIN';
  }

  function cargarPerfil() {
    return RX.get('/api/bff/auth/me').then(
      function (p) { estado.perfil = p; },
      function () { estado.perfil = null; }
    ).then(function () {
      if (cfg.authMode === 'msal') {
        estado.roles = ((estado.perfil && estado.perfil.roles) || []).map(RXRoles.normalizar).filter(Boolean);
      } else {
        estado.roles = [devRol()];
      }
    });
  }

  function nombreVisible() {
    if (cfg.authMode !== 'msal') return 'Desarrollo · ' + estado.roles.join(', ');
    var p = estado.perfil || {};
    return p.name || p.username || 'Sesión';
  }

  function aplicarPermisos() {
    $$('[data-perm]').forEach(function (el) {
      el.classList.toggle('hidden', !puede(el.getAttribute('data-perm')));
    });
    var herramientas = ['reportes', 'auditoria', 'rabbitmq', 'kafka'].some(puede);
    $('#grupo-herramientas').classList.toggle('hidden', !herramientas);
    $('#sesion-resumen').textContent = nombreVisible();
  }

  function saludo() {
    var ahora = new Date();
    var h = ahora.getHours() + ahora.getMinutes() / 60;
    var texto = 'Buenas noches';
    if (h >= 5 && h <= 7) texto = 'Buen día, madrugador';
    else if (h > 7 && h <= 12) texto = 'Buen día';
    else if (h > 12 && h <= 19.5) texto = 'Buenas tardes';
    var p = estado.perfil || {};
    var nombre = (p.name || p.username || '').split(' ')[0];
    if (!nombre || p.userId === 'anonimo' || p.username === 'usuario-invitado') nombre = 'equipo';
    $('#dynamic-greeting').textContent = texto + ', ' + nombre + '!';
  }

  function vistaDesdeHash() {
    var h = String(location.hash || '').replace(/^#\/?/, '').split('/')[0];
    return VISTAS.indexOf(h) !== -1 ? h : 'panel';
  }

  function moverIndicador() {
    var ind = $('#nav-indicator');
    var activo = document.querySelector('[data-nav].text-white');
    if (!ind || !activo || activo.classList.contains('hidden')) {
      if (ind) ind.style.opacity = '0';
      return;
    }
    ind.style.opacity = '1';
    ind.style.top = activo.offsetTop + 'px';
    ind.style.height = activo.offsetHeight + 'px';
  }

  function mostrarVista(vista) {
    if (!puede(vista)) {
      toast('Tu rol no tiene acceso a esta sección.', true);
      if (!puede('panel')) return;
      vista = 'panel';
    }
    limpiarTimers();
    estado.vista = vista;
    VISTAS.forEach(function (v) {
      var sec = document.getElementById('view-' + v);
      if (sec) sec.classList.toggle('hidden', v !== vista);
    });
    $$('[data-nav]').forEach(function (a) {
      var activo = a.getAttribute('data-nav') === vista;
      a.classList.toggle('text-white', activo);
      a.classList.toggle('bg-white/[0.07]', activo);
      a.classList.toggle('text-zinc-400', !activo);
    });
    moverIndicador();
    cargarVista(vista);
  }

  function cargarVista(vista) {
    if (vista === 'panel') {
      cargarPanel();
      estado.timers.panel = setInterval(cargarPanel, 30000);
    } else if (vista === 'envios') {
      cargarEnvios();
      actualizarBadge();
    } else if (vista === 'catalogo') {
      cargarCatalogo();
    } else if (vista === 'reportes') {
      cargarReportes();
    } else if (vista === 'rabbitmq') {
      cargarMq(estado.mq.tab);
    } else if (vista === 'kafka') {
      cargarKafka();
    }
  }

  /* ------------------------------------------------------------------ Panel */

  function cargarPanel() {
    var tareas = [];
    if (puede('envios')) tareas.push(RX.get('/api/bff/shipments/summary').then(pintarPanelEnvios, function (e) {
      $('#kpi-envios-sub').textContent = e.message;
    }));

    if (puede('reportes')) {
      tareas.push(RX.get('/api/bff/report/kpis', { range: 'last24h' }).then(pintarPanelIndicadores, function (e) {
        $('#panel-indicadores').innerHTML = '<p class="col-span-3 text-zinc-400">' + esc(e.message) + '</p>';
      }));
    }
    if (puede('rabbitmq')) {
      tareas.push(Promise.all([RX.get('/api/admin/mq/metrics'), RX.get('/api/admin/mq/cluster')]).then(
        function (r) { pintarPanelMq(r[0], r[1]); },
        function (e) { $('#kpi-mq-sub').textContent = e.message; }
      ));
    } else {
      $('#kpi-mq').textContent = '—';
      $('#kpi-mq-sub').textContent = 'Solo administradores';
    }
    if (puede('kafka')) {
      tareas.push(cargarLagPanel());
    } else {
      $('#kpi-lag').textContent = '—';
      $('#kpi-lag-sub').textContent = 'Solo administradores';
    }
    if (puede('estadoServicios')) {
      tareas.push(RX.get('/api/bff/status').then(pintarServicios, function (e) {
        $('#panel-servicios').innerHTML = '<li class="py-2 text-zinc-400">' + esc(e.message) + '</li>';
      }));
    }
    return Promise.all(tareas);
  }

  function pintarPanelEnvios(r) {
    var bs = r.byStatus || {};
    var entregados = bs.ENTREGADO || 0;
    var cancelados = bs.CANCELADO || 0;
    var enCurso = Math.max(0, (r.total || 0) - entregados - cancelados);
    $('#kpi-envios').textContent = num(enCurso);
    $('#kpi-envios-sub').textContent = num(bs.CREADO || 0) + ' creados · ' + num(bs.EN_RUTA || 0) + ' en ruta · ' + num(entregados) + ' entregados';
    $('#badge-envios-count').textContent = num(r.total);
  }

  function pintarPanelIndicadores(k) {
    $('#panel-indicadores').innerHTML =
      '<div><p class="text-[10px] uppercase tracking-wider text-zinc-500">Envíos</p><p class="font-mono text-xl font-bold text-white mt-1">' + num(k.totalShipments) + '</p></div>' +
      '<div><p class="text-[10px] uppercase tracking-wider text-zinc-500">Por hora</p><p class="font-mono text-xl font-bold text-white mt-1">' + dec(k.shipmentsPerHour) + '</p></div>' +
      '<div><p class="text-[10px] uppercase tracking-wider text-zinc-500">Entrega promedio</p><p class="font-mono text-xl font-bold text-white mt-1">' + dec(k.avgLeadTimeMinutes, 1) + ' min</p></div>';
  }

  function pintarPanelMq(m, nodos) {
    var lista = nodos || [];
    var arriba = lista.filter(function (n) { return n.running; }).length;
    $('#kpi-mq').textContent = num(m.activeQueues) + ' colas';
    $('#kpi-mq-sub').textContent = arriba + ' de ' + lista.length + ' nodos operando · ' + num(m.dlqMessages) + ' mensajes en DLQ';
  }

  function cargarLagPanel() {
    return Promise.allSettled(cfg.kafkaGroups.map(function (g) {
      return RX.get('/api/admin/kafka/groups/' + encodeURIComponent(g) + '/lag');
    })).then(function (resultados) {
      var total = 0;
      var conRetraso = 0;
      var ok = 0;
      resultados.forEach(function (r) {
        if (r.status !== 'fulfilled') return;
        ok++;
        (r.value || []).forEach(function (p) {
          total += Number(p.lag || 0);
          if (p.lag > 0) conRetraso++;
        });
      });
      if (!ok) {
        $('#kpi-lag').textContent = '—';
        $('#kpi-lag-sub').textContent = 'Sin datos de los grupos configurados';
        return;
      }
      $('#kpi-lag').innerHTML = num(total) + ' <span class="text-sm font-normal text-zinc-500">mensajes</span>';
      $('#kpi-lag-sub').textContent = conRetraso ? conRetraso + ' particiones con retraso' : 'Sincronización al día en todas las particiones';
    });
  }

  function pintarServicios(s) {
    var servicios = s.services || {};
    $('#panel-servicios').innerHTML = Object.keys(servicios).map(function (k) {
      var up = servicios[k] === 'UP';
      return '<li class="py-2 flex items-center justify-between">' +
        '<span class="font-mono text-zinc-300">' + esc(k) + '</span>' +
        '<span class="flex items-center gap-2 ' + (up ? 'text-zinc-300' : 'text-rose-300') + '">' +
        '<span class="w-1.5 h-1.5 rounded-full ' + (up ? 'bg-white' : 'bg-rose-400') + '"></span>' + esc(servicios[k]) + '</span></li>';
    }).join('') || '<li class="py-2 text-zinc-500">Sin datos</li>';
  }

  /* ------------------------------------------------------------------ Envíos */

  function cargarEnvios() {
    var e = estado.envios;
    return RX.get('/api/bff/shipments', { page: e.page, size: e.size, q: e.q, status: e.estado, capacity: e.capacidad })
      .then(function (pag) {
        e.items = pag.content || [];
        e.totalPages = pag.totalPages || 0;
        e.totalElements = pag.totalElements || 0;
        pintarEnvios();
        $('#envios-total-label').textContent = num(e.totalElements);
      })
      .catch(function (err) { toast(err.message, true); });
  }

  function actualizarBadge() {
    return RX.get('/api/bff/shipments/summary').then(function (r) {
      $('#badge-envios-count').textContent = num(r.total);
    }, function () { /* el contador es informativo: si falla, no se muestra error */ });
  }

  function refrescarEnvios() {
    return Promise.all([cargarEnvios(), actualizarBadge()]);
  }

  function pintarEnvios() {
    var e = estado.envios;
    $('#envios-tbody').innerHTML = e.items.map(filaEnvio).join('');
    $('#envios-vacio').classList.toggle('hidden', e.items.length > 0);
    $('#envios-pagina-info').textContent = 'Página ' + (e.page + 1) + ' de ' + Math.max(1, e.totalPages) + ' · ' + num(e.totalElements) + ' envíos';
    $('#btn-envios-ant').disabled = e.page <= 0;
    $('#btn-envios-sig').disabled = e.page >= e.totalPages - 1;
  }

  function filaEnvio(x) {
    var acciones = [];
    if (puede('cambiarEstado')) {
      (x.allowedTransitions || []).forEach(function (t) {
        if (t === 'CANCELADO') {
          acciones.push(boton('cancelar', { id: x.id, codigo: x.trackingCode }, ACCION_TEXTO[t], 'border border-white/[0.08] text-zinc-400 hover:text-white'));
        } else {
          acciones.push(boton('estado', { id: x.id, codigo: x.trackingCode, estado: t }, ACCION_TEXTO[t] || t, 'bg-white text-black font-semibold hover:bg-zinc-200'));
        }
      });
      if (x.canRetryCapacity) {
        acciones.push(boton('reintentarCupo', { id: x.id }, 'Reintentar cupo', 'border border-white/[0.08] text-zinc-300 hover:text-white'));
      }
    }
    if (puede('timeline')) {
      acciones.push(boton('timeline', { id: x.id, codigo: x.trackingCode }, 'Línea de tiempo', 'border border-white/[0.08] text-zinc-400 hover:text-white'));
    }
    return '<tr class="hover:bg-white/[0.01] transition-colors">' +
      '<td class="px-4 py-3 font-mono font-semibold text-white whitespace-nowrap">' + esc(x.trackingCode) + '</td>' +
      '<td class="px-4 py-3 font-mono text-zinc-400">' + esc(x.serviceCode || x.serviceType || x.serviceId) + '</td>' +
      '<td class="px-4 py-3 text-zinc-400">' + esc(x.zone || '—') + '</td>' +
      '<td class="px-4 py-3">' + insignia(ESTADO_TEXTO[x.status] || x.status, estiloEstado(x.status)) + '</td>' +
      '<td class="px-4 py-3">' + insignia(x.capacityStatus, estiloCapacidad(x.capacityStatus)) + '</td>' +
      '<td class="px-4 py-3 text-zinc-300">' + esc(x.recipientName) + '</td>' +
      '<td class="px-4 py-3 text-zinc-400">' + esc(x.destinationAddress) + '</td>' +
      '<td class="px-4 py-3 text-zinc-400 whitespace-nowrap">' + num(x.packagesCount) + ' pq · ' + dec(x.weightKg, 1) + ' kg</td>' +
      '<td class="px-4 py-3 text-zinc-500 font-mono text-[11px] whitespace-nowrap">' + fecha(x.updatedAt) + '</td>' +
      '<td class="px-4 py-3"><div class="flex items-center justify-end gap-1.5 flex-wrap">' + acciones.join('') + '</div></td>' +
      '</tr>';
  }

  function cambiarEstado(id, destino, boton, codigo) {
    return ejecutar(boton, function () {
      return RX.put('/api/bff/shipments/' + encodeURIComponent(id) + '/status', { status: destino });
    }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      toast('Envío ' + (codigo || '') + ' · ' + (ESTADO_TEXTO[destino] || destino));
      return refrescarEnvios();
    });
  }

  function reintentarCupo(id, boton) {
    return ejecutar(boton, function () {
      return RX.post('/api/bff/shipments/' + encodeURIComponent(id) + '/capacity/retry', {});
    }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      toast('Cupo reenviado al catálogo');
      return refrescarEnvios();
    });
  }

  function filaEvento(ev) {
    var idJson = 'json-' + Math.random().toString(36).slice(2, 10);
    return '<tr class="align-top hover:bg-white/[0.01]">' +
      '<td class="px-3 py-2.5 font-mono text-zinc-400 whitespace-nowrap">' + fecha(ev.occurredAt) + '</td>' +
      '<td class="px-3 py-2.5">' + insignia(ev.source, 'border-white/[0.1] bg-white/[0.05] text-zinc-300') + '</td>' +
      '<td class="px-3 py-2.5 font-mono text-zinc-300">' + esc(ev.eventType || '—') + '</td>' +
      '<td class="px-3 py-2.5 font-mono text-zinc-400 text-[11px]">' + esc(ev.routingKey || '—') + '</td>' +
      '<td class="px-3 py-2.5 font-mono text-zinc-300">' + esc(ev.actor || '—') + '</td>' +
      '<td class="px-3 py-2.5 text-zinc-400"><div>' + esc(ev.detail || '—') + '</div>' +
      '<button type="button" data-accion="json" data-target="' + idJson + '" class="text-[11px] text-zinc-300 hover:text-white underline mt-1">Ver JSON</button>' +
      '<pre id="' + idJson + '" class="hidden mt-2 p-3 bg-[#151515] border border-white/[0.06] rounded-xs font-mono text-[11px] text-zinc-300 overflow-x-auto">' + esc(JSON.stringify(ev, null, 2)) + '</pre></td>' +
      '</tr>';
  }

  function abrirTimeline(id, codigo) {
    $('#timeline-titulo').textContent = 'Línea de tiempo · ' + codigo;
    ocultarAviso('#timeline-aviso');
    $('#timeline-tbody').innerHTML = vacioFila(6, 'Cargando…');
    abrirModal('modal-timeline');
    return RX.get('/api/bff/shipments/' + encodeURIComponent(id) + '/timeline').then(function (linea) {
      $('#timeline-tbody').innerHTML = (linea.events || []).map(filaEvento).join('') || vacioFila(6, 'Sin eventos para este envío.');
      if (!linea.kafkaAvailable) mostrarAviso('#timeline-aviso', 'Auditoría de Kafka no disponible: solo se muestran eventos de RabbitMQ.');
    }, function (e) {
      $('#timeline-tbody').innerHTML = '<tr><td colspan="6" class="px-3 py-6 text-center text-rose-300">' + esc(e.message) + '</td></tr>';
    });
  }

  function abrirNuevoEnvio() {
    ocultarAviso('#env-error');
    $('#form-envio').reset();
    $('#env-zona').innerHTML = ZONAS.map(function (z) { return '<option value="' + z + '">' + z + '</option>'; }).join('');
    var carga = estado.servicios.length ? Promise.resolve(estado.servicios) : RX.get('/api/bff/catalog/services');
    return carga.then(function (lista) {
      estado.servicios = lista || [];
      $('#env-servicio').innerHTML = estado.servicios.filter(function (s) { return s.active; }).map(function (s) {
        return '<option value="' + esc(s.id) + '">' + esc(s.code) + ' · ' + esc(s.name) + '</option>';
      }).join('');
      abrirModal('modal-envio');
    }).catch(function (e) { toast(e.message, true); });
  }

  function enviarNuevoEnvio(ev) {
    var precio = $('#env-precio').value;
    var cuerpo = {
      serviceId: Number($('#env-servicio').value),
      zone: $('#env-zona').value,
      packagesCount: Number($('#env-paquetes').value),
      weightKg: Number($('#env-peso').value),
      volumeM3: Number($('#env-volumen').value),
      recipientName: $('#env-destinatario').value.trim(),
      recipientEmail: $('#env-email').value.trim() || null,
      recipientPhone: $('#env-telefono').value.trim() || null,
      originAddress: $('#env-origen').value.trim(),
      destinationAddress: $('#env-destino').value.trim(),
      price: precio ? Number(precio) : null,
    };
    return ejecutar(ev.submitter, function () { return RX.post('/api/bff/shipments', cuerpo); }).then(function (r) {
      if (!r.ok) {
        $('#env-error').textContent = r.error.message;
        $('#env-error').classList.remove('hidden');
        return;
      }
      cerrarModal('modal-envio');
      toast('Envío creado: ' + (r.valor && r.valor.trackingCode ? r.valor.trackingCode : ''));
      return refrescarEnvios();
    });
  }

  function pedirCancelar(id, codigo) {
    confirmar('¿Cancelar el envío ' + codigo + '? Se libera el cupo reservado.', function () {
      return cambiarEstado(id, 'CANCELADO', null, codigo);
    }, 'Sí, cancelar');
  }

  /* ------------------------------------------------------------------ Catálogo */

  function cargarCatalogo() {
    return RX.get('/api/bff/catalog/services').then(function (lista) {
      estado.servicios = lista || [];
      $('#catalogo-tbody').innerHTML = estado.servicios.map(filaServicio).join('') || vacioFila(8, 'Sin servicios en el catálogo.');
      mostrarCatalogoLista();
    }).catch(function (e) { toast(e.message, true); });
  }

  function mostrarCatalogoLista() {
    $('#catalogo-lista').classList.remove('hidden');
    $('#catalogo-detalle').classList.add('hidden');
  }

  function filaServicio(s) {
    return '<tr class="hover:bg-white/[0.01] transition-colors">' +
      '<td class="px-4 py-3 font-mono font-semibold text-white">' + esc(s.code) + '</td>' +
      '<td class="px-4 py-3 text-zinc-200">' + esc(s.name) + '</td>' +
      '<td class="px-4 py-3 font-mono text-zinc-400">' + esc(s.category) + '</td>' +
      '<td class="px-4 py-3">' + insignia(s.active ? 'Activo' : 'Inactivo', s.active ? 'border-white/[0.1] bg-white/[0.05] text-zinc-300' : 'border-dashed border-zinc-700 text-zinc-500') + '</td>' +
      '<td class="px-4 py-3 font-mono text-white">' + dinero(s.basePrice) + '</td>' +
      '<td class="px-4 py-3 font-mono text-zinc-300">' + num(s.estimatedDeliveryHours) + ' h</td>' +
      '<td class="px-4 py-3 font-mono text-zinc-300">' + num(s.availableCapacitiesCount) + '</td>' +
      '<td class="px-4 py-3 text-right">' + boton('detalle', { id: s.id }, 'Ver detalle', 'border border-white/[0.08] hover:bg-white/[0.04] text-zinc-300') + '</td>' +
      '</tr>';
  }

  function abrirDetalle(id) {
    return RX.get('/api/bff/catalog/services/' + encodeURIComponent(id)).then(function (d) {
      estado.detalle = d;
      pintarDetalle();
      $('#catalogo-lista').classList.add('hidden');
      $('#catalogo-detalle').classList.remove('hidden');
    }).catch(function (e) { toast(e.message, true); });
  }

  function recargarDetalle() {
    if (!estado.detalle) return Promise.resolve();
    return abrirDetalle(estado.detalle.id);
  }

  function pintarDetalle() {
    var d = estado.detalle;
    if (!d) return;
    $('#detalle-titulo').textContent = d.code + ' · ' + d.name;
    $('#detalle-descripcion').textContent = d.description || '';
    $('#detalle-resumen').textContent = 'Capacidad total ' + num(d.totalDailyCapacity) + ' · reservados ' + num(d.totalBookedPackages) +
      ' · utilización ' + dec(d.overallUtilizationPercentage) + ' %';
    var caps = d.capacities;
    $('#detalle-capacidades-caja').classList.toggle('hidden', !caps);
    if (caps) {
      $('#detalle-capacidades').innerHTML = caps.map(filaCapacidad).join('') || vacioFila(9, 'Sin capacidades configuradas.');
    }
    $('#detalle-tarifas').innerHTML = (d.tariffs || []).map(filaTarifa).join('') || vacioFila(7, 'Sin tarifas configuradas.');
  }

  function filaCapacidad(c) {
    var editable = puede('editarCapacidad');
    return '<tr>' +
      '<td class="px-3 py-2.5 font-mono text-white">' + esc(c.zone) + '</td>' +
      '<td class="px-3 py-2.5 font-mono text-zinc-300">' + esc(c.vehicleType) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + num(c.maxDailyPackages) + '</td>' +
      '<td class="px-3 py-2.5 font-mono text-zinc-400">' + num(c.currentBookedPackages) + '</td>' +
      '<td class="px-3 py-2.5 font-mono font-semibold text-white">' + num(c.availablePackages) + '</td>' +
      '<td class="px-3 py-2.5 text-zinc-400">' + dec(c.maxWeightKg, 1) + ' kg</td>' +
      '<td class="px-3 py-2.5 text-zinc-400">' + dec(c.maxVolumeM3, 2) + ' m³</td>' +
      '<td class="px-3 py-2.5"><input type="checkbox" data-accion="activar" data-cap="' + esc(c.id) + '"' +
        (c.active ? ' checked' : '') + (editable ? '' : ' disabled') + ' class="rounded-xs cursor-pointer disabled:cursor-not-allowed"></td>' +
      '<td class="px-3 py-2.5 text-right">' + (editable
        ? boton('editarCapacidad', { cap: c.id }, 'Editar máximo', 'border border-white/[0.08] hover:bg-white/[0.04] text-zinc-300')
        : '<span class="text-zinc-600">—</span>') + '</td>' +
      '</tr>';
  }

  function filaTarifa(t) {
    return '<tr>' +
      '<td class="px-3 py-2.5 font-mono">' + esc(t.originZone) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + esc(t.destinationZone) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + dinero(t.baseFare) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + dinero(t.perKmRate) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + dinero(t.perKgRate) + '</td>' +
      '<td class="px-3 py-2.5 font-mono">' + dinero(t.minFare) + '</td>' +
      '<td class="px-3 py-2.5">' + insignia(t.active ? 'Sí' : 'No', t.active ? 'border-white/[0.1] bg-white/[0.05] text-zinc-300' : 'border-dashed border-zinc-700 text-zinc-500') + '</td>' +
      '</tr>';
  }

  function abrirEditarCapacidad(capId) {
    var caps = (estado.detalle && estado.detalle.capacities) || [];
    var c = caps.filter(function (x) { return String(x.id) === String(capId); })[0];
    if (!c) return;
    estado.capacidadEditandoId = c.id;
    $('#cap-etiqueta').textContent = c.zone + ' · ' + c.vehicleType;
    $('#cap-valor').value = c.maxDailyPackages;
    abrirModal('modal-capacidad');
  }

  function guardarCapacidad(boton) {
    var valor = parseInt($('#cap-valor').value, 10);
    if (isNaN(valor) || valor < 0) return toast('Ingresa un número válido.', true);
    return ejecutar(boton, function () {
      return RX.put('/api/bff/catalog/manage/services/' + encodeURIComponent(estado.detalle.id) +
        '/capacities/' + encodeURIComponent(estado.capacidadEditandoId), { maxDailyPackages: valor });
    }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      cerrarModal('modal-capacidad');
      toast('Máximo diario guardado');
      return recargarDetalle();
    });
  }

  function cambiarActivaCapacidad(capId, activa) {
    return RX.put('/api/bff/catalog/manage/services/' + encodeURIComponent(estado.detalle.id) +
      '/capacities/' + encodeURIComponent(capId), { active: activa }).then(function () {
      toast('Capacidad ' + (activa ? 'activada' : 'desactivada'));
      return recargarDetalle();
    }, function (e) {
      toast(e.message, true);
      return recargarDetalle();
    });
  }

  /* ------------------------------------------------------------------ Reportes */

  function cargarReportes() {
    var rango = $('#reportes-rango').value;
    ocultarAviso('#reportes-aviso');
    return Promise.all([
      RX.get('/api/bff/report/kpis', { range: rango }),
      RX.get('/api/bff/report/top-services', { range: rango }),
    ]).then(function (r) {
      var k = r[0];
      var top = r[1] || [];
      $('#rep-total').textContent = num(k.totalShipments);
      $('#rep-hora').textContent = dec(k.shipmentsPerHour);
      $('#rep-promedio').innerHTML = dec(k.avgLeadTimeMinutes, 1) + ' <span class="text-sm font-normal text-zinc-400">min</span>';
      pintarEstadosReporte(k.activeShipmentsByStatus || {});
      $('#rep-top').innerHTML = top.map(function (t) {
        return '<tr><td class="px-3 py-2.5 font-mono font-semibold">' + esc(t.serviceType) + '</td>' +
          '<td class="px-3 py-2.5 font-mono">' + num(t.totalShipments) + '</td>' +
          '<td class="px-3 py-2.5 font-mono">' + dec(t.percentage) + ' %</td></tr>';
      }).join('') || vacioFila(3, 'Sin datos en el rango.');
    }).catch(function (e) {
      mostrarAviso('#reportes-aviso', e.message);
    });
  }

  function pintarEstadosReporte(porEstado) {
    var entradas = Object.keys(porEstado).map(function (k) { return [k, porEstado[k]]; });
    var total = entradas.reduce(function (a, p) { return a + Number(p[1] || 0); }, 0);
    $('#rep-estados').innerHTML = entradas.map(function (p) {
      var pct = total ? Math.round(Number(p[1]) * 100 / total) : 0;
      return '<div class="flex items-center gap-3 text-xs">' +
        '<span class="w-28 font-mono text-zinc-300">' + esc(ESTADO_TEXTO[p[0]] || p[0]) + '</span>' +
        '<div class="flex-1 bg-zinc-800 h-1.5 rounded-full overflow-hidden"><div class="bg-white h-full" style="width:' + pct + '%"></div></div>' +
        '<span class="w-20 text-right font-mono text-zinc-300">' + num(p[1]) + '</span></div>';
    }).join('') || '<p class="text-xs text-zinc-500">Sin envíos activos en el rango.</p>';
  }

  /* ------------------------------------------------------------------ Auditoría */

  function buscarAuditoria(ev) {
    var q = $('#auditoria-q').value.trim();
    if (!q) return toast('Escribe un código de seguimiento o un id de envío.', true);
    $('#auditoria-tbody').innerHTML = vacioFila(6, 'Buscando…');
    return RX.get('/api/bff/audit/resolve/' + encodeURIComponent(q)).then(function (coincidencia) {
      return RX.get('/api/bff/shipments/' + encodeURIComponent(coincidencia.id) + '/timeline').then(function (linea) {
        estado.auditoria = { envio: coincidencia, linea: linea };
        pintarAuditoria();
      });
    }).catch(function (e) {
      $('#auditoria-tbody').innerHTML = '';
      toast(e.message, true);
    });
  }

  function pintarAuditoria() {
    var a = estado.auditoria;
    if (!a) return;
    var e = a.envio;
    var resumen = $('#auditoria-resumen');
    resumen.classList.remove('hidden');
    resumen.innerHTML = '<span class="font-mono text-white font-semibold">' + esc(e.trackingCode) + '</span>';
    var fuente = $('#auditoria-fuente').value;
    var eventos = (a.linea.events || []).filter(function (ev) { return fuente === 'TODAS' || ev.source === fuente; });
    $('#auditoria-tbody').innerHTML = eventos.map(filaEvento).join('') || vacioFila(6, 'Sin eventos para este filtro.');
    if (!a.linea.kafkaAvailable) mostrarAviso('#auditoria-aviso', 'Auditoría de Kafka no disponible: se muestran solo eventos de RabbitMQ.');
    else ocultarAviso('#auditoria-aviso');
  }

  /* ------------------------------------------------------------------ RabbitMQ */

  var PLANTILLAS = {
    notificacion: {
      exchange: 'ex.rutaexpress.events',
      clave: 'notification.email.send',
      cuerpo: {
        type: 'notification.requested',
        eventId: 'REEMPLAZAR-UUID',
        timestamp: '2026-01-01T00:00:00Z',
        traceId: 'REEMPLAZAR-UUID',
        correlationId: 'REEMPLAZAR-UUID',
        payload: { shipmentId: 'RX-PRUEBA', recipientEmail: 'prueba@example.com', recipientName: 'Prueba', subject: 'Mensaje de prueba', message: 'Mensaje de prueba de la consola v2.' },
      },
    },
    'sin-event-id': {
      exchange: 'ex.rutaexpress.events',
      clave: 'notification.email.send',
      cuerpo: { type: 'notification.requested', payload: { recipientEmail: 'prueba@example.com' } },
    },
    invalido: {
      exchange: 'ex.rutaexpress.cmd',
      clave: 'capacity.reserve',
      cuerpo: '{no-es-json',
    },
  };

  function cargarMq(tab) {
    estado.mq.tab = tab;
    $$('[data-rtab]').forEach(function (b) {
      var on = b.getAttribute('data-rtab') === tab;
      b.classList.toggle('bg-white', on);
      b.classList.toggle('text-black', on);
      b.classList.toggle('text-zinc-400', !on);
      b.classList.toggle('hover:text-white', !on);
    });
    RTABS.forEach(function (t) { $('#rtab-' + t).classList.toggle('hidden', t !== tab); });
    if (tab === 'resumen') return cargarMqResumen();
    if (tab === 'colas') return cargarMqColas();
    if (tab === 'exchanges') return cargarMqExchanges();
    if (tab === 'bindings') return cargarMqBindings();
    if (tab === 'retencion') return cargarMqPoliticas();
    if (tab === 'pruebas') return cargarMqPruebas();
    return Promise.resolve();
  }

  function recargarMq() { return cargarMq(estado.mq.tab); }

  /* La API de gestión de RabbitMQ refresca sus contadores cada ~5 s: se vuelve a leer tras un breve retardo. */
  function refrescarMqTrasCambio() {
    recargarMq();
    setTimeout(recargarMq, 2000);
    setTimeout(recargarMq, 6000);
  }

  function cargarMqResumen() {
    return Promise.all([RX.get('/api/admin/mq/metrics'), RX.get('/api/admin/mq/cluster')]).then(function (r) {
      pintarNodos(r[1]);
      pintarMetricasMq(r[0]);
      registrarMuestra(r[0]);
      pintarGrafico();
    }).catch(function (e) {
      toast(e.message, true);
    }).then(function () {
      if (!estado.timers.mq) {
        estado.timers.mq = setInterval(function () {
          if (estado.vista === 'rabbitmq' && estado.mq.tab === 'resumen') cargarMqResumen();
        }, 15000);
      }
    });
  }

  function pintarNodos(nodos) {
    $('#mq-nodos').innerHTML = (nodos || []).map(function (n) {
      var pct = n.memLimit ? Math.min(100, Math.round(Number(n.memUsed) * 100 / Number(n.memLimit))) : 0;
      var alarma = [];
      if (n.memAlarm) alarma.push('memoria');
      if (n.diskFreeAlarm) alarma.push('disco');
      return '<div class="bg-[#1A1A1A] border border-white/[0.06] rounded-sm p-4">' +
        '<div class="flex items-center justify-between"><span class="text-sm font-bold font-mono text-white flex items-center gap-2"><i class="ti ti-cpu text-zinc-400"></i> ' + esc(n.name) + '</span>' +
        insignia(n.running ? 'En ejecución' : 'Detenido', n.running ? 'border-white/[0.1] bg-white/[0.05] text-zinc-300' : 'border-rose-900 text-rose-300') + '</div>' +
        '<div class="mt-4 flex items-baseline justify-between"><span class="text-xs text-zinc-400">Memoria</span><span class="text-xs font-mono font-semibold text-white">' + tamano(n.memUsed) + ' / ' + tamano(n.memLimit) + '</span></div>' +
        '<div class="w-full bg-zinc-800 h-1.5 rounded-full overflow-hidden mt-1.5"><div class="bg-white h-full" style="width:' + pct + '%"></div></div>' +
        '<div class="mt-3 flex items-center justify-between text-xs text-zinc-500 font-mono"><span>Disco libre</span><span>' + tamano(n.diskFree) + '</span></div>' +
        '<div class="mt-1 flex items-center justify-between text-xs text-zinc-500 font-mono"><span>Tiempo activo</span><span>' + duracion(n.uptimeMs) + '</span></div>' +
        (alarma.length ? '<p class="mt-2 text-[11px] text-rose-300">Alarma: ' + esc(alarma.join(', ')) + '</p>' : '') +
        '</div>';
    }).join('') || '<p class="text-xs text-zinc-500">Sin nodos.</p>';
  }

  function pintarMetricasMq(m) {
    var tarjetas = [
      ['Colas activas', m.activeQueues],
      ['Mensajes listos', m.messagesReady],
      ['Sin confirmar', m.messagesUnacknowledged],
      ['Consumidores', m.consumers],
      ['Colas DLQ', m.dlqQueues],
      ['En la DLQ ahora', m.dlqMessages],
      ['Llegados a DLQ', m.dlqArrivedTotal],
    ];
    $('#mq-metricas').innerHTML = tarjetas.map(function (t) {
      return '<div class="bg-[#1A1A1A] border border-white/[0.06] p-3 rounded-sm"><span class="text-[10px] uppercase tracking-wider text-zinc-400 block">' + esc(t[0]) +
        '</span><span class="text-xl font-bold font-mono text-white mt-1 block">' + num(t[1]) + '</span></div>';
    }).join('');
  }

  function registrarMuestra(m) {
    estado.mq.muestras.push(Number(m.messagesReady || 0) + Number(m.messagesUnacknowledged || 0));
    if (estado.mq.muestras.length > 40) estado.mq.muestras.shift();
  }

  function pintarGrafico() {
    var s = estado.mq.muestras;
    var max = Math.max(1, Math.max.apply(null, s));
    var puntos = s.map(function (v, i) {
      var x = s.length > 1 ? (i * 600 / (s.length - 1)) : 0;
      var y = 110 - (v / max) * 100;
      return x.toFixed(1) + ',' + y.toFixed(1);
    }).join(' ');
    $('#mq-linea').setAttribute('points', puntos);
    var ultimo = s.length ? s[s.length - 1] : 0;
    $('#mq-backlog-actual').textContent = 'Backlog actual: ' + num(ultimo);
  }

  function rellenarExchanges() {
    var opciones = estado.mq.exchanges.map(function (x) {
      return '<option value="' + esc(x.name) + '">' + esc(x.name) + ' (' + esc(x.type) + ')</option>';
    }).join('');
    $$('select[data-exchanges]').forEach(function (s) {
      var previo = s.value;
      s.innerHTML = (s.name === 'dlx' ? '<option value="">(ninguno)</option>' : '') + opciones;
      if (previo) s.value = previo;
    });
  }

  function rellenarDestinos() {
    var tipo = $('#form-binding select[name="tipoDestino"]').value;
    var nombres = tipo === 'queue'
      ? estado.mq.colas.map(function (q) { return q.name; })
      : estado.mq.exchanges.map(function (x) { return x.name; });
    $('#form-binding select[name="destino"]').innerHTML = nombres.map(function (n) {
      return '<option value="' + esc(n) + '">' + esc(n) + '</option>';
    }).join('');
  }

  function cargarMqColas() {
    return Promise.all([RX.get('/api/admin/mq/queues'), RX.get('/api/admin/mq/exchanges')]).then(function (r) {
      estado.mq.colas = r[0] || [];
      estado.mq.exchanges = r[1] || [];
      rellenarExchanges();
      $('#mq-colas-tbody').innerHTML = estado.mq.colas.map(filaCola).join('') || vacioFila(7, 'No hay colas declaradas.');
    }).catch(function (e) { toast(e.message, true); });
  }

  function filaCola(q) {
    return '<tr class="hover:bg-white/[0.01]">' +
      '<td class="px-3 py-2 font-mono font-semibold text-white">' + esc(q.name) + '</td>' +
      '<td class="px-3 py-2 font-mono">' + num(q.messagesReady) + '</td>' +
      '<td class="px-3 py-2 font-mono">' + num(q.unacknowledged) + '</td>' +
      '<td class="px-3 py-2 font-mono">' + num(q.consumers) + '</td>' +
      '<td class="px-3 py-2 font-mono">' + num(q.arrivedTotal) + '</td>' +
      '<td class="px-3 py-2">' + (q.alert ? insignia('Alerta', 'border-rose-900 text-rose-300') : insignia(q.declared ? 'OK' : 'No declarada', 'border-white/[0.1] text-zinc-300')) + '</td>' +
      '<td class="px-3 py-2 text-right">' + boton('borrarCola', { nombre: q.name }, 'Eliminar', 'border border-white/[0.08] text-zinc-400 hover:text-white') + '</td>' +
      '</tr>';
  }

  function cargarMqExchanges() {
    return RX.get('/api/admin/mq/exchanges').then(function (lista) {
      estado.mq.exchanges = lista || [];
      rellenarExchanges();
      $('#mq-exchanges-tbody').innerHTML = estado.mq.exchanges.map(function (x) {
        return '<tr class="hover:bg-white/[0.01]">' +
          '<td class="px-4 py-3 font-mono font-semibold text-white">' + esc(x.name) + '</td>' +
          '<td class="px-4 py-3 font-mono text-zinc-400">' + esc(x.type) + '</td>' +
          '<td class="px-4 py-3">' + (x.durable ? 'Sí' : 'No') + '</td>' +
          '<td class="px-4 py-3 text-right">' + boton('borrarExchange', { nombre: x.name }, 'Eliminar', 'border border-white/[0.08] text-zinc-400 hover:text-white') + '</td>' +
          '</tr>';
      }).join('') || vacioFila(4, 'No hay exchanges.');
    }).catch(function (e) { toast(e.message, true); });
  }

  function cargarMqBindings() {
    return Promise.all([RX.get('/api/admin/mq/bindings'), RX.get('/api/admin/mq/queues'), RX.get('/api/admin/mq/exchanges')]).then(function (r) {
      estado.mq.bindings = r[0] || [];
      estado.mq.colas = r[1] || [];
      estado.mq.exchanges = r[2] || [];
      rellenarExchanges();
      rellenarDestinos();
      $('#mq-bindings-tbody').innerHTML = estado.mq.bindings.map(function (b) {
        return '<tr class="hover:bg-white/[0.01]">' +
          '<td class="px-4 py-3 font-mono">' + esc(b.source) + '</td>' +
          '<td class="px-4 py-3 font-mono">' + esc(b.destination) + '</td>' +
          '<td class="px-4 py-3 text-zinc-400">' + esc(b.destinationType) + '</td>' +
          '<td class="px-4 py-3 font-mono text-zinc-300">' + esc(b.routingKey || '—') + '</td>' +
          '<td class="px-4 py-3 text-right">' + boton('borrarBinding', {
            origen: b.source, destino: b.destination, tipo: b.destinationType, clave: b.routingKey || '',
          }, 'Eliminar', 'border border-white/[0.08] text-zinc-400 hover:text-white') + '</td>' +
          '</tr>';
      }).join('') || vacioFila(5, 'No hay bindings.');
    }).catch(function (e) { toast(e.message, true); });
  }

  function etiquetaDefinicion(k) {
    var etiquetas = {
      'max-length': 'Máximo de mensajes',
      'message-ttl': 'Vencimiento (TTL)',
      overflow: 'Al llenarse',
      'dead-letter-exchange': 'Exchange DLX',
      'dead-letter-routing-key': 'Routing key DLX',
    };
    return etiquetas[k] || k;
  }

  function valorDefinicion(k, v) {
    if (typeof v === 'number') return k === 'message-ttl' ? num(v) + ' ms' : num(v);
    return String(v);
  }

  function cargarMqPoliticas() {
    return RX.get('/api/admin/mq/policies').then(function (lista) {
      estado.mq.politicas = lista || [];
      $('#mq-politicas').innerHTML = estado.mq.politicas.map(function (p) {
        var def = p.definition || {};
        var filas = Object.keys(def).map(function (k) {
          return '<div class="flex justify-between gap-3 border-b border-white/[0.04] py-1 text-xs">' +
            '<span class="text-zinc-400">' + esc(etiquetaDefinicion(k)) + '</span>' +
            '<span class="font-mono text-white text-right">' + esc(valorDefinicion(k, def[k])) + '</span></div>';
        }).join('');
        return '<div class="bg-[#1A1A1A] border border-white/[0.06] rounded-sm p-4 space-y-3">' +
          '<div class="flex items-center justify-between"><span class="text-sm font-bold font-mono text-white">' + esc(p.name) + '</span>' +
          insignia('Prioridad ' + p.priority, 'border-white/[0.1] bg-white/[0.05] text-zinc-300') + '</div>' +
          '<div class="text-xs text-zinc-400"><span class="text-zinc-500">Patrón:</span> <span class="font-mono break-all">' + esc(p.pattern) + '</span></div>' +
          '<div class="text-xs text-zinc-400"><span class="text-zinc-500">Aplica a:</span> ' + esc(p.applyTo) + '</div>' +
          '<div>' + filas + '</div></div>';
      }).join('') || '<p class="text-xs text-zinc-500">No hay políticas.</p>';
      $('#mq-politicas-json').textContent = JSON.stringify(estado.mq.politicas, null, 2);
    }).catch(function (e) { toast(e.message, true); });
  }

  function cargarMqPruebas() {
    return RX.get('/api/admin/mq/exchanges').then(function (lista) {
      estado.mq.exchanges = lista || [];
      rellenarExchanges();
    }).catch(function (e) { toast(e.message, true); });
  }

  function aplicarPlantilla() {
    var p = PLANTILLAS[$('#p-plantilla').value];
    if (!p) return;
    $('#p-exchange').value = p.exchange;
    $('#p-clave').value = p.clave;
    $('#p-cuerpo').value = typeof p.cuerpo === 'string' ? p.cuerpo : JSON.stringify(p.cuerpo, null, 2);
  }

  /* Los errores de borrado se resuelven igual: si el broker protege el recurso (409), se pide confirmación para forzar. */
  function borrarRecurso(ruta, nombre, tipo) {
    confirmar('¿Eliminar ' + tipo + ' «' + nombre + '»?', function () {
      return RX.del(ruta).then(function () {
        toast(tipo.charAt(0).toUpperCase() + tipo.slice(1) + ' eliminada');
        return refrescarMqTrasCambio();
      }, function (e) {
        if (e.status !== 409) return toast(e.message, true);
        confirmar(e.message + ' ¿Forzar la eliminación? Hazlo solo si sabes lo que haces.', function () {
          return RX.del(ruta, { force: true }).then(function () {
            toast('Eliminada (forzado)');
            return refrescarMqTrasCambio();
          }, function (e2) { toast(e2.message, true); });
        }, 'Forzar');
      });
    }, 'Sí, eliminar');
  }

  function borrarBinding(d) {
    var query = { source: d.origen, destination: d.destino, destinationType: d.tipo, routingKey: d.clave };
    confirmar('¿Eliminar el binding ' + d.origen + ' → ' + d.destino + (d.clave ? ' (' + d.clave + ')' : '') + '?', function () {
      return RX.del('/api/admin/mq/bindings', query).then(function () {
        toast('Binding eliminado');
        return refrescarMqTrasCambio();
      }, function (e) {
        if (e.status !== 409) return toast(e.message, true);
        confirmar(e.message + ' ¿Forzar la eliminación?', function () {
          return RX.del('/api/admin/mq/bindings', Object.assign({}, query, { force: true })).then(function () {
            toast('Binding eliminado (forzado)');
            return refrescarMqTrasCambio();
          }, function (e2) { toast(e2.message, true); });
        }, 'Forzar');
      });
    }, 'Sí, eliminar');
  }

  function crearCola(ev) {
    var f = ev.target;
    var cuerpo = { name: f.nombre.value.trim(), durable: f.durable.checked };
    if (f.ttl.value) cuerpo.ttlMs = Number(f.ttl.value);
    if (f.maxLength.value) cuerpo.maxLength = Number(f.maxLength.value);
    if (f.overflow.value) cuerpo.overflow = f.overflow.value;
    if (f.dlx.value) cuerpo.deadLetterExchange = f.dlx.value;
    if (f.dlrk.value.trim()) cuerpo.deadLetterRoutingKey = f.dlrk.value.trim();
    return ejecutar(ev.submitter, function () { return RX.post('/api/admin/mq/queues', cuerpo); }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      f.reset();
      f.classList.add('hidden');
      toast('Cola creada');
      return refrescarMqTrasCambio();
    });
  }

  function crearExchange(ev) {
    var f = ev.target;
    var cuerpo = { name: f.nombre.value.trim(), type: f.tipo.value, durable: f.durable.checked };
    return ejecutar(ev.submitter, function () { return RX.post('/api/admin/mq/exchanges', cuerpo); }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      f.reset();
      f.classList.add('hidden');
      toast('Exchange creado');
      return refrescarMqTrasCambio();
    });
  }

  function crearBinding(ev) {
    var f = ev.target;
    var cuerpo = {
      source: f.origen.value,
      destinationType: f.tipoDestino.value,
      destination: f.destino.value,
      routingKey: f.clave.value.trim(),
    };
    return ejecutar(ev.submitter, function () { return RX.post('/api/admin/mq/bindings', cuerpo); }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      f.reset();
      f.classList.add('hidden');
      toast('Binding creado');
      return refrescarMqTrasCambio();
    });
  }

  function guardarRetencion(ev) {
    var f = ev.target;
    var cuerpo = {
      commandTtlMs: Number(f.commandTtlMs.value),
      dlqTtlMs: Number(f.dlqTtlMs.value),
      maxLength: Number(f.maxLength.value),
    };
    return ejecutar(ev.submitter, function () { return RX.put('/api/admin/mq/retention', cuerpo); }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      toast('Retención guardada');
      return cargarMqPoliticas();
    });
  }

  function aplicarTopologia(boton) {
    return ejecutar(boton, function () { return RX.post('/api/admin/mq/topology', {}); }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      toast('Topología y retención aplicadas');
      return refrescarMqTrasCambio();
    });
  }

  function publicarPrueba(ev) {
    var exchange = $('#p-exchange').value;
    var clave = $('#p-clave').value.trim();
    var veces = Math.min(50, Math.max(1, parseInt($('#p-veces').value, 10) || 1));
    var plantilla = $('#p-cuerpo').value;
    if (!exchange || !clave) return toast('Elige un exchange y escribe la routing key.', true);
    /* La plantilla «Mensaje inválido» no es JSON a propósito: sirve para comprobar la DLQ. */
    var esInvalidoIntencional = plantilla.indexOf('{no-es-json') !== -1;
    if (!esInvalidoIntencional) {
      try {
        JSON.parse(plantilla.replace(/REEMPLAZAR-UUID/g, '00000000-0000-0000-0000-000000000000'));
      } catch (_) {
        return toast('El cuerpo debe ser JSON válido.', true);
      }
    }
    var resumen = { enviados: 0, confirmados: 0, ruteados: 0 };
    var secuencia = Promise.resolve();
    for (var i = 0; i < veces; i++) {
      secuencia = secuencia.then(function () {
        var cuerpo = plantilla.replace(/REEMPLAZAR-UUID/g, uuid());
        return RX.post('/api/admin/mq/exchanges/' + encodeURIComponent(exchange) + '/messages', { routingKey: clave, body: cuerpo })
          .then(function (res) {
            resumen.enviados++;
            if (res.confirmed) resumen.confirmados++;
            if (res.routed) resumen.ruteados++;
          });
      });
    }
    return ejecutar(ev.submitter, function () { return secuencia; }).then(function (r) {
      if (!r.ok) return toast(r.error.message, true);
      $('#p-resultado').textContent = 'Enviados ' + resumen.enviados + ' · confirmados ' + resumen.confirmados + ' · ruteados ' + resumen.ruteados;
    });
  }

  /* ------------------------------------------------------------------ Kafka */

  function cargarKafka() {
    kafkaTopicos();
    kafkaCluster();
    return Promise.resolve();
  }

  function kafkaTopicos() {
    return RX.get('/api/admin/kafka/topics').then(function (lista) {
      $('#kafka-topicos').innerHTML = (lista || []).map(function (t) {
        return '<li class="px-2 py-1 bg-[#151515] border border-white/[0.04] rounded-xs">' + esc(t) + '</li>';
      }).join('') || '<li class="text-zinc-500">Sin tópicos.</li>';
    }, function (e) {
      $('#kafka-topicos').innerHTML = '<li class="text-rose-300">' + esc(e.message) + '</li>';
    });
  }

  function kafkaCluster() {
    return RX.get('/api/admin/kafka/cluster').then(function (c) {
      $('#kafka-cluster').innerHTML = '<div><span class="text-zinc-500">ID del clúster:</span> ' + esc(c.clusterId) + '</div>' +
        (c.nodes || []).map(function (n) {
          return '<div>Broker ' + esc(n.id) + ' · ' + esc(n.host) + ':' + esc(n.port) + '</div>';
        }).join('');
    }, function (e) {
      $('#kafka-cluster').innerHTML = '<span class="text-rose-300">' + esc(e.message) + '</span>';
    });
  }

  function consultarLag() {
    var grupo = $('#kafka-grupo').value.trim();
    if (!grupo) return toast('Escribe el grupo de consumo.', true);
    ocultarAviso('#kafka-lag-aviso');
    return RX.get('/api/admin/kafka/groups/' + encodeURIComponent(grupo) + '/lag').then(function (filas) {
      $('#kafka-lag-tbody').innerHTML = (filas || []).map(function (p) {
        return '<tr>' +
          '<td class="px-3 py-2 font-mono">' + esc(p.topic) + '</td>' +
          '<td class="px-3 py-2 font-mono">' + num(p.partition) + '</td>' +
          '<td class="px-3 py-2 font-mono">' + num(p.committedOffset) + '</td>' +
          '<td class="px-3 py-2 font-mono">' + num(p.endOffset) + '</td>' +
          '<td class="px-3 py-2 font-mono font-semibold ' + (p.lag > 0 ? 'text-rose-300' : 'text-white') + '">' + num(p.lag) + '</td>' +
          '</tr>';
      }).join('') || vacioFila(5, 'El grupo no tiene particiones asignadas.');
    }, function (e) {
      $('#kafka-lag-tbody').innerHTML = '';
      mostrarAviso('#kafka-lag-aviso', e.message);
    });
  }

  /* ------------------------------------------------------------------ Sesión */

  function abrirSesion() {
    var p = estado.perfil || {};
    $('#sesion-nombre').textContent = p.name || p.username || 'Sin nombre';
    $('#sesion-id').textContent = p.userId || '—';
    $('#sesion-proveedor').textContent = cfg.authMode === 'msal' ? 'Microsoft (Entra ID)' : 'Desarrollo (sin seguridad)';
    $('#sesion-api').textContent = cfg.apiBaseUrl || window.location.origin;
    $('#sesion-roles').innerHTML = estado.roles.map(function (r) {
      return insignia(r, 'border-white/[0.1] bg-white/[0.05] text-zinc-200');
    }).join('') || '<span class="text-zinc-500">Sin roles asignados</span>';
    $('#sesion-vence').textContent = '—';
    $('#bloque-rol-dev').classList.toggle('hidden', cfg.authMode !== 'none');
    $('#dev-rol').value = estado.roles[0] || 'ADMIN';
    if (cfg.authMode === 'msal') {
      RXAuth.vencimientoToken().then(function (f) {
        $('#sesion-vence').textContent = f ? f.toLocaleString('es-CL') : '—';
      });
    }
    abrirModal('modal-sesion');
  }

  function cambiarRolDev(rol) {
    try { sessionStorage.setItem('rx-dev-rol', rol); } catch (_) { /* sin almacenamiento: solo dura esta carga */ }
    estado.roles = [rol];
    aplicarPermisos();
    mostrarVista(estado.vista);
    toast('Rol de prueba: ' + rol);
  }

  function salir() {
    if (cfg.authMode === 'msal') return RXAuth.cerrarSesion();
    toast('Modo desarrollo: no hay sesión que cerrar.');
  }

  /* ------------------------------------------------------------------ Eventos */

  function manejarClic(ev) {
    var t = ev.target;
    if (!t || !t.closest) return;

    var pestana = t.closest('[data-rtab]');
    if (pestana) return cargarMq(pestana.getAttribute('data-rtab'));

    var alternar = t.closest('[data-toggle]');
    if (alternar) {
      var formulario = document.getElementById(alternar.getAttribute('data-toggle'));
      if (formulario) formulario.classList.toggle('hidden');
      return;
    }

    var cerrar = t.closest('[data-cerrar]');
    if (cerrar) return cerrarModal(cerrar.getAttribute('data-cerrar'));

    var el = t.closest('[data-accion]');
    if (!el || el.tagName === 'INPUT') return;
    var d = el.dataset;
    var acciones = {
      nuevoEnvio: function () { return abrirNuevoEnvio(); },
      estado: function () { return cambiarEstado(d.id, d.estado, el, d.codigo); },
      cancelar: function () { return pedirCancelar(d.id, d.codigo); },
      reintentarCupo: function () { return reintentarCupo(d.id, el); },
      timeline: function () { return abrirTimeline(d.id, d.codigo); },
      detalle: function () { return abrirDetalle(d.id); },
      volverCatalogo: function () { return mostrarCatalogoLista(); },
      editarCapacidad: function () { return abrirEditarCapacidad(d.cap); },
      json: function () { var pre = document.getElementById(d.target); if (pre) pre.classList.toggle('hidden'); },
      mqJson: function () { $('#mq-politicas-json').classList.toggle('hidden'); },
      kafkaTopicos: function () { return kafkaTopicos(); },
      topologia: function () { return aplicarTopologia(el); },
      borrarCola: function () { return borrarRecurso('/api/admin/mq/queues/' + encodeURIComponent(d.nombre), d.nombre, 'cola'); },
      borrarExchange: function () { return borrarRecurso('/api/admin/mq/exchanges/' + encodeURIComponent(d.nombre), d.nombre, 'exchange'); },
      borrarBinding: function () { return borrarBinding(d); },
    };
    if (acciones[d.accion]) {
      ev.preventDefault();
      acciones[d.accion]();
    }
  }

  function manejarCambio(ev) {
    var t = ev.target;
    if (t && t.matches && t.matches('input[data-accion="activar"]')) {
      cambiarActivaCapacidad(t.getAttribute('data-cap'), t.checked);
    }
  }

  function manejarEnvio(ev) {
    var manejadores = {
      'form-envio': enviarNuevoEnvio,
      'form-cola': crearCola,
      'form-exchange': crearExchange,
      'form-binding': crearBinding,
      'form-retencion': guardarRetencion,
      'form-prueba': publicarPrueba,
      'form-auditoria': buscarAuditoria,
      'form-lag': consultarLag,
    };
    var fn = manejadores[ev.target.id];
    if (fn) {
      ev.preventDefault();
      fn(ev);
    }
  }

  function registrarEventos() {
    window.addEventListener('hashchange', function () { mostrarVista(vistaDesdeHash()); });
    document.addEventListener('click', manejarClic);
    document.addEventListener('change', manejarCambio);
    document.addEventListener('submit', manejarEnvio);

    $('#btn-sesion').addEventListener('click', abrirSesion);
    $('#btn-salir').addEventListener('click', salir);
    $('#dev-rol').addEventListener('change', function (e) { cambiarRolDev(e.target.value); });
    $('#confirmar-si').addEventListener('click', function () {
      var fn = accionConfirmada;
      accionConfirmada = null;
      cerrarModal('modal-confirmar');
      if (fn) Promise.resolve().then(fn).catch(function (e) { toast(e.message || 'Error inesperado.', true); });
    });

    $('#envios-q').addEventListener('input', debounce(function () {
      estado.envios.q = $('#envios-q').value.trim();
      estado.envios.page = 0;
      cargarEnvios();
    }, 300));
    $('#envios-estado').addEventListener('change', function () {
      estado.envios.estado = $('#envios-estado').value;
      estado.envios.page = 0;
      cargarEnvios();
    });
    $('#envios-capacidad').addEventListener('change', function () {
      estado.envios.capacidad = $('#envios-capacidad').value;
      estado.envios.page = 0;
      cargarEnvios();
    });
    $('#btn-envios-limpiar').addEventListener('click', function () {
      $('#envios-q').value = '';
      $('#envios-estado').value = '';
      $('#envios-capacidad').value = '';
      estado.envios.q = '';
      estado.envios.estado = '';
      estado.envios.capacidad = '';
      estado.envios.page = 0;
      cargarEnvios();
    });
    $('#btn-envios-ant').addEventListener('click', function () {
      if (estado.envios.page > 0) { estado.envios.page--; cargarEnvios(); }
    });
    $('#btn-envios-sig').addEventListener('click', function () {
      if (estado.envios.page < estado.envios.totalPages - 1) { estado.envios.page++; cargarEnvios(); }
    });

    $('#btn-detalle-refrescar').addEventListener('click', recargarDetalle);
    $('#btn-cap-guardar').addEventListener('click', function () { guardarCapacidad(this); });

    $('#reportes-rango').addEventListener('change', cargarReportes);
    $('#auditoria-fuente').addEventListener('change', pintarAuditoria);
    $('#p-plantilla').addEventListener('change', aplicarPlantilla);
    $('#form-binding select[name="tipoDestino"]').addEventListener('change', rellenarDestinos);
  }

  /* ------------------------------------------------------------------ Arranque */

  function avisoGlobal(texto) {
    var el = $('#aviso-global');
    el.textContent = texto;
    el.classList.remove('hidden');
  }

  function arrancar() {
    registrarEventos();
    RXAuth.iniciarAutenticacion().then(function (auth) {
      if (auth.redirigiendo) return false;
      return cargarPerfil().then(function () { return true; });
    }).then(function (listo) {
      if (!listo) return;
      aplicarPermisos();
      if (!estado.roles.length) {
        avisoGlobal('Tu sesión no tiene un rol válido de RutaExpress. Solicita la asignación a un administrador.');
        return;
      }
      saludo();
      mostrarVista(vistaDesdeHash());
    }).catch(function (e) {
      avisoGlobal('No se pudo iniciar la sesión: ' + (e && e.message ? e.message : e));
    });
  }

  arrancar();
})();
