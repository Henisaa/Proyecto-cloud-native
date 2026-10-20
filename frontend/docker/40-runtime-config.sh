#!/bin/sh
set -eu
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
