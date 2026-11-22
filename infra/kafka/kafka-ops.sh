#!/usr/bin/env bash
# Operaciones replicables de administración de Kafka (tópicos, configuraciones y consumer groups).
# Compatible con el stack raíz (compose.yaml) y con la capa modular (infra/kafka/compose.yml).
#
# Uso:
#   bash infra/kafka/kafka-ops.sh init-topics
#   bash infra/kafka/kafka-ops.sh list-topics
#   bash infra/kafka/kafka-ops.sh describe-topic shipments.events
#   bash infra/kafka/kafka-ops.sh create-topic rutaexpress.demo.ep5 3 3 600000 delete
#   bash infra/kafka/kafka-ops.sh alter-partitions rutaexpress.demo.ep5 6
#   bash infra/kafka/kafka-ops.sh alter-config rutaexpress.demo.ep5 retention.ms=300000
#   bash infra/kafka/kafka-ops.sh delete-config rutaexpress.demo.ep5 retention.ms
#   bash infra/kafka/kafka-ops.sh delete-topic rutaexpress.demo.ep5
#   bash infra/kafka/kafka-ops.sh list-groups
#   bash infra/kafka/kafka-ops.sh describe-group ms-rutaexpress-audit-group

set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:29092,kafka-2:29092,kafka-3:29092}"
REPLICATION_FACTOR="${KAFKA_REPLICATION_FACTOR:-3}"
MIN_ISR="${KAFKA_MIN_ISR:-2}"

detect_container() {
  if [ -n "${KAFKA_CONTAINER:-}" ]; then
    echo "$KAFKA_CONTAINER"
    return 0
  fi
  local found
  found="$(docker ps --filter "label=com.docker.compose.service=kafka-1" --format '{{.Names}}' | head -n 1 || true)"
  if [ -z "$found" ]; then
    echo "No se encontró un contenedor en ejecución para kafka-1. Defina KAFKA_CONTAINER o levante Kafka primero." >&2
    exit 1
  fi
  echo "$found"
}

run_kafka_tool() {
  local tool="$1"
  shift
  local container
  container="$(detect_container)"
  docker exec -i "$container" "/opt/kafka/bin/$tool" --bootstrap-server "$BOOTSTRAP" "$@"
}

ensure_topic() {
  local topic="$1"
  local partitions="${2:-3}"
  local retention_ms="${3:-604800000}"
  local cleanup_policy="${4:-delete}"
  run_kafka_tool kafka-topics.sh --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION_FACTOR" \
    --config "retention.ms=$retention_ms" \
    --config "cleanup.policy=$cleanup_policy" \
    --config "min.insync.replicas=$MIN_ISR"
}

usage() {
  cat <<'EOF'
Uso: bash infra/kafka/kafka-ops.sh <comando> [argumentos]

Comandos disponibles:
  init-topics
  list-topics
  describe-topic <topic>
  create-topic <topic> [partitions] [replicas] [retention_ms] [cleanup_policy]
  alter-partitions <topic> <partitions>
  alter-config <topic> <key=value>
  delete-config <topic> <key>
  delete-topic <topic>
  list-groups
  describe-group <group_id>
EOF
}

cmd="${1:-}"
shift || true

case "$cmd" in
  init-topics)
    ensure_topic "shipments.events" 3 604800000 "delete"
    ensure_topic "audit.timeline" 3 2592000000 "compact,delete"
    ensure_topic "catalog.events" 3 604800000 "delete"
    ensure_topic "tutorial-particiones" 3 604800000 "delete"
    ensure_topic "sensor-topic" 3 300000 "delete"
    ensure_topic "ordenes-creadas" 3 604800000 "delete"
    ensure_topic "shipments.events.audit.DLT" 3 1209600000 "delete"
    ensure_topic "audit.timeline.audit.DLT" 3 1209600000 "delete"
    ensure_topic "shipments.events.report.DLT" 3 1209600000 "delete"
    ensure_topic "ordenes-creadas.DLT" 3 1209600000 "delete"
    ;;
  list-topics)
    run_kafka_tool kafka-topics.sh --list
    ;;
  describe-topic)
    topic="${1:?Indique el nombre del tópico}"
    run_kafka_tool kafka-topics.sh --describe --topic "$topic"
    run_kafka_tool kafka-configs.sh --entity-type topics --entity-name "$topic" --describe
    ;;
  create-topic)
    topic="${1:?Indique el nombre del tópico}"
    partitions="${2:-3}"
    replicas="${3:-$REPLICATION_FACTOR}"
    retention_ms="${4:-604800000}"
    cleanup_policy="${5:-delete}"
    min_isr="1"
    if [ "$replicas" -ge 3 ]; then min_isr="$MIN_ISR"; fi
    run_kafka_tool kafka-topics.sh --create --if-not-exists \
      --topic "$topic" \
      --partitions "$partitions" \
      --replication-factor "$replicas" \
      --config "retention.ms=$retention_ms" \
      --config "cleanup.policy=$cleanup_policy" \
      --config "min.insync.replicas=$min_isr"
    ;;
  alter-partitions)
    topic="${1:?Indique el nombre del tópico}"
    partitions="${2:?Indique el nuevo número de particiones}"
    run_kafka_tool kafka-topics.sh --alter --topic "$topic" --partitions "$partitions"
    ;;
  alter-config)
    topic="${1:?Indique el nombre del tópico}"
    kv="${2:?Indique key=value}"
    run_kafka_tool kafka-configs.sh --entity-type topics --entity-name "$topic" --alter --add-config "$kv"
    ;;
  delete-config)
    topic="${1:?Indique el nombre del tópico}"
    key="${2:?Indique la clave de configuración}"
    run_kafka_tool kafka-configs.sh --entity-type topics --entity-name "$topic" --alter --delete-config "$key"
    ;;
  delete-topic)
    topic="${1:?Indique el nombre del tópico}"
    run_kafka_tool kafka-topics.sh --delete --topic "$topic"
    ;;
  list-groups)
    run_kafka_tool kafka-consumer-groups.sh --list
    ;;
  describe-group)
    group="${1:?Indique el group_id}"
    run_kafka_tool kafka-consumer-groups.sh --describe --group "$group"
    ;;
  *)
    usage
    exit 1
    ;;
esac
