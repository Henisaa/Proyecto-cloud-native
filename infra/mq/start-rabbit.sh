#!/usr/bin/env bash
set -euo pipefail
install -m 600 -o rabbitmq -g rabbitmq /run/secrets/erlang_cookie /var/lib/rabbitmq/.erlang.cookie
ctl() { gosu rabbitmq rabbitmqctl "$@"; }

# Loading definitions suppresses RabbitMQ's default-user seeding. Only the first
# node creates the configured account; cluster replication supplies it to rabbit2.
# Existing accounts must authenticate: never silently rotate an existing password.
if [ "${HOSTNAME}" = rabbit1 ]; then
  (
    ready=false
    for attempt in {1..60}; do
      if ctl await_startup >/dev/null 2>&1; then ready=true; break; fi
      sleep 2
    done
    if [ "$ready" != true ]; then
      echo 'RabbitMQ did not become ready for account initialization' >&2
      exit 1
    fi
    if ! ctl authenticate_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS" >/dev/null 2>&1; then
      ctl add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
    fi
    ctl set_user_tags "$RABBITMQ_DEFAULT_USER" administrator
    ctl set_permissions -p / "$RABBITMQ_DEFAULT_USER" '.*' '.*' '.*'
  ) &
fi
exec docker-entrypoint.sh rabbitmq-server
