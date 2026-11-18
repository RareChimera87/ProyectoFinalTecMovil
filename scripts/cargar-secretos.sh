#!/usr/bin/env bash
# Carga los VALORES de los secretos de ParcheU en Secret Manager. Terraform solo
# crea los contenedores (sin valor); los valores nunca pasan por Terraform ni
# por el repo. I1 lo ejecuta en dev; el usuario en prod.
#
# Uso:
#   scripts/cargar-secretos.sh <dev|prod> [--rotar]
#
#   hmac-anon-key  openssl rand -base64 32 (solo si aún no tiene versión, salvo --rotar).
#   play-sa        Opcional. En prod la API suplanta a sa-play sin llaves (PLAY_SERVICE_ACCOUNT, E7.1), así que
#                  normalmente queda sin valor. Solo si la suplantación fallara: JSON de la llave de sa-play
#                  tomado de PLAY_SA_JSON=<ruta>; si no se da, se omite.
#   anthropic-api-key  Opcional. Clave de la API directa de Anthropic (solo la usa AI_PROVIDER=anthropic, ADR-0008),
#                  leída del archivo ANTHROPIC_KEY_FILE=<ruta> (una línea, permisos 600). Se manda por stdin y
#                  nunca se imprime. Si no se da, se omite. Cada carga crea una versión nueva (rotación).
#
# Variables opcionales: PROJECT_ID (por defecto el de ~/.parcheu/proyectos.env o parcheu-<env>).
set -euo pipefail

ENVIRONMENT="${1:?Uso: $0 <dev|prod> [--rotar]}"
ROTATE="${2:-}"
case "$ENVIRONMENT" in dev|prod) ;; *) echo "Entorno inválido: $ENVIRONMENT" >&2; exit 2 ;; esac

if [ -z "${PROJECT_ID:-}" ]; then
  var="PROJECT_ID_$(echo "$ENVIRONMENT" | tr '[:lower:]' '[:upper:]')"
  file="${PARCHEU_HOME:-$HOME/.parcheu}/proyectos.env"
  PROJECT_ID=""
  # proyectos.env solo existe donde corrió crear-proyectos.sh; con `[ -f ] && ...` dentro
  # de $( ) y `set -e`, su ausencia hacía salir al script en silencio.
  if [ -f "$file" ]; then
    PROJECT_ID="$(sed -n "s/^$var=//p" "$file" | tail -1)"
  fi
  PROJECT_ID="${PROJECT_ID:-parcheu-$ENVIRONMENT}"
fi
echo "Proyecto: $PROJECT_ID"

has_version() {
  [ -n "$(gcloud secrets versions list "$1" --project="$PROJECT_ID" --filter='state=ENABLED' --limit=1 --format='value(name)' 2>/dev/null)" ]
}

require_container() {
  gcloud secrets describe "$1" --project="$PROJECT_ID" >/dev/null 2>&1 || {
    echo "El secreto $1 no existe en $PROJECT_ID: aplica primero infra/terraform/envs/$ENVIRONMENT" >&2
    exit 1
  }
}

# --- hmac-anon-key -----------------------------------------------------------
require_container hmac-anon-key
if has_version hmac-anon-key && [ "$ROTATE" != "--rotar" ]; then
  echo "hmac-anon-key ya tiene valor; no se cambia (usa --rotar para generar otro)."
else
  # El valor va por stdin: no queda en el historial ni en argumentos del proceso.
  openssl rand -base64 32 | tr -d '\n' |
    gcloud secrets versions add hmac-anon-key --project="$PROJECT_ID" --data-file=- >/dev/null
  echo "hmac-anon-key: nueva versión cargada."
  if [ "$ROTATE" = "--rotar" ]; then
    echo "Aviso: rotar la llave cambia todos los anonId; hazlo solo antes de tener usuarios." >&2
  fi
fi

# --- play-sa -----------------------------------------------------------------
require_container play-sa
if [ -n "${PLAY_SA_JSON:-}" ]; then
  [ -f "$PLAY_SA_JSON" ] || { echo "No existe $PLAY_SA_JSON" >&2; exit 1; }
  jq -e '.type == "service_account"' "$PLAY_SA_JSON" >/dev/null || { echo "$PLAY_SA_JSON no es una llave de cuenta de servicio" >&2; exit 1; }
  gcloud secrets versions add play-sa --project="$PROJECT_ID" --data-file="$PLAY_SA_JSON" >/dev/null
  echo "play-sa: nueva versión cargada desde $PLAY_SA_JSON."
elif has_version play-sa; then
  echo "play-sa ya tiene valor."
else
  echo "play-sa sin valor (normal: en prod la API suplanta a sa-play con PLAY_SERVICE_ACCOUNT; ver E7.1)."
fi

# --- anthropic-api-key -------------------------------------------------------
if [ -n "${ANTHROPIC_KEY_FILE:-}" ]; then
  require_container anthropic-api-key
  [ -f "$ANTHROPIC_KEY_FILE" ] || { echo "No existe $ANTHROPIC_KEY_FILE" >&2; exit 1; }
  perms="$(stat -c %a "$ANTHROPIC_KEY_FILE" 2>/dev/null || stat -f %Lp "$ANTHROPIC_KEY_FILE")"
  case "$perms" in
    600|400) ;;
    *) echo "$ANTHROPIC_KEY_FILE tiene permisos $perms: déjalo en 600 (chmod 600 \"$ANTHROPIC_KEY_FILE\")" >&2; exit 1 ;;
  esac
  # Validaciones sin imprimir el valor: una sola línea, no vacía y sin espacios.
  [ "$(grep -c '' "$ANTHROPIC_KEY_FILE")" -le 1 ] || { echo "$ANTHROPIC_KEY_FILE debe tener solo la clave, en una línea" >&2; exit 1; }
  if [ -z "$(tr -d '\r\n' <"$ANTHROPIC_KEY_FILE")" ] || tr -d '\r\n' <"$ANTHROPIC_KEY_FILE" | grep -q '[[:space:]]'; then
    echo "$ANTHROPIC_KEY_FILE está vacío o la clave tiene espacios" >&2
    exit 1
  fi
  tr -d '\r\n' <"$ANTHROPIC_KEY_FILE" | grep -q '^sk-ant-' ||
    echo "Aviso: la clave no empieza por sk-ant-; revisa que sea una clave de la API de Anthropic." >&2
  # Por stdin y sin salto de línea final: no queda en argumentos del proceso ni en la salida.
  tr -d '\r\n' <"$ANTHROPIC_KEY_FILE" |
    gcloud secrets versions add anthropic-api-key --project="$PROJECT_ID" --data-file=- >/dev/null
  echo "anthropic-api-key: nueva versión cargada desde $ANTHROPIC_KEY_FILE (la API la lee en su siguiente revisión)."
elif has_version anthropic-api-key; then
  echo "anthropic-api-key ya tiene valor."
else
  echo "anthropic-api-key sin valor (normal con AI_PROVIDER=gemini; para cargarla: ANTHROPIC_KEY_FILE=<ruta>)."
fi
