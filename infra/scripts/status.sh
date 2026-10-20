#!/usr/bin/env bash
# Estado de todos los compose y del clúster RabbitMQ (todo debe estar healthy).
set -euo pipefail
cd "$(dirname "$0")/.."
for dir in mq apps kafka edge; do
  echo "== $dir"
  docker compose --env-file .env -f "$dir/compose.yml" ps --format 'table {{.Service}}\t{{.State}}\t{{.Health}}' || true
done
echo "== Clúster RabbitMQ"
docker compose --env-file .env -f mq/compose.yml exec -T rabbit1 rabbitmqctl cluster_status | sed -n '1,25p' || true
