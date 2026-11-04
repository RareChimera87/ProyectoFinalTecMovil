#!/usr/bin/env bash
# Otorga (o quita) el claim `admin` de Firebase Auth a una o más cuentas (E8.5).
#
# Uso: scripts/grant-admin.sh [--revoke] [--dry-run] <dev|prod> <correo> [<correo> ...]
#
# - Busca cada cuenta por correo en el proyecto (parcheu-dev / parcheu-prod) y reescribe sus
#   custom claims conservando los demás: solo agrega o quita `admin`.
# - Usa la API REST de Identity Toolkit con el token de `gcloud auth print-access-token`: hay que
#   haber iniciado sesión con una cuenta con rol de administrador de Firebase Auth en ese proyecto.
# - Cada cuenta debe haber iniciado sesión al menos una vez (si no, no existe en Auth).
# - El claim viaja en el ID token: la persona debe cerrar sesión y volver a entrar (o esperar hasta
#   una hora) para que el servidor lo vea. La página admin.html lo refresca al entrar.
# - --revoke además revoca las sesiones de la cuenta (`validSince` = ahora, lo mismo que
#   `revokeRefreshTokens`): el backend comprueba la revocación en los tokens admin y los corta de inmediato.
# - --dry-run no escribe nada: solo muestra qué cuentas encontraría y qué claims dejaría.
# - Variables opcionales: PARCHEU_PROJECT_DEV / PARCHEU_PROJECT_PROD para otro ID de proyecto.
set -euo pipefail

usage() {
  echo "Uso: $0 [--revoke] [--dry-run] <dev|prod> <correo> [<correo> ...]" >&2
  exit 2
}

revoke=false
dry=false
while [[ $# -gt 0 && "$1" == --* ]]; do
  case "$1" in
    --revoke) revoke=true ;;
    --dry-run) dry=true ;;
    *) usage ;;
  esac
  shift
done
[[ $# -ge 2 ]] || usage

env_name="$1"
shift
case "$env_name" in
  dev) project="${PARCHEU_PROJECT_DEV:-parcheu-dev}" ;;
  prod) project="${PARCHEU_PROJECT_PROD:-parcheu-prod}" ;;
  *) usage ;;
esac

for tool in curl jq gcloud; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Falta $tool." >&2; exit 1; }
done

# El token va en un archivo de cabeceras (`-H @archivo`), no como argumento: los argumentos de curl se ven en `ps`.
hdrs="$(mktemp)"
trap 'rm -f "$hdrs"' EXIT
chmod 600 "$hdrs"
# Asignación aparte: dentro del argumento de printf el fallo de gcloud no cortaría el script (set -e).
access_token="$(gcloud auth print-access-token)"
[[ -n "$access_token" ]] || { echo "gcloud no devolvió un access token." >&2; exit 1; }
printf 'Authorization: Bearer %s\n' "$access_token" >"$hdrs"
unset access_token
base="https://identitytoolkit.googleapis.com/v1/projects/$project"

# Llama a Identity Toolkit con el proyecto de cuota correcto y falla con el cuerpo del error.
api() {
  local path="$1" body="$2" out status
  out="$(mktemp)"
  status="$(curl -sS -o "$out" -w '%{http_code}' -X POST "$base/$path" \
    -H "@$hdrs" -H "x-goog-user-project: $project" \
    -H 'Content-Type: application/json' -d "$body")"
  if [[ "$status" != 2* ]]; then
    echo "Identity Toolkit respondió $status en $path:" >&2
    cat "$out" >&2
    rm -f "$out"
    exit 1
  fi
  cat "$out"
  rm -f "$out"
}

status=0
for email in "$@"; do
  found="$(api 'accounts:lookup' "$(jq -n --arg e "$email" '{email: [$e]}')")"
  uid="$(jq -r '.users[0].localId // empty' <<<"$found")"
  if [[ -z "$uid" ]]; then
    echo "SIN CUENTA  $email (debe iniciar sesión en la app al menos una vez)" >&2
    status=1
    continue
  fi
  # customAttributes llega como un JSON en texto; se conserva y solo se toca `admin`.
  current="$(jq -r '.users[0].customAttributes // "{}"' <<<"$found")"
  if $revoke; then
    claims="$(jq -c 'del(.admin)' <<<"$current")"
    verb="QUITA admin (y revoca sesiones)"
  else
    claims="$(jq -c '.admin = true' <<<"$current")"
    verb="OTORGA admin"
  fi
  if $dry; then
    echo "[dry-run] $verb a $email ($uid) en $project -> claims $claims"
    continue
  fi
  # Al quitar admin se revocan también las sesiones en la misma llamada: sin `validSince` el ID token que ya
  # circula conservaría el claim hasta una hora más.
  if $revoke; then
    update="$(jq -nc --arg u "$uid" --arg c "$claims" --arg v "$(date +%s)" '{localId: $u, customAttributes: $c, validSince: $v}')"
  else
    update="$(jq -nc --arg u "$uid" --arg c "$claims" '{localId: $u, customAttributes: $c}')"
  fi
  api 'accounts:update' "$update" >/dev/null
  echo "$verb a $email ($uid) en $project -> claims $claims"
done
exit "$status"
