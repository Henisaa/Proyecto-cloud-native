Frontend RutaExpress
Angular 19, TypeScript, MSAL Angular/Browser, Tailwind CSS y GSAP.

Función
Login corporativo Azure AD, dashboard, envíos, catálogo, reportería y auditoría. El dashboard agrega monitoreo de colas RabbitMQ y tópicos Kafka para Admin. La vista `/admin/mq` permite consultar métricas y crear o eliminar colas, exchanges y bindings; los errores 400/409 del administrador se muestran en pantalla.
authGuard exige sesión; roleGuard limita módulos. El interceptor solicita access token y adjunta Bearer para /api/bff. El BFF aplica la autorización real; los guards no sustituyen la validación backend. Los alias Operador del dominio y Cliente del dominio se normalizan para navegación.

Requisitos
Node compatible con Angular 19, npm y BFF en puerto 8080. Configuración Azure en src/environments/environment.ts: clientId, authority, scope y redirectUri. Registre el origen local como Single-page application en Azure y asigne los roles de la API al usuario. No agregue secretos de cliente a Angular.

Levantar localmente desde la raíz
cd frontend
npm ci
npm start -- --proxy-config proxy.conf.json
Abra http://localhost:4200. El proxy envía /api al destino definido en proxy.conf.json. El destino local por defecto es http://localhost:8080; cambie proxy.conf.json para trabajar con otro BFF. El archivo environment usa apiBaseUrl vacío para mismo origen. Para monitoreo arranque mq-admin y kafka-admin y cree las topologías según sus README.

Validar
npm run build
npm test -- --watch=false --browsers=ChromeHeadless
Las pruebas de navegador requieren Chrome o Chromium instalado. Puede usar Edge configurando CHROME_BIN con la ruta real de msedge.exe antes de ejecutar npm test. El build comprueba TypeScript y plantillas Angular.

Producción
npm run build
El despliegue estático usa `vercel.json`, que reescribe `/api/*` hacia el dominio del API Gateway. `nginx.conf` conserva un proxy `/api` a `bff:8080` únicamente para desarrollo local cuando ambos contenedores comparten red; no debe usarse como entrada pública de producción. Configure MSAL redirectUri y los orígenes registrados para el dominio real.

Limpiar después de validación
Remove-Item -LiteralPath node_modules,dist,.angular -Recurse -Force -ErrorAction SilentlyContinue
package-lock.json es versionado para que npm ci reproduzca las dependencias. No lo borre al limpiar.

Dependencias heredadas
La validación npm audit de este cambio detectó vulnerabilidades en la línea Angular 19 y dependencias de tooling. Su corrección completa requiere una migración mayor de Angular y revisar MSAL y los builders. Este cambio conserva la versión de frontend existente; build y pruebas exitosas no certifican que esas dependencias estén libres de vulnerabilidades.
