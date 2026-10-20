#!/usr/bin/env bash
set -euo pipefail
install -m 600 -o rabbitmq -g rabbitmq /run/secrets/erlang_cookie /var/lib/rabbitmq/.erlang.cookie

# Loading definitions suppresses RabbitMQ's default-user seeding. Only the first
# node creates the configured account; cluster replication supplies it to rabbit2.
# Existing accounts must authenticate: never silently rotate an existing password.
if [ "${HOSTNAME}" = rabbit1 ]; then
  (
    rabbitmqctl await_startup
    if ! rabbitmqctl authenticate_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS" >/dev/null 2>&1; then
      rabbitmqctl add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
    fi
    rabbitmqctl set_user_tags "$RABBITMQ_DEFAULT_USER" administrator
    rabbitmqctl set_permissions -p / "$RABBITMQ_DEFAULT_USER" '.*' '.*' '.*'
  ) &
fi
exec docker-entrypoint.sh rabbitmq-server
