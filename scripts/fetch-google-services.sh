#!/usr/bin/env bash
# Descarga el google-services.json de un flavor de la app Android desde Firebase (E0.7).
#
# Uso: scripts/fetch-google-services.sh <dev|prod>
#
# - dev  -> proyecto parcheu-dev,  paquete me.santiagoreina.parcheu.dev -> android/app/src/dev/google-services.json
# - prod -> proyecto parcheu-prod, paquete me.santiagoreina.parcheu     -> android/app/src/prod/google-services.json
#
# Usa firebase-tools con las credenciales del desarrollador (firebase login o el ADC de
# `gcloud auth application-default login`). El archivo NUNCA se versiona (.gitignore: **/google-services.json).
# En CI no se usa este script: el archivo sale del secreto GOOGLE_SERVICES_JSON_{DEV,PROD} (base64).
#
# Variables opcionales: PARCHEU_PROJECT_DEV / PARCHEU_PROJECT_PROD para otro ID de proyecto.
set -euo pipefail

usage() {
  echo "Uso: $0 <dev|prod>" >&2
  exit 2
}

[[ $# -eq 1 ]] || usage
flavor="$1"
case "$flavor" in
  dev)
    project="${PARCHEU_PROJECT_DEV:-parcheu-dev}"
    package="me.santiagoreina.parcheu.dev"
    ;;
  prod)
    project="${PARCHEU_PROJECT_PROD:-parcheu-prod}"
    package="me.santiagoreina.parcheu"
    ;;
  *) usage ;;
esac

if ! command -v firebase >/dev/null 2>&1; then
  echo "Falta firebase-tools (npm i -g firebase-tools)." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
  echo "Hace falta jq o python3 para leer la respuesta de firebase-tools." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest_dir="$repo_root/android/app/src/$flavor"
dest="$dest_dir/google-services.json"

# Busca el App ID de la app Android con ese paquete.
apps_json="$(firebase apps:list ANDROID --project "$project" --json)"
if command -v jq >/dev/null 2>&1; then
  app_id="$(jq -r --arg p "$package" '.result[] | select(.packageName == $p) | .appId' <<<"$apps_json" | head -n1)"
else
  app_id="$(python3 -c '
import json, sys
pkg = sys.argv[1]
apps = json.load(sys.stdin).get("result", [])
print(next((a["appId"] for a in apps if a.get("packageName") == pkg), ""))
' "$package" <<<"$apps_json")"
fi

if [[ -z "$app_id" ]]; then
  echo "No hay una app Android con el paquete $package en el proyecto $project." >&2
  echo "Regístrala (E0.8) con: firebase apps:create ANDROID 'ParcheU $flavor' --package-name $package --project $project" >&2
  exit 1
fi

mkdir -p "$dest_dir"
# firebase-tools no sobrescribe archivos: se descarga a un directorio temporal vacío.
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
tmp="$tmp_dir/google-services.json"
firebase apps:sdkconfig ANDROID "$app_id" --project "$project" --out "$tmp"
# Validación mínima: debe ser JSON con el paquete esperado.
if ! grep -q "\"package_name\": *\"$package\"" "$tmp"; then
  echo "La configuración descargada no contiene el paquete $package; no se escribe nada." >&2
  exit 1
fi
mv -f "$tmp" "$dest"

echo "Escrito $dest (proyecto $project, app $app_id)."
if git -C "$repo_root" ls-files --error-unmatch "$dest" >/dev/null 2>&1; then
  echo "ATENCIÓN: $dest está versionado; quítalo con git rm --cached." >&2
  exit 1
fi
