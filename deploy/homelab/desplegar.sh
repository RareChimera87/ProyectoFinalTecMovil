#!/usr/bin/env bash
# Despliega el backend de ParcheU en el homeserver. Se corre DESDE el portátil:
#   deploy/homelab/desplegar.sh [--host usuario@homelab] [--sin-build]
# Construye la imagen con Jib, la copia por scp con el compose, y en el homeserver hace docker load,
# levanta la API (que migra antes) y verifica la salud; si falla, revierte a la imagen anterior. No toca otros contenedores.
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
    echo "TASKS_MODE=inline"
    echo "INTERNAL_OIDC_AUDIENCE=https://api-parcheu.homeserverlab.site"
  } > .env
  echo "Creado .env con contraseñas nuevas."
else
  # Un .env de un despliegue anterior puede no traer claves nuevas; se agregan sin tocar las existentes.
  for clave in POSTGRES_PASSWORD HMAC_ANON_KEY; do
    grep -q "^$clave=" .env || echo "$clave=$(aleatorio)" >> .env
  done
  grep -q "^TASKS_MODE=" .env || echo "TASKS_MODE=inline" >> .env
  # La versión sale de la imagen (SHA); un APP_VERSION fijo en el .env la taparía.
  sed -i '/^APP_VERSION=/d' .env
  echo "Se conserva el .env existente."
fi
chmod 600 .env
REMOTO

paso "Cargando la imagen, etiquetándola con su SHA y levantando los servicios"
# Imprime la etiqueta nueva y la anterior (para revertir) en la última línea: "NUEVA ANTERIOR".
ETIQUETAS="$(remoto "DIR=$DIR_REMOTO bash -s" <<'REMOTO'
set -euo pipefail
umask 077
cd ~/"$DIR"
docker load -i jib-image.tar >&2
rm -f jib-image.tar
NUEVA="$(docker image inspect parcheu-api:latest --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^APP_VERSION=//p' | head -1)"
case "$NUEVA" in
  ''|dev) NUEVA="$(docker image inspect parcheu-api:latest --format '{{.Id}}' | sed 's/^sha256://' | cut -c1-12)" ;;
esac
docker tag parcheu-api:latest "parcheu-api:$NUEVA"
ACTUAL="$(sed -n 's/^PARCHEU_TAG=//p' .env | tail -1)"
if [ "$ACTUAL" = "$NUEVA" ]; then ANTERIOR="$(sed -n 's/^PARCHEU_PREV_TAG=//p' .env | tail -1)"; else ANTERIOR="$ACTUAL"; fi
fijar() { grep -v "^$1=" .env > .env.nuevo || true; echo "$1=$2" >> .env.nuevo; cat .env.nuevo > .env; rm -f .env.nuevo; }
fijar PARCHEU_PREV_TAG "$ANTERIOR"
fijar PARCHEU_TAG "$NUEVA"
# Solo `up`: la API depende de la base sana y de que parcheu-migrate termine bien, así que la migración corre sola.
docker compose up -d --remove-orphans parcheu-api >&2
echo "$NUEVA $ANTERIOR"
REMOTO
)" || falla "No se pudo cargar o levantar la imagen. Revisa los mensajes de arriba."
NUEVA="${ETIQUETAS##*$'\n'}"; NUEVA="${NUEVA%% *}"
ANTERIOR="$(printf '%s' "$ETIQUETAS" | tail -1 | cut -s -d' ' -f2)"

paso "Comprobando salud en el servidor (http://127.0.0.1:$PUERTO/v1/health)"
salud_remota() {
  for _ in $(seq 1 30); do
    if remoto "curl -fsS --max-time 5 http://127.0.0.1:$PUERTO/v1/health" 2>/dev/null; then echo; return 0; fi
    sleep 3
  done
  return 1
}
if ! salud_remota; then
  remoto "cd ~/$DIR_REMOTO && docker compose logs --tail 40 parcheu-api" >&2 || true
  if [ -n "$ANTERIOR" ]; then
    echo "La versión $NUEVA no respondió: se revierte a $ANTERIOR (las migraciones de Flyway no se deshacen)." >&2
    remoto "cd ~/$DIR_REMOTO && sed -i 's/^PARCHEU_TAG=.*/PARCHEU_TAG=$ANTERIOR/' .env && docker compose up -d parcheu-api" >&2 || true
    if salud_remota; then falla "Despliegue revertido a $ANTERIOR: $NUEVA no pasó la comprobación de salud."; fi
    falla "$NUEVA falló y la reversión a $ANTERIOR tampoco respondió. Revisa los registros del servidor."
  fi
  falla "La API no respondió y no hay versión anterior a la que revertir. Revisa los registros de arriba."
fi

# Despliegue sano: se borran las etiquetas anteriores a la previa (nunca `docker image prune`: no toca otras imágenes).
remoto "NUEVA=$NUEVA ANTERIOR=$ANTERIOR bash -s" <<'REMOTO' || true
for tag in $(docker images parcheu-api --format '{{.Tag}}'); do
  case "$tag" in latest|"$NUEVA"|"$ANTERIOR"|'<none>') ;; *) docker rmi "parcheu-api:$tag" >/dev/null 2>&1 || true ;; esac
done
REMOTO

paso "Comprobando alcance desde este equipo (http://$NOMBRE_HOST:$PUERTO/v1/health)"
if curl -fsS --max-time 8 "http://$NOMBRE_HOST:$PUERTO/v1/health" >/dev/null 2>&1; then
  echo "OK desde este equipo."
else
  echo "AVISO: la API sana en el servidor, pero no responde desde aquí: el 8090 solo escucha en 127.0.0.1 y 192.168.0.10 (no en Tailscale). Usa la LAN o --host con esa IP." >&2
fi

MODO="$(remoto "sed -n 's/^PARCHEU_ENV=//p' ~/$DIR_REMOTO/.env | tail -1")"
if curl -fsS --max-time 8 "https://api-parcheu.homeserverlab.site/v1/health" >/dev/null 2>&1; then
  echo "HTTPS OK: https://api-parcheu.homeserverlab.site/v1/health"
  if [ "$MODO" = "local" ]; then
    echo "ALERTA: PARCHEU_ENV=local (autenticación Fake: cualquiera suplanta a cualquier usuario) y el HTTPS ya responde. Restringe el Proxy Host de NPM a 192.168.0.0/24 y 100.64.0.0/10 con una Access List, o pasa a dev (README)." >&2
  fi
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
echo "API:  http://$NOMBRE_HOST:$PUERTO/v1/health (LAN)"
echo "Modo: PARCHEU_ENV=$MODO  versión: $NUEVA  (ver deploy/homelab/README.md para pasar a dev con la llave de Google)"
