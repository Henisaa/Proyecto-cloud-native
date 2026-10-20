#!/usr/bin/env bash
set -euo pipefail
mkdir -p infra/secrets
openssl rand -hex 24 > infra/secrets/erlang.cookie
chmod 600 infra/secrets/erlang.cookie
docker network create rutaexpress
mq() { docker compose --env-file tests/system/ci.env -p ci-bootstrap -f infra/mq/compose.yml "$@"; }
cleanup() {
  code=$?
  trap - EXIT
  mq logs --no-color || true
  mq down --volumes || true
  exit "$code"
}
trap cleanup EXIT
mq up -d --wait --wait-timeout 180
for port in 15672 15673; do
  curl --retry 10 --retry-all-errors --retry-delay 2 -fsS \
    -u ci-user:ci-disposable-rabbit "http://127.0.0.1:$port/api/nodes" |
    jq -e 'map(select(.running)) | length == 2'
done
# A restart must preserve the account and cluster, without duplicate initialization.
mq restart rabbit1
mq up -d --wait --wait-timeout 180
curl --retry 15 --retry-all-errors --retry-delay 2 -fsS \
  -u ci-user:ci-disposable-rabbit http://127.0.0.1:15672/api/nodes |
  jq -e 'map(select(.running)) | length == 2'
