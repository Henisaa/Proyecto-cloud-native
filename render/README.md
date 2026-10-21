# Imagen todo en uno (Render u otro servicio de un solo contenedor)

El `Dockerfile` de la raíz construye **una sola imagen** con todo RutaExpress EP2 dentro:

| Proceso | Puerto interno | Detalle |
|---|---|---|
| nginx | `$PORT` (10000 en Render) | Única entrada pública: frontend, `/api/` → BFF, `/rabbitmq/`, `/mailpit/`, `/health` |
| Frontend Angular | (archivos estáticos de nginx) | Mismo origen que el API: no necesita CORS |
| BFF | 8080 | Valida el JWT y aplica roles |
| shipments | 8085 | Oracle externo (`DB_URL`) |
| catalog | 8081 | Oracle externo (`DB_URL`) |
| notify | 8082 | Redis y SMTP internos (o SMTP externo) |
| rabbit-admin | 8086 | Administra el clúster por la API de gestión |
| RabbitMQ `rabbit1@localhost` + `rabbit2@localhost` | 5672/5673, gestión 15672/15673 | Clúster de dos nodos dentro del contenedor |
| Redis | 6379 | Idempotencia de notify |
| Mailpit | 1025 (SMTP), 8025 (web) | Captura los correos |

`supervisord` arranca todos los procesos, los reinicia si se caen y envía sus logs a la salida del contenedor.
**Oracle no va dentro**: shipments y catalog se conectan a una base externa con las variables de abajo.

Esta imagen sirve para **probar**. El despliegue que pide la evaluación (EC2 + docker compose + API Gateway) sigue
en `infra/`.

## Configuración en Render (New → Web Service)

| Campo | Valor |
|---|---|
| Language | `Docker` |
| Branch | la rama que se quiere probar |
| Root Directory | **vacío** (la imagen necesita varias carpetas del repo) |
| Dockerfile Path | `./Dockerfile` (el valor por defecto) |
| Instance Type | **2 GB de RAM como mínimo** (medido: ~1,4 GB al arrancar y ~1 GB estable). 4 GB da margen para pruebas de carga. Con 512 MB no arranca |
| Health Check Path (Advanced) | `/health` |

No hace falta definir `PORT`: nginx usa el que pone Render.
El build tarda bastante porque ejecuta las pruebas de los cinco microservicios y del frontend, igual que el CI.

## Variables de entorno

Obligatorias (sin ellas el contenedor se detiene al arrancar con un mensaje que dice cuáles faltan):

| Variable | Ejemplo / descripción |
|---|---|
| `DB_URL` | URL JDBC de Oracle, la misma para shipments y catalog. Ver "Base de datos" |
| `SHIPMENTS_DB_PASSWORD` | Clave del usuario `ruta_shipments` |
| `CATALOG_DB_PASSWORD` | Clave del usuario `ruta_catalog` |
| `RABBITMQ_PASSWORD` | Clave de la cuenta RabbitMQ que crea el contenedor (también protege `/mailpit/`) |

Opcionales:

| Variable | Por defecto | Para qué |
|---|---|---|
| `RABBITMQ_USERNAME` | `rutaexpress` | Usuario de RabbitMQ, del Management UI y de Mailpit |
| `SHIPMENTS_DB_USERNAME` / `CATALOG_DB_USERNAME` | `ruta_shipments` / `ruta_catalog` | Usuarios de Oracle |
| `SECURITY_ENABLED` | `true` | `false` desactiva el JWT en todos los servicios (todo queda abierto; solo para una prueba corta) |
| `AZURE_AD_ISSUER_URI` / `AZURE_AD_AUDIENCE` | tenant y app del proyecto | Validación del JWT de Entra ID |
| `MSAL_REDIRECT_URI` | `RENDER_EXTERNAL_URL` (la pone Render) | Redirect URI de MSAL. Registrarla en Entra ID como **SPA** |
| `MSAL_CLIENT_ID` / `MSAL_AUTHORITY` / `MSAL_SCOPE` | los del proyecto | Configuración de MSAL del frontend |
| `MAIL_HOST` / `MAIL_PORT` | Mailpit interno (`localhost:1025`) | SMTP real, por ejemplo `smtp.gmail.com` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_SMTP_AUTH` / `MAIL_STARTTLS` | vacío / `false` | Credenciales del SMTP real (`true` / `true` con Gmail) |
| `RABBIT_DLQ_CONSUMER_ENABLED` | `true` | `false` deja los mensajes en las DLQ para verlos en el Management UI |
| `JAVA_OPTS_BFF`, `JAVA_OPTS_SHIPMENTS`, `JAVA_OPTS_CATALOG`, `JAVA_OPTS_NOTIFY`, `JAVA_OPTS_RABBIT_ADMIN` | heap de 192 a 320 MB por servicio | Reemplazan las opciones de la JVM de ese servicio |

## Base de datos (Oracle externo)

Hacen falta **dos usuarios distintos** (`ruta_shipments` y `ruta_catalog`): cada servicio aplica sus propias
migraciones Flyway y no pueden compartir esquema. Flyway crea las tablas solo al arrancar.

**Oracle Autonomous Database (Always Free, en OCI)**, con "Mutual TLS (mTLS) authentication" desactivado y la lista
de acceso abierta a Render (o a todos, para la prueba). Como ADMIN, en Database Actions → SQL:

```sql
CREATE USER ruta_shipments IDENTIFIED BY "<clave de 12+ caracteres con mayúscula, minúscula y número>";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO ruta_shipments;
ALTER USER ruta_shipments QUOTA UNLIMITED ON DATA;

CREATE USER ruta_catalog IDENTIFIED BY "<otra clave>";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO ruta_catalog;
ALTER USER ruta_catalog QUOTA UNLIMITED ON DATA;
```

`DB_URL` es la cadena de conexión TLS que muestra la consola ("Database connection" → TLS, perfil `_low`), copiada
tal cual y con el prefijo `jdbc:oracle:thin:@`. Tiene esta forma:

```
jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=<puerto>)(host=adb.<región>.oraclecloud.com))(connect_data=(service_name=<id>_low.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
```

Cualquier otra Oracle accesible desde Internet sirve igual (`jdbc:oracle:thin:@//<host>:1521/<servicio>`), creando
los usuarios como en `infra/apps/oracle-init/001-users.sh`.

## Después del deploy

| URL | Qué es |
|---|---|
| `https://<servicio>.onrender.com/` | Frontend (login con Entra ID) |
| `https://<servicio>.onrender.com/rabbitmq/` | Management UI: los dos nodos, colas, DLQ y políticas (`RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`) |
| `https://<servicio>.onrender.com/mailpit/` | Correos enviados por notify (mismas credenciales) |
| `https://<servicio>.onrender.com/health` | Salud de nginx (lo usa Render) |

Los microservicios tardan uno o dos minutos en estar listos después de que Render marca el deploy como "Live": hasta
entonces el API responde 502. En los logs se ve cada servicio con su nombre (`[ms-rutaexpress-…]`).

Para el login real, en la app de Entra ID agregar `https://<servicio>.onrender.com` como Redirect URI de tipo
**Single-page application** (ver §5.3 de `PLAN_MAESTRO_IMPLEMENTACION_EP2.md` para el resto de la configuración).

Render no guarda disco en esta imagen: cada deploy o reinicio arranca RabbitMQ, Redis y Mailpit vacíos. Los datos de
negocio quedan en Oracle.

## Probar la misma imagen en un PC

```sh
docker build -t rutaexpress-todo-en-uno .
docker run --rm -p 10000:10000 \
  -e DB_URL=jdbc:oracle:thin:@//<host>:1521/FREEPDB1 \
  -e SHIPMENTS_DB_PASSWORD=... -e CATALOG_DB_PASSWORD=... -e RABBITMQ_PASSWORD=... \
  -e MSAL_REDIRECT_URI=http://localhost:10000 \
  rutaexpress-todo-en-uno
```
