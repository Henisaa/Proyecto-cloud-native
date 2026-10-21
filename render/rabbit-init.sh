#!/usr/bin/env bash
# Tarea única al arrancar: asegura la cuenta de la aplicación con permisos de administrador y elimina guest, que
# quedaría accesible desde nginx porque dentro del contenedor todas las conexiones llegan desde localhost.
set -euo pipefail
ctl() { rabbitmqctl -q -n rabbit1@localhost "$@"; }

until ctl await_startup >/dev/null 2>&1; do
  sleep 2
done
if ! ctl authenticate_user "$RABBITMQ_USERNAME" "$RABBITMQ_PASSWORD" >/dev/null 2>&1; then
  ctl add_user "$RABBITMQ_USERNAME" "$RABBITMQ_PASSWORD"
fi
ctl set_user_tags "$RABBITMQ_USERNAME" administrator
ctl set_permissions -p / "$RABBITMQ_USERNAME" '.*' '.*' '.*'
ctl delete_user guest >/dev/null 2>&1 || true
echo "Cuenta RabbitMQ '$RABBITMQ_USERNAME' lista; guest eliminado."
