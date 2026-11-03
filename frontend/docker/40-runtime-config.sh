#!/bin/sh
set -eu
# Solo se permiten orígenes HTTP(S) en la CSP; no se interpolan rutas ni caracteres de configuración nginx.
api_origin=$(printf '%s' "${API_BASE_URL:-}" | jq -Rr 'if . == "" then "" elif test("^https?://[A-Za-z0-9.-]+(:[0-9]+)?/?$") then sub("/$"; "") else error("API_BASE_URL debe ser un origen HTTP(S)") end')
cat > /tmp/rutaexpress-headers.conf <<EOF
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' https://login.microsoftonline.com $api_origin; frame-src 'self' https://login.microsoftonline.com; frame-ancestors 'none'; base-uri 'self'; object-src 'none'" always;
EOF
jq -n \
  --arg apiBaseUrl "${API_BASE_URL:-}" \
  --arg environmentName "${ENVIRONMENT_NAME:-produccion}" \
  --arg clientId "${MSAL_CLIENT_ID:?falta MSAL_CLIENT_ID}" \
  --arg authority "${MSAL_AUTHORITY:?falta MSAL_AUTHORITY}" \
  --arg redirectUri "${MSAL_REDIRECT_URI:?falta MSAL_REDIRECT_URI}" \
  --arg postLogoutRedirectUri "${MSAL_POST_LOGOUT_REDIRECT_URI:-${MSAL_REDIRECT_URI}}" \
  --arg scope "${MSAL_SCOPE:?falta MSAL_SCOPE}" \
  '{apiBaseUrl:$apiBaseUrl,environmentName:$environmentName,clientId:$clientId,authority:$authority,redirectUri:$redirectUri,postLogoutRedirectUri:$postLogoutRedirectUri,scope:$scope}' \
  > /usr/share/nginx/html/config.json
