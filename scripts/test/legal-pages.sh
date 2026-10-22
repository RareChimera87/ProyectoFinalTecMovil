#!/usr/bin/env bash
# Prueba de E8.4: levanta el emulador de Firebase Hosting con firebase/public y verifica que
# cada página legal responde 200 y contiene sus secciones obligatorias.
# Uso: scripts/test/legal-pages.sh            (usa el firebase de PATH o npx firebase-tools)
#      PORT=5099 scripts/test/legal-pages.sh
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PUBLIC="$RAIZ/firebase/public"
PORT="${PORT:-5099}"
BASE="http://127.0.0.1:$PORT"
TMP="$(mktemp -d)"
LOG="$TMP/emulador.log"
PID=""

limpiar() {
  [[ -n "$PID" ]] && kill "$PID" 2>/dev/null || true
  # El emulador lanza procesos hijos (node): se liberan por la ruta de su configuración temporal.
  pkill -f "$TMP/firebase.json" 2>/dev/null || true
  rm -rf "$TMP"
}
trap limpiar EXIT

if command -v firebase >/dev/null 2>&1; then
  FIREBASE=(firebase)
else
  FIREBASE=(npx --yes firebase-tools@15)
fi

# Configuración temporal equivalente a firebase/firebase.json (E0.8) más lo que necesitan
# /i/{codigo} (rewrite) y /.well-known (no ignorar), que completa E6.1. No toca firebase.json.
# El emulador resuelve "public" relativo a la configuración: se enlaza la carpeta real.
ln -s "$PUBLIC" "$TMP/site"
cat > "$TMP/firebase.json" <<JSON
{
  "hosting": {
    "public": "site",
    "ignore": ["firebase.json", "**/node_modules/**"],
    "cleanUrls": true,
    "rewrites": [{ "source": "/i/**", "destination": "/i/index.html" }]
  },
  "emulators": { "hosting": { "port": $PORT }, "ui": { "enabled": false }, "singleProjectMode": false }
}
JSON

(cd "$TMP" && "${FIREBASE[@]}" emulators:start --only hosting --project demo-parcheu-legal --config "$TMP/firebase.json" >"$LOG" 2>&1) &
PID=$!

for _ in $(seq 1 60); do
  if [[ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/privacidad" || true)" == "200" ]]; then break; fi
  sleep 1
done

fallos=0
verificar() { # ruta, texto obligatorio...
  local ruta="$1"; shift
  local cuerpo codigo
  codigo="$(curl -s -o "$TMP/cuerpo" -w '%{http_code}' "$BASE$ruta")" || codigo=000
  if [[ "$codigo" != "200" ]]; then echo "FALLA $ruta -> HTTP $codigo"; fallos=$((fallos + 1)); return; fi
  cuerpo="$(cat "$TMP/cuerpo")"
  for texto in "$@"; do
    if ! grep -qF -- "$texto" <<<"$cuerpo"; then
      echo "FALLA $ruta: falta '$texto'"; fallos=$((fallos + 1))
    fi
  done
  echo "OK    $ruta"
}

verificar /privacidad 'Responsable del tratamiento' 'Datos que recolectamos' 'Finalidades' \
  'Vertex AI' 'Transferencia internacional' 'Retención de datos' 'Ley 1581' \
  'Consulta' 'Actualización' 'Supresión' 'Revocación' 'mailto:'
verificar /terminos 'mayores de 18 años' 'Conducta' 'generado por inteligencia artificial' 'Suscripción'
verificar /borrar-cuenta 'desde la app' 'Opción 2' 'correo' 'Qué se borra'
verificar /i/ABC123 'ParcheU'
verificar /.well-known/assetlinks.json '[]'

# Todas las filas de la tabla de retención de arquitectura.md §10.2 deben estar en /privacidad.
ARQ="$RAIZ/docs/arquitectura.md"
if [[ -f "$ARQ" ]]; then
  filas="$(awk '/^### 10\.2 /{f=1;next} /^### 10\.3 /{f=0} f && /^\| /' "$ARQ" | grep -vc -e '^| Dato ' -e '^|---' || true)"
  celdas="$(grep -c '^<tr><td>' "$PUBLIC/privacidad.html" || true)"
  if [[ "$filas" != "$celdas" ]]; then
    echo "FALLA retención: arquitectura §10.2 tiene $filas filas y /privacidad $celdas"; fallos=$((fallos + 1))
  else
    echo "OK    tabla de retención ($filas filas)"
  fi
fi

# La versión del texto debe ser la misma en todas las páginas (LEGAL_TEXT_VERSION).
versiones="$(grep -ho 'name="legal-text-version" content="[^"]*"' "$PUBLIC"/*.html "$PUBLIC"/i/index.html | sort -u | wc -l)"
if [[ "$versiones" != "1" ]]; then echo "FALLA: versiones de texto distintas entre páginas"; fallos=$((fallos + 1)); else echo "OK    versión única del texto"; fi

if [[ "$fallos" -gt 0 ]]; then echo "$fallos verificación(es) fallaron"; sed -n 1,20p "$LOG"; exit 1; fi
echo "Todas las páginas legales responden 200 con sus secciones."
