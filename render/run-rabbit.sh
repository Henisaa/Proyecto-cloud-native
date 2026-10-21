#!/usr/bin/env bash
# Arranca el nodo RabbitMQ indicado (1 o 2) dentro del mismo contenedor. Cada nodo tiene su nombre, sus puertos y su
# configuración (rabbit1.conf / rabbit2.conf); ambos forman el clúster por descubrimiento clásico. El nodo 2 espera
# a que el 1 esté en marcha para unirse a él y no formar un clúster aparte.
set -euo pipefail
nodo=$1

export RABBITMQ_NODENAME="rabbit${nodo}@localhost"
export RABBITMQ_CONFIG_FILE="/opt/rutaexpress/rabbit${nodo}.conf"
# Directorio vacío (lo crea start.sh): no se cargan los conf.d de la imagen oficial.
export RABBITMQ_CONFIG_FILES=/etc/rutaexpress/rabbit-conf.d
# El puerto de distribución Erlang se calcula con estas variables, no con el .conf: sin ellas ambos nodos usarían 25672.
export RABBITMQ_NODE_PORT=$((5671 + nodo))
export RABBITMQ_DIST_PORT=$((25671 + nodo))
# Primer arranque del clúster: el nodo 1 crea esta cuenta en lugar del usuario guest.
export RABBITMQ_DEFAULT_USER="$RABBITMQ_USERNAME"
export RABBITMQ_DEFAULT_PASS="$RABBITMQ_PASSWORD"

if [ "$nodo" = 2 ]; then
  until rabbitmq-diagnostics -q -n rabbit1@localhost check_running >/dev/null 2>&1; do
    sleep 2
  done
fi

exec rabbitmq-server
