#!/usr/bin/env bash
# Levanta el sistema completo en el orden que exige la plataforma:
#   red -> RabbitMQ (2 nodos healthy) -> datos y aplicaciones -> Kafka (si COMPOSE_PROFILES incluye ep1) -> entrada.
# Uso: ./infra/scripts/up.sh [--dev]   (--dev construye desde el monorepo y publica puertos locales)
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "Falta infra/.env: copie infra/.env.example y complete los valores."; exit 1; }
DEV=()
[ "${1:-}" = "--dev" ] && DEV=(dev)

dc() { local dir=$1; shift; local files=(-f "$dir/compose.yml"); [ ${#DEV[@]} -gt 0 ] && [ -f "$dir/compose.dev.yml" ] && files+=(-f "$dir/compose.dev.yml"); docker compose --env-file .env "${files[@]}" "$@"; }

docker network inspect rutaexpress >/dev/null 2>&1 || docker network create rutaexpress
bash ./scripts/init-secrets.sh

dc mq up -d --wait rabbit1 rabbit2

if grep -qE '^COMPOSE_PROFILES=.*ep1' .env; then
  dc kafka up -d --wait
fi

if [ ${#DEV[@]} -gt 0 ]; then dc apps up -d --build; else dc apps pull --quiet; dc apps up -d; fi
dc apps up -d --wait

if [ ${#DEV[@]} -gt 0 ]; then dc edge up -d --build frontend; else dc edge pull --quiet; dc edge up -d; fi
echo "Listo. Estado: ./infra/scripts/status.sh"
