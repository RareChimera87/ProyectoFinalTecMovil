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

# La configuración temporal se genera a partir de firebase/firebase.json (E0.8) para probar lo que
# realmente se despliega: solo se cambian "public" (enlace a la carpeta real, el emulador lo resuelve
# relativo a la configuración) y los emuladores (solo hosting, en PORT). Si E0.8 aún no está
# mezclada y el archivo no existe, se usa una base mínima y se avisa; las rutas que dependen de
# reglas ausentes se omiten de forma explícita (ver más abajo), no se dan por buenas.
REAL="$RAIZ/firebase/firebase.json"
ln -s "$PUBLIC" "$TMP/site"
if [[ -f "$REAL" ]]; then
  FUENTE="firebase/firebase.json"
else
  FUENTE="base mínima (falta firebase/firebase.json de E0.8)"
  echo '{"hosting":{"public":"public","ignore":["firebase.json","**/node_modules/**"],"cleanUrls":true}}' >"$TMP/base.json"
  REAL="$TMP/base.json"
fi
echo "Configuración de Hosting: $FUENTE"
node -e '
const fs = require("fs");
const [real, destino, puerto] = process.argv.slice(1);
const cfg = JSON.parse(fs.readFileSync(real, "utf8"));
if (Array.isArray(cfg.hosting)) throw new Error("hosting múltiple no soportado por la prueba");
cfg.hosting.public = "site";
delete cfg.firestore;
cfg.emulators = { hosting: { port: Number(puerto) }, ui: { enabled: false }, singleProjectMode: false };
fs.writeFileSync(destino, JSON.stringify(cfg, null, 2));
' "$REAL" "$TMP/firebase.json" "$PORT"
# Capacidades que la configuración real debe tener para esas rutas.
TIENE_REWRITE_I="$(node -e 'const h=JSON.parse(require("fs").readFileSync(process.argv[1])).hosting;process.stdout.write((h.rewrites||[]).some(r=>String(r.source).startsWith("/i/")&&r.destination==="/i/index.html")?"si":"no")' "$TMP/firebase.json")"
TIENE_WELLKNOWN="$(node -e 'const h=JSON.parse(require("fs").readFileSync(process.argv[1])).hosting;const ig=h.ignore||[];process.stdout.write(!ig.includes("**/.*")||ig.some(x=>String(x).startsWith("!")&&String(x).includes(".well-known"))?"si":"no")' "$TMP/firebase.json")"

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
# /i/{codigo} y assetlinks.json dependen de reglas de firebase.json que agregan E0.8/E6.1. Si la
# configuración real no las tiene, se omiten de forma explícita (no se prueban contra una config inventada).
omitir() { echo "OMITIDO $1: $2"; omitidos=$((omitidos + 1)); }
omitidos=0
if [[ "$TIENE_REWRITE_I" == "si" ]]; then verificar /i/ABCD-1234 'ParcheU' 'Código de invitación'
else omitir /i/ABC123 "firebase.json no tiene el rewrite /i/** -> /i/index.html (pendiente en E0.8/E6.1)"; fi
if [[ "$TIENE_WELLKNOWN" == "si" ]]; then
  # App Links de E6.1 (ADR-0018) y C-B9: el archivo del repo es el de dev (solo el paquete .dev);
  # el de prod se genera al desplegar (firebase/render-assetlinks.mjs, probado en firebase/test/assetlinks.test.js).
  verificar /.well-known/assetlinks.json 'delegate_permission/common.handle_all_urls' \
    '"me.santiagoreina.parcheu.dev"' 'sha256_cert_fingerprints'
  if grep -q '"me.santiagoreina.parcheu"' "$TMP/cuerpo"; then
    echo "FALLA assetlinks.json del repo (dev) no debe autorizar el paquete de prod"; fallos=$((fallos + 1))
  fi
  if node -e 'const a=JSON.parse(require("fs").readFileSync(process.argv[1]));const ok=f=>/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(f);if(!Array.isArray(a)||a.length===0||!a.every(s=>s.target&&(s.target.sha256_cert_fingerprints||[]).length>0&&s.target.sha256_cert_fingerprints.every(ok)))process.exit(1)' "$TMP/cuerpo"; then
    echo "OK    assetlinks.json es JSON válido con huellas SHA-256"
  else
    echo "FALLA assetlinks.json no es JSON válido o tiene huellas mal formadas"; fallos=$((fallos + 1))
  fi
else omitir /.well-known/assetlinks.json "firebase.json ignora **/.* y no exceptúa !**/.well-known/** (pendiente en E0.8/E6.1)"; fi

# La tabla de retención de arquitectura.md §10.2 debe estar en /privacidad: mismas filas, en el mismo
# orden, y con los mismos plazos numéricos (columnas "Plazo o evento" y "Al borrar la cuenta"). El
# texto se redacta distinto para el usuario, pero un "90 días" cambiado por "9 días" debe fallar.
ARQ="$RAIZ/docs/arquitectura.md"
if [[ -f "$ARQ" ]]; then
  if resultado="$(node "$RAIZ/scripts/test/legal-retention.js" "$ARQ" "$PUBLIC/privacidad.html")"; then
    echo "OK    tabla de retención ($resultado filas, mismos plazos que arquitectura §10.2)"
  else
    echo "FALLA retención: $resultado"; fallos=$((fallos + 1))
  fi
fi

# La versión del texto debe ser la misma en todas las páginas (LEGAL_TEXT_VERSION).
versiones="$(grep -ho 'name="legal-text-version" content="[^"]*"' "$PUBLIC"/*.html "$PUBLIC"/i/index.html | sort -u | wc -l)"
if [[ "$versiones" != "1" ]]; then echo "FALLA: versiones de texto distintas entre páginas"; fallos=$((fallos + 1)); else echo "OK    versión única del texto"; fi

if [[ "$fallos" -gt 0 ]]; then echo "$fallos verificación(es) fallaron"; sed -n 1,20p "$LOG"; exit 1; fi
echo "Páginas legales verificadas contra $FUENTE ($omitidos ruta(s) omitida(s) de forma explícita)."
