# RutaExpress en local

Desde la raíz, con Docker Desktop en modo contenedores Linux:

```sh
docker compose up --build
```

No requiere instalar Java, Gradle, Node, Oracle o Bash en el equipo. El Compose de la raíz construye **frontend-v2** y los ocho backends. Incluye Oracle Free, Redis, Mailpit, dos nodos RabbitMQ, tres brokers Kafka, tres ZooKeeper y Kafka UI. Kafka, auditoría y reportería están activos por defecto.

La primera ejecución descarga imágenes y dependencias, ejecuta las pruebas de los Dockerfiles, inicializa Oracle y crea la cookie persistente del clúster RabbitMQ. Puede tardar varios minutos. Asignar inicialmente **16 GiB de RAM a Docker** y espacio suficiente para imágenes, cachés y datos. Los Dockerfiles comparten una caché Gradle con bloqueo y limitan sus workers: solo una fase Gradle backend usa la caché a la vez, incluso al construir todos los servicios con un único comando.

## Direcciones

| Servicio | Dirección |
|---|---|
| frontend-v2 | http://localhost:4200 |
| API a través del frontend | http://localhost:4200/api/bff/auth/me |
| RabbitMQ Management | http://localhost:15672 y http://localhost:15673 |
| Mailpit | http://localhost:8025 |
| Kafka UI | http://localhost:8090 |

Los backends y las bases de datos solo se exponen en la red interna de Compose. nginx reenvía `/api` al BFF, por lo que el navegador usa el mismo origen y no necesita CORS local. Los puertos publicados están ligados a `127.0.0.1`.

El usuario RabbitMQ predeterminado es `ruta_local` y la contraseña `RutaLocalRabbit2026`. Son valores de desarrollo exclusivos de este Compose. No usar este archivo para despliegue público. Las configuraciones de nube siguen en `infra/apps`, `infra/mq`, `infra/kafka`, `infra/edge` e `infra/vps`.

## Microsoft Entra ID

La aplicación y sus datos corren localmente; **el login sigue usando Microsoft Entra ID y requiere internet**. frontend-v2 también descarga MSAL, estilos, iconos y fuentes desde CDN. No es una modalidad sin conexión.

En el registro de aplicación utilizado, registrar `http://localhost:4200` como redirect URI de tipo **Single-page application (SPA)**. Usar `localhost` al abrir la aplicación para coincidir con esa URI. Asignar los roles a los usuarios en la aplicación empresarial y verificar sus valores en el access token de la API. Un usuario sin roles reconocidos no obtiene permisos de Cliente automáticamente.

El Compose usa los identificadores públicos ya existentes en el proyecto. Para otro tenant, copiar `.env.example` a `.env` en la raíz y configurar emisor, audiencia, client ID, authority y scope de forma coherente. No se necesitan secretos de cliente para la SPA. El `.env` de la raíz pertenece a este Compose; `infra/.env` corresponde a los scripts de despliegue anteriores.

## Matriz de autorización

| Operación | Admin | Despachador | Cliente | Auditor |
|---|---|---|---|---|
| Consultar envíos y resumen | Todos | Todos | Solo propios por `oid` | No |
| Crear envíos | Sí | Sí | Sí | No |
| Cambiar estados y reintentar capacidad | Sí | Sí | No | No |
| Pantalla de catálogo y capacidades | Sí | Lectura | No | No |
| Administrar catálogo | Sí | No | No | No |
| Resolver referencia y consultar timeline | Sí | No | No | Lectura |
| Reportes y administración de brokers | Sí | No | No | No |

Cliente mantiene la consulta de servicios y tarifas para crear envíos y la comprobación de disponibilidad. Las respuestas de selección no incluyen las capacidades ni sus totales. El control se aplica también directamente en Catalog.

Se aceptan los cuatro roles del caso y los alias `Operador`/`Operador del dominio` → `DESPACHADOR`, `Cliente del dominio` → `CLIENTE`. Se normalizan espacios exteriores, mayúsculas y el prefijo `ROLE_`. Otros valores se ignoran y los scopes no se convierten en roles. Con múltiples roles se suman los permisos; `CLIENTE` + `AUDITOR` sigue viendo solo sus envíos en las operaciones de envíos, con acceso independiente al timeline.

La consulta puntual `GET /api/bff/audit/resolve/{reference}` permite resolver un ID o código de seguimiento para auditoría. Su equivalente interno es `GET /api/shipments/audit/resolve/{reference}`. Ambos requieren Admin o Auditor y solo devuelven `id` y `trackingCode`.

## Operación y datos

```sh
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
docker compose logs -f
docker compose exec rabbit1 rabbitmqctl cluster_status
docker compose down
```

`down` conserva los datos y la cookie. Volver a ejecutar `up` reutiliza esos volúmenes. No cambiar las contraseñas de Oracle o RabbitMQ esperando que se actualicen usuarios ya creados: los valores iniciales solo se aplican al crear datos nuevos. No borrar la cookie de un clúster existente.

El proyecto Compose se llama `rutaexpress-local`; usa red y volúmenes propios y no reutiliza la red externa `rutaexpress` de los despliegues anteriores. Detener esos despliegues si ocupan los mismos puertos locales. `FRONTEND_PORT` permite cambiar el puerto del frontend; en ese caso ajustar también `MSAL_REDIRECT_URI` y registrar la URI en Entra.

## Verificación

```sh
docker compose config -q
node --test frontend-v2/tests/*.test.cjs
docker compose build
```

Los builds backend ejecutan sus pruebas unitarias y de integración ligera. Las pruebas Testcontainers necesitan acceso al motor Docker y mantienen su ejecución separada en CI. Un arranque saludable verifica infraestructura y procesos, pero la comprobación final del login requiere los usuarios reales del tenant.

Para probar el flujo HTTP con tokens RSA locales válidos, roles, propiedad de envíos, reserva RabbitMQ, correo y auditoría Kafka, existe un override exclusivo de pruebas. Cambia temporalmente el emisor confiable de los backends; no desactiva seguridad. Su endpoint de emisión de tokens está limitado a loopback y no debe exponerse ni usarse en producción:

```sh
docker compose -f compose.yaml -f tests/local/compose.jwt.yml up -d --build --wait --wait-timeout 600
python tests/local/smoke.py
```

La prueba crea un servicio y un envío con datos de prueba en los volúmenes locales. Para restaurar Entra ID y retirar el emisor de pruebas:

```sh
docker compose up -d --wait --wait-timeout 600 --remove-orphans
```
