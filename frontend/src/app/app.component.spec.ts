import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { MsalBroadcastService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth.service';

describe('AppComponent', () => {
  it('inicializa autenticación sin redirigir a un usuario sin sesión', async () => {
    const auth = { init: jasmine.createSpy('init'), account: signal(null) };
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth },
        { provide: MsalBroadcastService, useValue: { inProgress$: of(InteractionStatus.None) } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(auth.init).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
