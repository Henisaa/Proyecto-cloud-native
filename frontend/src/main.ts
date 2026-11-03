import { bootstrapApplication } from '@angular/platform-browser';
import { loadRuntimeConfig } from './app/core/app-config';

// La configuración del entorno (config.json) se lee antes de construir MSAL y el resto de los proveedores.
loadRuntimeConfig()
  .then(async () => {
    const [{ appConfig }, { AppComponent }] = await Promise.all([
      import('./app/app.config'),
      import('./app/app.component'),
    ]);
    return bootstrapApplication(AppComponent, appConfig);
  })
  .catch((err) => console.error(err));
