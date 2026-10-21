#!/usr/bin/env bash
# Punto de entrada de la imagen todo en uno: valida la configuración, prepara la cookie de Erlang, nginx y el
# config.json del frontend, y entrega el control a supervisord, que levanta y vigila todos los procesos.
set -euo pipefail

falta=()
for variable in DB_URL SHIPMENTS_DB_PASSWORD CATALOG_DB_PASSWORD RABBITMQ_PASSWORD; do
  [ -n "${!variable:-}" ] || falta+=("$variable")
done
if [ ${#falta[@]} -gt 0 ]; then
  echo "Faltan variables de entorno obligatorias: ${falta[*]} (ver render/README.md)." >&2
  exit 1
fi

# Valores por defecto de todo lo opcional; los procesos los heredan de supervisord.
export PUBLIC_PORT="${PORT:-10000}"
export RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-rutaexpress}"
export SHIPMENTS_DB_USERNAME="${SHIPMENTS_DB_USERNAME:-ruta_shipments}"
export CATALOG_DB_USERNAME="${CATALOG_DB_USERNAME:-ruta_catalog}"
export SECURITY_ENABLED="${SECURITY_ENABLED:-true}"
export AZURE_AD_ISSUER_URI="${AZURE_AD_ISSUER_URI:-https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09/v2.0}"
export AZURE_AD_AUDIENCE="${AZURE_AD_AUDIENCE:-api://f3136620-804c-4b15-b22d-b6fad957937e}"
export MAIL_HOST="${MAIL_HOST:-localhost}"
export MAIL_PORT="${MAIL_PORT:-1025}"
# La interfaz de Mailpit (/mailpit/) pide el mismo usuario y clave que RabbitMQ.
export MP_UI_AUTH="${RABBITMQ_USERNAME}:${RABBITMQ_PASSWORD}"

# Los dos nodos RabbitMQ y sus herramientas de línea de comandos comparten esta cookie (mismo HOME).
cookie=/var/lib/rabbitmq/.erlang.cookie
if [ ! -s "$cookie" ]; then
  head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 40 > "$cookie"
fi
chown rabbitmq:rabbitmq "$cookie"
chmod 400 "$cookie"

mkdir -p /etc/rutaexpress/rabbit-conf.d
sed "s/__PORT__/${PUBLIC_PORT}/" /opt/rutaexpress/nginx.conf.template > /etc/rutaexpress/nginx.conf

# Frontend: mismo origen que el API (nginx reenvía /api/ al BFF), por eso apiBaseUrl va vacío y no hace falta CORS.
# Sin MSAL_REDIRECT_URI se usa la URL pública que Render publica en RENDER_EXTERNAL_URL (o el origen del navegador).
redirect="${MSAL_REDIRECT_URI:-${RENDER_EXTERNAL_URL:-}}"
jq -n \
  --arg environmentName "${ENVIRONMENT_NAME:-render}" \
  --arg clientId "${MSAL_CLIENT_ID:-f3136620-804c-4b15-b22d-b6fad957937e}" \
  --arg authority "${MSAL_AUTHORITY:-https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09}" \
  --arg redirectUri "$redirect" \
  --arg postLogoutRedirectUri "${MSAL_POST_LOGOUT_REDIRECT_URI:-$redirect}" \
  --arg scope "${MSAL_SCOPE:-api://f3136620-804c-4b15-b22d-b6fad957937e/access_as_user}" \
  '{apiBaseUrl:"",environmentName:$environmentName,clientId:$clientId,authority:$authority,redirectUri:$redirectUri,postLogoutRedirectUri:$postLogoutRedirectUri,scope:$scope}' \
  > /usr/share/rutaexpress/html/config.json

exec supervisord -c /opt/rutaexpress/supervisord.conf
