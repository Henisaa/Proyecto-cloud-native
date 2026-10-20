import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { BffService } from '../core/bff.service';
import { Capacidad, CrearEnvio, Envio, ServicioDetalle, ServicioResumen } from '../core/models';
import { ShipmentNewPage, textoONulo } from './shipments/shipment-new.page';
import { CatalogPage } from './catalog/catalog.page';
import { AuditPage } from './audit/audit.page';
import { ReportsPage } from './reports/reports.page';
import { MqColasComponent } from './admin-mq/mq-colas.component';
import { MqExchangesComponent } from './admin-mq/mq-exchanges.component';
import { MqBindingsComponent } from './admin-mq/mq-bindings.component';
import { MqRetencionComponent } from './admin-mq/mq-retencion.component';
import { AdminKafkaPage } from './admin-kafka/admin-kafka.page';

describe('Flujos de páginas y formularios EP2', () => {
  let api: jasmine.SpyObj<BffService>;
  const failure = () => throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Conflicto de prueba' } }));
  beforeEach(() => {
    api = jasmine.createSpyObj<BffService>('BffService', ['servicios', 'crearEnvio', 'servicio', 'editarCapacidad', 'listarEnvios', 'lineaDeTiempo', 'kpis', 'topServicios', 'mqColas', 'mqMetricas', 'mqExchanges', 'mqBindings', 'mqPoliticas', 'crearCola', 'eliminarCola', 'crearExchange', 'eliminarExchange', 'crearBinding', 'eliminarBinding', 'aplicarRetencion', 'aplicarTopologia', 'kafkaTopics', 'kafkaLag']);
    api.servicios.and.returnValue(of([])); api.mqExchanges.and.returnValue(of([])); api.mqColas.and.returnValue(of([]));
    api.mqMetricas.and.returnValue(of({ queues: [] } as any)); api.mqBindings.and.returnValue(of([])); api.mqPoliticas.and.returnValue(of([]));
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: AuthService, useValue: { roles: ['ADMIN'], hasRole: () => true } },
      { provide: BffService, useValue: api },
    ] });
  });

  it('no envía formulario vacío y marca obligatorios', () => {
    const page = TestBed.runInInjectionContext(() => new ShipmentNewPage());
    page.enviar();
    expect(api.crearEnvio).not.toHaveBeenCalled();
    expect(page.error('recipientName')).toBe('Campo obligatorio');
    expect(textoONulo('   ')).toBeNull();
  });
  it('solo ofrece servicios activos y disponibles', () => {
    api.servicios.and.returnValue(of([{ id: 1, active: true, status: 'ACTIVE' }, { id: 2, active: false, status: 'ACTIVE' }, { id: 3, active: true, status: 'SUSPENDED' }] as ServicioResumen[]));
    const page = TestBed.runInInjectionContext(() => new ShipmentNewPage());
    page.ngOnInit();
    expect(page.servicios().map(x => x.id)).toEqual([1]);
  });
  for (const [field, value] of [['packagesCount', 1.5], ['weightKg', 1.123], ['volumeM3', 1.1234], ['recipientEmail', 'invalid'], ['recipientPhone', 'x'.repeat(31)]] as const) {
    it(`formulario rechaza ${field} fuera de contrato`, () => {
      const page = TestBed.runInInjectionContext(() => new ShipmentNewPage());
      page.form.controls[field].setValue(value as never);
      expect(page.form.controls[field].invalid).toBeTrue();
    });
  }
  it('normaliza campos opcionales y muestra validaciones de servidor', () => {
    api.crearEnvio.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { validationErrors: { recipientName: 'Nombre rechazado' } } })));
    const page = TestBed.runInInjectionContext(() => new ShipmentNewPage());
    page.form.setValue({ serviceId: 1, zone: 'URBAN_CENTER', packagesCount: 1, weightKg: 1, volumeM3: 0.1, recipientName: ' Ana ', recipientEmail: '', recipientPhone: ' ', originAddress: ' Origen ', destinationAddress: ' Destino ' });
    page.enviar();
    expect(api.crearEnvio.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ recipientEmail: null, recipientPhone: null, recipientName: 'Ana' }));
    expect(page.error('recipientName')).toBe('Nombre rechazado');
    expect(page.enviando()).toBeFalse();
  });

  it('catálogo rechaza máximo menor a reservados antes de enviar', () => {
    const page = TestBed.runInInjectionContext(() => new CatalogPage());
    page.maximoEditado.set(2);
    page.guardarMaximo({ id: 1 } as ServicioDetalle, { id: 1, currentBookedPackages: 3 } as Capacidad);
    expect(api.editarCapacidad).not.toHaveBeenCalled();
    expect(page.errorEdicion()).not.toBeNull();
  });
  it('catálogo recupera estado vigente después de conflicto de edición', () => {
    api.editarCapacidad.and.returnValue(failure()); api.servicio.and.returnValue(of({ id: 1, capacities: [] } as unknown as ServicioDetalle));
    const page = TestBed.runInInjectionContext(() => new CatalogPage());
    page.cambiarActivo({ id: 1 } as ServicioDetalle, { id: 2 } as Capacidad, false);
    expect(page.guardando()).toBeFalse();
    expect(page.errorEdicion()).toBe('Conflicto de prueba');
    expect(api.servicio).toHaveBeenCalledWith(1);
  });
  it('catálogo muestra error de consulta sin seguir cargando', () => {
    api.servicios.and.returnValue(failure());
    const page = TestBed.runInInjectionContext(() => new CatalogPage()); page.recargar();
    expect(page.cargando()).toBeFalse(); expect(page.error()).toBe('Conflicto de prueba');
  });

  it('auditoría exige término y resuelve seguimiento al id interno', () => {
    const page = TestBed.runInInjectionContext(() => new AuditPage()); page.buscar();
    expect(page.errorCampo()).not.toBeNull(); expect(api.lineaDeTiempo).not.toHaveBeenCalled();
    api.listarEnvios.and.returnValue(of({ content: [{ id: 's1', trackingCode: 'RX-TEST' }] } as any));
    api.lineaDeTiempo.and.returnValue(of({ events: [], kafkaAvailable: false } as any));
    page.termino.set('rx-test'); page.buscar();
    expect(api.lineaDeTiempo).toHaveBeenCalledWith('s1'); expect(page.kafkaDisponible()).toBeFalse();
  });
  it('auditoría no consulta timeline para un código inexistente', () => {
    api.listarEnvios.and.returnValue(of({ content: [] } as any));
    const page = TestBed.runInInjectionContext(() => new AuditPage()); page.termino.set('RX-NOEXISTE'); page.buscar();
    expect(api.lineaDeTiempo).not.toHaveBeenCalled(); expect(page.cargando()).toBeFalse();
  });
  it('auditoría presenta errores HTTP y permite reintento', () => {
    api.lineaDeTiempo.and.returnValue(failure());
    const page = TestBed.runInInjectionContext(() => new AuditPage()); page.termino.set('s1'); page.buscar();
    expect(page.error()).toBe('Conflicto de prueba'); expect(page.cargando()).toBeFalse();
    api.lineaDeTiempo.and.returnValue(of({ events: [], kafkaAvailable: true } as any)); page.buscar();
    expect(page.error()).toBeNull();
  });
  it('reportes entrega KPIs y ranking del mismo rango', () => {
    api.kpis.and.returnValue(of({ activeShipmentsByStatus: { ENTREGADO: 2 } } as any)); api.topServicios.and.returnValue(of([]));
    const page = TestBed.runInInjectionContext(() => new ReportsPage()); page.rango.set('last7d'); page.cargar();
    expect(api.kpis).toHaveBeenCalledWith('last7d'); expect(api.topServicios).toHaveBeenCalledWith('last7d');
    expect(page.estados()).toEqual([['ENTREGADO', 2]]); expect(page.cargando()).toBeFalse();
  });
  it('reportes falla visiblemente si el ranking no está disponible', () => {
    api.kpis.and.returnValue(of({} as any)); api.topServicios.and.returnValue(failure());
    const page = TestBed.runInInjectionContext(() => new ReportsPage()); page.cargar();
    expect(page.error()).toBe('Conflicto de prueba'); expect(page.cargando()).toBeFalse();
  });

  it('colas no acepta nombres reservados ni confirma creación fallida', () => {
    const page = TestBed.runInInjectionContext(() => new MqColasComponent()); page.nueva.set({ name: 'amq.x', durable: true }); page.crear();
    expect(api.crearCola).not.toHaveBeenCalled();
    api.crearCola.and.returnValue(failure()); page.nueva.set({ name: 'q.demo', durable: true }); page.crear();
    expect(page.errorForm()).toBe('Conflicto de prueba'); expect(page.enCurso()).toBeFalse();
  });
  it('colas refresca y limpia formulario al crear correctamente', () => {
    api.crearCola.and.returnValue(of({}));
    const page = TestBed.runInInjectionContext(() => new MqColasComponent()); page.nueva.set({ name: ' q.demo ', durable: true }); page.crear();
    expect(api.crearCola).toHaveBeenCalledWith({ name: 'q.demo', durable: true });
    expect(page.nueva().name).toBe(''); expect(api.mqMetricas).toHaveBeenCalled();
  });
  it('exchanges exige confirmación y solo fuerza recursos protegidos', () => {
    api.eliminarExchange.and.returnValue(of({}));
    const page = TestBed.runInInjectionContext(() => new MqExchangesComponent()); page.eliminar();
    expect(api.eliminarExchange).not.toHaveBeenCalled();
    page.aEliminar.set({ name: 'ex.demo', type: 'topic', durable: true }); page.eliminar();
    expect(api.eliminarExchange).toHaveBeenCalledWith('ex.demo', false);
    expect(page.esProtegido('ex.rutaexpress.cmd')).toBeTrue();
  });
  it('bindings exige origen y destino y conserva comodines', () => {
    const page = TestBed.runInInjectionContext(() => new MqBindingsComponent()); page.crear();
    expect(api.crearBinding).not.toHaveBeenCalled();
    api.crearBinding.and.returnValue(of({})); page.origen.set('ex.demo'); page.destino.set('q.demo'); page.clave.set('demo.#.*'); page.crear();
    expect(api.crearBinding).toHaveBeenCalledWith(jasmine.objectContaining({ routingKey: 'demo.#.*' }));
    expect(page.errorForm()).toBeNull(); expect(page.enCurso()).toBeFalse();
  });
  it('retención rechaza límites inválidos y muestra errores del servidor', () => {
    const page = TestBed.runInInjectionContext(() => new MqRetencionComponent()); page.maximo.set(0); page.aplicar();
    expect(api.aplicarRetencion).not.toHaveBeenCalled();
    api.aplicarRetencion.and.returnValue(failure()); page.maximo.set(10); page.aplicar();
    expect(page.errorForm()).toBe('Conflicto de prueba'); expect(page.enCurso()).toBeFalse();
  });
  it('topología refleja respuesta y consulta políticas después de aplicar', () => {
    api.aplicarTopologia.and.returnValue(of({ exchanges: ['ex.demo'] }));
    const page = TestBed.runInInjectionContext(() => new MqRetencionComponent()); page.topologia();
    expect(page.resultado()).toContain('ex.demo'); expect(api.mqPoliticas).toHaveBeenCalled();
  });
  it('Kafka opcional no consulta un grupo vacío y presenta el error de conexión', () => {
    const page = TestBed.runInInjectionContext(() => new AdminKafkaPage()); page.consultarLag();
    expect(api.kafkaLag).not.toHaveBeenCalled();
    api.kafkaTopics.and.returnValue(failure()); page.topics(); expect(page.error()).toBe('Conflicto de prueba');
    api.kafkaLag.and.returnValue(of([])); page.grupo = ' ms-rutaexpress-audit-group '; page.consultarLag();
    expect(api.kafkaLag).toHaveBeenCalledWith('ms-rutaexpress-audit-group'); expect(page.error()).toBe('');
  });
});
