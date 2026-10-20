#!/usr/bin/env bash
# Genera la cookie de Erlang del clúster RabbitMQ (secreto de Docker, nunca en Git).
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p secrets
if [ -s secrets/erlang.cookie ]; then
  echo "secrets/erlang.cookie ya existe; no se reemplaza (cambiarla rompe el clúster existente)."
else
  head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 40 > secrets/erlang.cookie
  chmod 600 secrets/erlang.cookie
  echo "secrets/erlang.cookie creada."
fi
