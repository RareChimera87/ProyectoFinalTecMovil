#!/usr/bin/env bash
# Despliega el backend de ParcheU en el homeserver. Se corre DESDE el portátil:
#   deploy/homelab/desplegar.sh [--host usuario@homelab] [--sin-build]
# Construye la imagen con Jib, la copia por scp con el compose, y en el homeserver hace docker load,
# migra, levanta la API y verifica la salud. No toca otros contenedores.
set -euo pipefail

HOST="user@homelab"
SIN_BUILD=0
PUERTO=8090
DIR_REMOTO="parcheu"

uso() {
  cat <<USO
Uso: $0 [--host usuario@servidor] [--sin-build] [--ayuda]
  --host        destino SSH (por defecto user@homelab)
  --sin-build   reutiliza backend/build/jib-image.tar sin reconstruir
USO
}

while [ $# -gt 0 ]; do
  case "$1" in
    --host) [ $# -ge 2 ] || { echo "Falta el valor de --host" >&2; exit 2; }; HOST="$2"; shift 2 ;;
    --sin-build) SIN_BUILD=1; shift ;;
    -h|--ayuda) uso; exit 0 ;;
    *) echo "Opción desconocida: $1" >&2; uso >&2; exit 2 ;;
  esac
done

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
NOMBRE_HOST="${HOST#*@}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=10)

paso() { printf '\n==> %s\n' "$*"; }
falla() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
remoto() { ssh "${SSH_OPTS[@]}" "$HOST" "$@"; }

paso "Comprobando acceso SSH a $HOST"
remoto 'docker compose version >/dev/null' || falla "No hay SSH sin contraseña a $HOST o falta Docker Compose en el servidor."

paso "Verificando que el puerto $PUERTO esté libre (o sea de ParcheU)"
remoto "PUERTO=$PUERTO bash -s" <<'REMOTO' || falla "El puerto $PUERTO del servidor está ocupado por otro servicio. Libéralo o cambia el puerto en compose.yaml."
set -u
if docker ps --filter name=parcheu-api --format '{{.Ports}}' | grep -q ":$PUERTO->"; then
  echo "Ya lo usa parcheu-api (redespliegue)."
  exit 0
fi
if command -v ss >/dev/null 2>&1; then
  if ss -ltnH "sport = :$PUERTO" | grep -q .; then exit 1; fi
else
  if (exec 3<>"/dev/tcp/127.0.0.1/$PUERTO") 2>/dev/null; then exit 1; fi
fi
echo "Libre."
REMOTO

TAR="$RAIZ/backend/build/jib-image.tar"
if [ "$SIN_BUILD" -eq 0 ]; then
  paso "Construyendo la imagen con Jib"
  SHA="$(git -C "$RAIZ" rev-parse --short=12 HEAD 2>/dev/null || echo homelab)"
  (cd "$RAIZ/backend" && ./gradlew jibBuildTar -PsinContenedores -PgitSha="$SHA" -Pimagen=parcheu-api)
fi
[ -f "$TAR" ] || falla "No existe $TAR (corre sin --sin-build)."

paso "Copiando imagen, compose y respaldo a $HOST:~/$DIR_REMOTO/"
remoto "mkdir -p ~/$DIR_REMOTO/secrets ~/$DIR_REMOTO/backups && chmod 700 ~/$DIR_REMOTO/secrets"
scp "${SSH_OPTS[@]}" "$TAR" "$HOST:$DIR_REMOTO/jib-image.tar"
scp "${SSH_OPTS[@]}" "$AQUI/compose.yaml" "$AQUI/respaldar.sh" "$AQUI/.env.example" "$HOST:$DIR_REMOTO/"
remoto "chmod +x ~/$DIR_REMOTO/respaldar.sh"

paso "Preparando ~/$DIR_REMOTO/.env (no se sobrescribe si ya existe)"
remoto "DIR=$DIR_REMOTO bash -s" <<'REMOTO'
set -euo pipefail
cd ~/"$DIR"
aleatorio() { head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
if [ ! -f .env ]; then
  umask 077
  {
    echo "PARCHEU_ENV=local"
    echo "POSTGRES_PASSWORD=$(aleatorio)"
    echo "HMAC_ANON_KEY=$(aleatorio)"
    echo "APP_VERSION=homelab"
    echo "INTERNAL_OIDC_AUDIENCE=https://api-parcheu.homeserverlab.site"
  } > .env
  echo "Creado .env con contraseñas nuevas."
else
  # Un .env de un despliegue anterior puede no traer claves nuevas; se agregan sin tocar las existentes.
  for clave in POSTGRES_PASSWORD HMAC_ANON_KEY; do
    grep -q "^$clave=" .env || echo "$clave=$(aleatorio)" >> .env
  done
  echo "Se conserva el .env existente."
fi
chmod 600 .env
REMOTO

paso "Cargando la imagen y levantando los servicios"
remoto "DIR=$DIR_REMOTO bash -s" <<'REMOTO'
set -euo pipefail
cd ~/"$DIR"
docker load -i jib-image.tar
rm -f jib-image.tar
docker compose up -d parcheu-db
docker compose run --rm parcheu-migrate
docker compose up -d --remove-orphans parcheu-api
REMOTO

paso "Comprobando salud en http://$NOMBRE_HOST:$PUERTO/v1/health"
ok=0
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 "http://$NOMBRE_HOST:$PUERTO/v1/health" 2>/dev/null; then ok=1; echo; break; fi
  sleep 3
done
if [ "$ok" -ne 1 ]; then
  echo "Desde este equipo no se alcanzó el puerto; probando desde el servidor..." >&2
  if remoto "curl -fsS --max-time 5 http://localhost:$PUERTO/v1/health"; then
    echo
    echo "AVISO: la API responde en el servidor pero no desde aquí. Revisa Tailscale/LAN o usa --host con la IP." >&2
  else
    remoto "cd ~/$DIR_REMOTO && docker compose logs --tail 40 parcheu-api" >&2 || true
    falla "La API no respondió. Revisa los registros de arriba."
  fi
fi

if curl -fsS --max-time 8 "https://api-parcheu.homeserverlab.site/v1/health" >/dev/null 2>&1; then
  echo "HTTPS OK: https://api-parcheu.homeserverlab.site/v1/health"
else
  echo "AVISO: https://api-parcheu.homeserverlab.site aún no responde (falta el Proxy Host en NPM o el DNS); no es un error." >&2
fi

paso "Instalando el respaldo diario (cron, 03:17)"
remoto "DIR=$DIR_REMOTO bash -s" <<'REMOTO' || echo "AVISO: no se pudo instalar el cron; corre respaldar.sh a mano o instala crontab." >&2
set -euo pipefail
command -v crontab >/dev/null 2>&1 || { echo "crontab no está instalado en el servidor" >&2; exit 1; }
LINEA="17 3 * * * $HOME/$DIR/respaldar.sh >> $HOME/$DIR/backups/respaldo.log 2>&1"
{ crontab -l 2>/dev/null | grep -vF "$DIR/respaldar.sh" || true; echo "$LINEA"; } | crontab -
echo "Cron instalado."
REMOTO

paso "Listo"
echo "API:  http://$NOMBRE_HOST:$PUERTO/v1/health"
echo "Modo: $(remoto "grep '^PARCHEU_ENV=' ~/$DIR_REMOTO/.env")  (ver deploy/homelab/README.md para pasar a dev con la llave de Google)"
