import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { BffService } from '../../core/bff.service';
import { DashboardPage } from './dashboard.page';

describe('Monitoreo de mensajería', () => {
  it('mantiene las colas visibles cuando Kafka falla', () => {
    const queues = [{ name: 'q.rutaexpress.notification.dlq', messages: 2, consumers: 0, alert: true }];
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { hasRole: () => true } },
      { provide: BffService, useValue: { mqQueues: () => of(queues), kafkaTopics: () => throwError(() => new Error('offline')) } },
    ] });
    const page = TestBed.runInInjectionContext(() => new DashboardPage());
    page.loadBrokers();
    expect(page.queues).toEqual(queues);
    expect(page.mqError).toBe('');
    expect(page.kafkaError).toContain('No se pudo consultar Kafka');
    expect(page.brokerLoading).toBeFalse();
  });
  it('no consulta administradores para otros roles', () => {
    const mq = jasmine.createSpy('mqQueues');
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { hasRole: () => false } },
      { provide: BffService, useValue: { mqQueues: mq } },
    ] });
    TestBed.runInInjectionContext(() => new DashboardPage()).loadBrokers();
    expect(mq).not.toHaveBeenCalled();
  });
});
