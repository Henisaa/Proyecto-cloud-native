#!/usr/bin/env bash
# Arranca un microservicio Spring con el entorno que en docker compose ponía infra/apps/compose.yml. Todos comparten
# el contenedor, así que cada JVM recibe un tope de memoria fijo (JAVA_OPTS_<SERVICIO> lo reemplaza si hace falta).
set -euo pipefail
servicio=$1

export RABBITMQ_ADDRESSES=localhost:5672,localhost:5673

case "$servicio" in
  bff)
    export PORT=8080
    export CATALOG_SERVICE_URL=http://localhost:8081
    export NOTIFY_SERVICE_URL=http://localhost:8082
    export SHIPMENTS_SERVICE_URL=http://localhost:8085
    export MQ_ADMIN_SERVICE_URL=http://localhost:8086
    heap=256m
    ;;
  catalog)
    export PORT=8081
    export SPRING_PROFILES_ACTIVE=prod
    export SPRING_DATASOURCE_URL="$DB_URL"
    export SPRING_DATASOURCE_USERNAME="$CATALOG_DB_USERNAME"
    export SPRING_DATASOURCE_PASSWORD="$CATALOG_DB_PASSWORD"
    export KAFKA_ENABLED=false
    heap=320m
    ;;
  shipments)
    export PORT=8085
    export SPRING_PROFILES_ACTIVE=prod
    export SPRING_DATASOURCE_URL="$DB_URL"
    export SPRING_DATASOURCE_USERNAME="$SHIPMENTS_DB_USERNAME"
    export SPRING_DATASOURCE_PASSWORD="$SHIPMENTS_DB_PASSWORD"
    export CATALOGO_ENABLED=true
    export CATALOG_SERVICE_URL=http://localhost:8081
    export KAFKA_ENABLED=false
    heap=320m
    ;;
  notify)
    export PORT=8082
    export REDIS_HOST=localhost
    export REDIS_PORT=6379
    heap=224m
    ;;
  rabbit-admin)
    export PORT=8086
    export RABBITMQ_MANAGEMENT_URLS=http://localhost:15672/rabbitmq,http://localhost:15673/rabbitmq
    heap=192m
    ;;
  *)
    echo "Servicio desconocido: $servicio" >&2
    exit 1
    ;;
esac

propias="JAVA_OPTS_$(echo "$servicio" | tr 'a-z-' 'A-Z_')"
export JAVA_TOOL_OPTIONS="${!propias:--Xms64m -Xmx$heap -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8}"

exec java -jar "/app/$servicio.jar"
