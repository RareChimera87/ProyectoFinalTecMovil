#!/usr/bin/env bash
# Carga los VALORES de los secretos de ParcheU en Secret Manager. Terraform solo
# crea los contenedores (sin valor); los valores nunca pasan por Terraform ni
# por el repo. I1 lo ejecuta en dev; el usuario en prod.
#
# Uso:
#   scripts/cargar-secretos.sh <dev|prod> [--rotar]
#
#   hmac-anon-key  openssl rand -base64 32 (solo si aún no tiene versión, salvo --rotar).
#   play-sa        JSON de la cuenta de servicio de Play, si Play no admite WIF (verificar
#                  en E7.1). Se toma de PLAY_SA_JSON=<ruta>; si no se da, se omite.
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
  echo "play-sa sin valor (opcional: solo si Play no admite la identidad de sa-api; ver E7.1)."
fi
