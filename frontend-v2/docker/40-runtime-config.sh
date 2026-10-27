#!/bin/sh
set -eu
# jq quotes every value as JSON; environment values are never evaluated as JavaScript.
printf 'window.RUTAEXPRESS_CONFIG = ' > /usr/share/nginx/html/config.js
jq -n \
  --arg clientId "${MSAL_CLIENT_ID:?falta MSAL_CLIENT_ID}" \
  --arg authority "${MSAL_AUTHORITY:?falta MSAL_AUTHORITY}" \
  --arg scope "${MSAL_SCOPE:?falta MSAL_SCOPE}" \
  --arg redirectUri "${MSAL_REDIRECT_URI:-http://localhost:4200}" \
  '{apiBaseUrl:"",authMode:"msal",msal:{clientId:$clientId,authority:$authority,scope:$scope,redirectUri:$redirectUri},kafkaGroups:["ms-rutaexpress-report-group","ms-rutaexpress-audit-group"]}' \
  >> /usr/share/nginx/html/config.js
printf ';\n' >> /usr/share/nginx/html/config.js
