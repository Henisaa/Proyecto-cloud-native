#!/usr/bin/env bash
# Despliega RutaExpress EP2 (sin frontend) en cualquier VPS Linux: Azure VM, AWS EC2, Google Cloud, DigitalOcean...
#
# Todo corre en Docker: clúster RabbitMQ de 2 nodos, Oracle Free, Redis, Mailpit y los microservicios bff, shipments,
# catalog, notify y rabbit-admin, construidos desde este repo con sus pruebas. El frontend se publica aparte (Vercel).
# Si Docker no está instalado, lo instala. Los microservicios quedan accesibles en http://<ip-de-la-vps>:<puerto>.
#
# Requisitos: Linux x86_64 o arm64, 8 GB de RAM recomendados (con menos se crea swap), unos 15 GB libres y salida a
# Internet (Docker Hub, Maven Central y, con la seguridad activa, login.microsoftonline.com).
#
# Uso, desde la raíz del repo clonado (si no es root, se vuelve a ejecutar con sudo):
#   bash deploy-vps.sh               instala Docker si falta, genera infra/vps/.env, construye y levanta todo
#   bash deploy-vps.sh --no-build    levanta sin reconstruir las imágenes
#   bash deploy-vps.sh --status      estado de los contenedores y del clúster RabbitMQ
#   bash deploy-vps.sh --logs [svc]  logs en vivo (de todos o de un servicio, por ejemplo bff)
#   bash deploy-vps.sh --down        detiene todo sin borrar datos
#
# La primera vez se pueden pasar estas variables; quedan guardadas en infra/vps/.env (se edita ahí después):
#   CORS_ALLOWED_ORIGINS=https://mi-app.vercel.app   origen del frontend que llama al BFF directo
#   SECURITY_ENABLED=false                           sin validación JWT (todo queda abierto: solo para pruebas)
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  exec sudo -E bash "$0" "$@"
fi
cd "$(dirname "$(readlink -f "$0")")"

COMPOSE_FILE=infra/vps/compose.yml
ENV_FILE=infra/vps/.env
SERVICIOS=(rabbit-admin notify catalog shipments bff)

paso() { echo; echo "==> $*"; }
aviso() { echo "AVISO: $*" >&2; }
fallo() { echo "ERROR: $*" >&2; exit 1; }
dc() {
  [ -f "$ENV_FILE" ] || fallo "no existe $ENV_FILE: ejecute primero bash deploy-vps.sh"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

instalar_paquetes() {
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@"
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y -q "$@"
  elif command -v yum >/dev/null 2>&1; then
    yum install -y -q "$@"
  elif command -v zypper >/dev/null 2>&1; then
    zypper -n install "$@"
  else
    fallo "no se encontró un gestor de paquetes para instalar: $*"
  fi
}

asegurar_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    paso "Docker no está instalado: instalándolo"
    command -v curl >/dev/null 2>&1 || instalar_paquetes curl ca-certificates
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${ID:-}" in
      amzn)
        instalar_paquetes docker
        ;;
      rocky|almalinux|ol|centos|rhel)
        repo=centos
        [ "$ID" = rhel ] && repo=rhel
        instalar_paquetes dnf-plugins-core
        dnf config-manager --add-repo "https://download.docker.com/linux/$repo/docker-ce.repo"
        instalar_paquetes docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
        ;;
      *)
        # Script oficial de Docker (Ubuntu, Debian, Fedora, Raspbian...); si la versión de la distro aún no está
        # en el repositorio de Docker, se usan los paquetes de la propia distro.
        curl -fsSL https://get.docker.com | sh || instalar_paquetes docker.io docker-compose-v2 || instalar_paquetes docker.io
        ;;
    esac
  fi

  if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
    systemctl enable --now docker >/dev/null 2>&1 || true
  fi
  docker info >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true
  for _ in $(seq 1 15); do
    docker info >/dev/null 2>&1 && break
    sleep 1
  done
  docker info >/dev/null 2>&1 || fallo "Docker está instalado pero no responde (revise: systemctl status docker)"

  if ! docker compose version >/dev/null 2>&1; then
    paso "Instalando el plugin Docker Compose"
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
  fi
  echo "$(docker --version) | $(docker compose version)"
}

revisar_recursos() {
  local ram swap disco
  ram=$(awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo)
  swap=$(awk '/SwapTotal/ {print int($2 / 1024)}' /proc/meminfo)
  disco=$(df -Pm . | awk 'NR == 2 {print $4}')
  paso "Recursos: ${ram} MB de RAM, ${swap} MB de swap, ${disco} MB libres en disco"
  # Oracle (3 GB) + 5 microservicios + 2 nodos RabbitMQ: 8 GB recomendado. Con menos, el swap evita que el kernel
  # mate a Oracle o a un build por falta de memoria.
  if [ "$ram" -lt 7000 ] && [ "$swap" -lt 1024 ] && [ ! -e /swapfile ]; then
    echo "Menos de 8 GB de RAM y sin swap: se crea /swapfile de 4 GB"
    if { fallocate -l 4G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=4096 status=none; } \
      && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile; then
      grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
    else
      aviso "no se pudo activar el swap; continúe solo si la VPS tiene memoria suficiente"
      rm -f /swapfile
    fi
  fi
  [ "$ram" -ge 3500 ] || aviso "con menos de 4 GB de RAM Oracle y los microservicios difícilmente caben (recomendado: 8 GB)"
  [ "$disco" -ge 15000 ] || aviso "quedan menos de 15 GB libres; las imágenes y los builds ocupan alrededor de 10 GB"
}

clave() {
  local aleatorio
  aleatorio=$(LC_ALL=C tr -dc 'A-Za-z0-9' < <(head -c 512 /dev/urandom) | cut -c1-20)
  echo "Rx${aleatorio}9"
}

generar_env() {
  if [ -f "$ENV_FILE" ]; then
    paso "Usando la configuración existente de $ENV_FILE"
    return
  fi
  paso "Generando $ENV_FILE con contraseñas aleatorias (no se sube a Git)"
  (
    umask 077
    cat > "$ENV_FILE" <<EOF
# Generado por deploy-vps.sh el $(date -u +%Y-%m-%d). Contiene contraseñas: no subir a Git.
# Tras editarlo: bash deploy-vps.sh --no-build

# --- Seguridad (Microsoft Entra ID). false = sin JWT, todo abierto: solo para pruebas.
SECURITY_ENABLED=${SECURITY_ENABLED:-true}
AZURE_AD_ISSUER_URI=https://login.microsoftonline.com/bc307149-9a0a-45b8-9f7d-2dfc104f9a09/v2.0
AZURE_AD_AUDIENCE=api://f3136620-804c-4b15-b22d-b6fad957937e

# --- Origen(es) del frontend que llaman al BFF directo, separados por coma (https://mi-app.vercel.app).
#     Vacío si delante hay un API Gateway que resuelve CORS.
CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-}

# --- RabbitMQ (también es el usuario del Management UI y de Mailpit)
RABBITMQ_USERNAME=rutaexpress
RABBITMQ_PASSWORD=$(clave)

# --- Oracle (contenedor local). DB_URL solo si se usa una base externa.
ORACLE_ADMIN_PASSWORD=$(clave)
SHIPMENTS_DB_PASSWORD=$(clave)
CATALOG_DB_PASSWORD=$(clave)
AUDIT_DB_PASSWORD=$(clave)
REPORT_DB_PASSWORD=$(clave)
# DB_URL=jdbc:oracle:thin:@//<host>:1521/<servicio>

# --- Puertos públicos (http://<ip>:<puerto>)
BFF_PORT=8080
CATALOG_PORT=8081
NOTIFY_PORT=8082
SHIPMENTS_PORT=8085
RABBIT_ADMIN_PORT=8086
RABBIT1_UI_PORT=15672
RABBIT2_UI_PORT=15673
MAILPIT_PORT=8025

# --- Correo: por defecto Mailpit captura los correos. Para un SMTP real, complete estas variables.
# MAIL_HOST=smtp.gmail.com
# MAIL_PORT=587
# MAIL_USERNAME=
# MAIL_PASSWORD=
# MAIL_SMTP_AUTH=true
# MAIL_STARTTLS=true
EOF
  )
}

preparar_archivos() {
  bash infra/scripts/init-secrets.sh
  # Con SELinux en modo Enforcing (RHEL, Rocky, Alma), los contenedores solo leen archivos con esta etiqueta.
  if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" = Enforcing ]; then
    chcon -R -t container_file_t infra/mq infra/apps/oracle-init infra/secrets
  fi
}

construir() {
  local servicio
  for servicio in "${SERVICIOS[@]}"; do
    paso "Construyendo ms-rutaexpress-$servicio (compila y ejecuta sus pruebas; tarda unos minutos)"
    docker build -t "rutaexpress/ms-rutaexpress-$servicio:local" "ms-rutaexpress-$servicio"
  done
}

levantar() {
  local servicio
  for servicio in "${SERVICIOS[@]}"; do
    docker image inspect "rutaexpress/ms-rutaexpress-$servicio:local" >/dev/null 2>&1 \
      || fallo "falta la imagen de $servicio: ejecute bash deploy-vps.sh (sin --no-build)"
  done
  paso "Levantando contenedores (el primer arranque de Oracle tarda unos minutos)"
  # Los dos nodos RabbitMQ van primero y juntos: tras un reinicio completo cada uno espera al otro. El resto depende
  # de que rabbit1 esté sano, y docker compose aborta si lo encuentra todavía "unhealthy".
  dc up -d rabbit1 rabbit2
  local intento rabbit1
  rabbit1=$(dc ps -q rabbit1)
  for intento in $(seq 1 60); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$rabbit1")" = healthy ] && break
    [ "$intento" -eq 1 ] && echo "Esperando a que el clúster RabbitMQ esté listo..."
    sleep 5
  done
  if ! dc up -d --remove-orphans --wait; then
    estado
    echo
    echo "Si catalog o shipments muestran 'Unable to resolve the Configuration with the provided Issuer', la VPS no"
    echo "llega a login.microsoftonline.com (salida HTTPS bloqueada o DNS)."
    fallo "algún servicio no quedó sano; vea sus logs con: bash deploy-vps.sh --logs <servicio>"
  fi
}

ip_publica() {
  curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null \
    || curl -fsS --max-time 5 https://ifconfig.me 2>/dev/null \
    || hostname -I | awk '{print $1}'
}

estado() {
  dc ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
  echo
  echo "Clúster RabbitMQ:"
  dc exec -T rabbit1 rabbitmqctl cluster_status 2>/dev/null | sed -n '/Running Nodes/,/^Versions/p' || true
}

resumen() {
  local ip
  ip=$(ip_publica)
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  cat <<EOF

=====================================================================================
 RutaExpress EP2 desplegado. Accesos desde fuera (http://<ip>:<puerto>):

   BFF (API para el frontend)      http://$ip:${BFF_PORT:-8080}
   catalog                         http://$ip:${CATALOG_PORT:-8081}
   notify                          http://$ip:${NOTIFY_PORT:-8082}
   shipments                       http://$ip:${SHIPMENTS_PORT:-8085}
   rabbit-admin                    http://$ip:${RABBIT_ADMIN_PORT:-8086}
   RabbitMQ Management (nodo 1)    http://$ip:${RABBIT1_UI_PORT:-15672}
   RabbitMQ Management (nodo 2)    http://$ip:${RABBIT2_UI_PORT:-15673}
   Mailpit (correos)               http://$ip:${MAILPIT_PORT:-8025}

   Salud de cada servicio:         http://$ip:<puerto>/actuator/health
   Usuario de RabbitMQ y Mailpit:  ${RABBITMQ_USERNAME:-} (clave en $ENV_FILE)
   Seguridad JWT (Entra ID):       SECURITY_ENABLED=${SECURITY_ENABLED:-true}

 Pendiente fuera de la VPS:
   - Abrir esos puertos TCP en el firewall de la nube (Security Group en AWS, NSG en Azure).
   - Si el frontend llama directo al BFF: CORS_ALLOWED_ORIGINS en $ENV_FILE con su URL.
   - Un frontend en https (Vercel) no puede llamar a http://: el navegador lo bloquea (mixed content).
     Ponga delante un API Gateway u otro proxy con https y use esa URL en el frontend.
=====================================================================================
EOF
}

case "${1:-}" in
  --status) estado ;;
  --logs) dc logs -f --tail 100 ${2:+"$2"} ;;
  --down) paso "Deteniendo (los datos de Oracle y RabbitMQ se conservan en sus volúmenes)"; dc down ;;
  --no-build | "")
    asegurar_docker
    revisar_recursos
    generar_env
    # Desde aquí manda infra/vps/.env: una variable exportada en la terminal tendría prioridad en docker compose.
    unset SECURITY_ENABLED CORS_ALLOWED_ORIGINS
    preparar_archivos
    [ "${1:-}" = --no-build ] || construir
    levantar
    estado
    resumen
    ;;
  *) fallo "opción desconocida: $1 (use --no-build, --status, --logs, --down)" ;;
esac
