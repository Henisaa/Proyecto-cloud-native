#!/usr/bin/env bash
# Baja todo SIN borrar volúmenes. Para borrar datos use: ./infra/scripts/down.sh --volumes (solo tras la evaluación).
set -euo pipefail
cd "$(dirname "$0")/.."
EXTRA=()
[ "${1:-}" = "--volumes" ] && EXTRA=(--volumes)
for dir in edge apps kafka mq; do
  docker compose --env-file .env -f "$dir/compose.yml" down "${EXTRA[@]}" || true
done
