# Verificación obligatoria por push

El workflow CI ejecuta todos los módulos en cada push, PR y ejecución manual. No filtra archivos ni cancela la verificación de un commit anterior. Una imagen se publica únicamente después de que el job `quality-gate` apruebe todas las etapas; se publica exactamente la imagen candidata que se probó.

## Capas

- Compilación y pruebas dentro de Docker de ocho servicios Kotlin y del frontend Angular de producción.
- Suite canónica de los ocho servicios con Testcontainers habilitado, reportes JUnit y cobertura JaCoCo de todas las clases. Las pruebas de integración usan infraestructura real en contenedores.
- Karma/Chromium con cobertura y comprobación de todas las rutas lazy del frontend. Smoke de la imagen nginx: salud, configuración, fallback SPA, CSP y usuario sin privilegios.
- Validación de Compose, sintaxis de scripts, actionlint y pruebas del propio verificador.
- Stack integrado: Oracle y migraciones, Redis, dos nodos RabbitMQ, tres brokers Kafka con tres ZooKeeper, Mailpit y las ocho aplicaciones con seguridad habilitada.
- Playwright: contratos HTTP, JWT inválidos, cinco actores, aislamiento por propietario, reservas y liberaciones concurrentes, rechazo y reintento, correo y auditoría, TTL/overflow, mensajes venenosos y SMTP detenido. El recorrido de pantalla corre en Chromium, Firefox, WebKit y emulación móvil.

## Condiciones de rechazo

No se aceptan suites ausentes, tests omitidos o enfocados, errores, fallos, casos duplicados, conteos inferiores al mínimo ni regresiones de cobertura. Playwright tiene cero reintentos y rechaza pruebas inestables. Los pisos globales y por clase crítica están versionados en los archivos JSON de esta carpeta; la política impide reducirlos respecto a la base del cambio. No se excluyen clases de producción del reporte para aumentar el porcentaje.

Los artefactos conservan JUnit, JaCoCo, cobertura frontend, HTML de Playwright, capturas/trazas de fallos y logs del stack. Revisar el resultado del commit exacto: que exista un push no implica que haya terminado correctamente.

## Límites de la evidencia

El emisor OIDC de CI firma JWT con una clave efímera. El gateway local valida esos tokens y las aplicaciones vuelven a validarlos. El frontend E2E tiene un punto de entrada separado para seleccionar actores; la imagen de producción conserva MSAL y no contiene ese punto de entrada.

Esto comprueba la autorización y los flujos del sistema en CI. La autenticación interactiva real en Microsoft Entra ID, AWS API Gateway, EC2, DNS y TLS se verifica adicionalmente contra el entorno de evaluación con `infra/acceptance`. No existe una garantía de ausencia de todos los defectos posibles ni se declara cobertura del 100 %.
