#!/usr/bin/env bash
set -euo pipefail
mkdir -p work/system-reports infra/secrets
# Cookie and keys exist only in the ephemeral runner.
openssl rand -hex 24 > infra/secrets/erlang.cookie
chmod 600 infra/secrets/erlang.cookie
docker network create rutaexpress
envfile=tests/system/ci.env
sys() { docker compose --env-file "$envfile" -p ci-system -f tests/system/compose.yml "$@"; }
mq() { docker compose --env-file "$envfile" -p ci-mq -f infra/mq/compose.yml "$@"; }
kafka() { docker compose --env-file "$envfile" -p ci-kafka -f infra/kafka/compose.yml "$@"; }
apps() { docker compose --env-file "$envfile" -p ci-apps -f infra/apps/compose.yml -f tests/system/apps.override.yml "$@"; }
diagnostics() {
  code=$?
  trap - EXIT
  for kind in sys mq kafka apps; do
    "$kind" ps -a > "work/system-reports/${kind}-status.txt" 2>&1 || true
    "$kind" logs --no-color > "work/system-reports/${kind}-logs.txt" 2>&1 || true
  done
  mq exec -T rabbit1 rabbitmqctl cluster_status > work/system-reports/cluster.txt 2>&1 || true
  # Clean only this job's isolated Compose projects. The runner is discarded afterwards.
  sys down --volumes || true
  apps down --volumes || true
  kafka down --volumes || true
  mq down --volumes || true
  exit "$code"
}
trap diagnostics EXIT
sys up -d --build --wait --wait-timeout 90 oidc
mq up -d --wait --wait-timeout 180
kafka up -d --wait --wait-timeout 300
apps up -d --wait --wait-timeout 300
sys up -d gateway frontend-e2e
curl --retry 30 --retry-all-errors --retry-delay 2 -fsS http://127.0.0.1:8088/health
curl --retry 30 --retry-all-errors --retry-delay 2 -fsS http://127.0.0.1:8089/health
docker run --rm --network host --ipc=host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/work/system-reports:/reports" rutaexpress-ci/browser-tests:test
cp work/system-reports/results.json work/system-reports/browser-results.json
