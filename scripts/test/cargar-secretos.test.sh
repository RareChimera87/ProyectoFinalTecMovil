#!/usr/bin/env bash
# Pruebas de scripts/cargar-secretos.sh con un gcloud falso (nunca toca GCP).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/home"
printf '#!/usr/bin/env bash\nexit 1\n' >"$TMP/bin/gcloud"
chmod +x "$TMP/bin/gcloud"

pass=0; failed=0
check() { # check <descripción> <condición>
  if eval "$2"; then pass=$((pass + 1)); echo "  ✓ $1"; else failed=$((failed + 1)); echo "  ✗ $1"; echo "$out" | sed 's/^/      /'; fi
}
run() { out="$(HOME="$TMP/home" PARCHEU_HOME="" PATH="$TMP/bin:$PATH" "$ROOT/scripts/cargar-secretos.sh" "$@" 2>&1)"; rc=$?; }

echo "cargar-secretos.sh"
# Caso del hallazgo: HOME vacío, sin ~/.parcheu/proyectos.env.
run dev
check "sin proyectos.env imprime 'Proyecto: parcheu-dev'" 'grep -q "^Proyecto: parcheu-dev$" <<<"$out"'
check "sin proyectos.env avisa que el secreto no existe (no sale en silencio)" 'grep -q "hmac-anon-key no existe" <<<"$out" && [ "$rc" -eq 1 ]'
run prod
check "prod sin proyectos.env usa parcheu-prod" 'grep -q "^Proyecto: parcheu-prod$" <<<"$out"'

mkdir -p "$TMP/home/.parcheu"
echo "PROJECT_ID_DEV=parcheu-dev-xyz" >"$TMP/home/.parcheu/proyectos.env"
run dev
check "con proyectos.env usa el ID registrado" 'grep -q "^Proyecto: parcheu-dev-xyz$" <<<"$out"'
run prod
check "proyectos.env sin la variable de prod cae al ID por defecto" 'grep -q "^Proyecto: parcheu-prod$" <<<"$out"'
run test
check "entorno inválido sale con 2" '[ "$rc" -eq 2 ]'

# --- anthropic-api-key con un gcloud falso que acepta todo y guarda lo que recibe por stdin ---
mkdir -p "$TMP/bin2" "$TMP/cap"
cat >"$TMP/bin2/gcloud" <<EOF
#!/usr/bin/env bash
echo "\$*" >>"$TMP/gcloud.log"
if [ "\$1 \$2 \$3" = "secrets versions add" ]; then cat >"$TMP/cap/\$4"; fi
exit 0
EOF
chmod +x "$TMP/bin2/gcloud"
run2() { rm -f "$TMP/cap/"* "$TMP/gcloud.log"; out="$(HOME="$TMP/home" PARCHEU_HOME="" PATH="$TMP/bin2:$PATH" "$@" 2>&1)"; rc=$?; }
KEY="sk-ant-api03-PRUEBA-no-real"
printf '%s\n' "$KEY" >"$TMP/clave"
chmod 600 "$TMP/clave"

run2 env ANTHROPIC_KEY_FILE="$TMP/clave" "$ROOT/scripts/cargar-secretos.sh" prod
check "ANTHROPIC_KEY_FILE carga una versión de anthropic-api-key" '[ "$rc" -eq 0 ] && grep -q "anthropic-api-key: nueva versión" <<<"$out"'
check "la versión lleva la clave exacta, sin salto de línea final" '[ "$(cat "$TMP/cap/anthropic-api-key")" = "$KEY" ] && [ "$(wc -c <"$TMP/cap/anthropic-api-key")" -eq ${#KEY} ]'
check "la clave no aparece en la salida ni en los argumentos de gcloud" '! grep -q "$KEY" <<<"$out" && ! grep -q "$KEY" "$TMP/gcloud.log"'
check "la clave va por stdin (--data-file=-) al proyecto de prod" 'grep -q "^secrets versions add anthropic-api-key --project=parcheu-prod --data-file=-$" "$TMP/gcloud.log"'

run2 "$ROOT/scripts/cargar-secretos.sh" prod
check "sin ANTHROPIC_KEY_FILE no se carga nada en anthropic-api-key" '[ "$rc" -eq 0 ] && [ ! -e "$TMP/cap/anthropic-api-key" ] && grep -q "anthropic-api-key sin valor" <<<"$out"'

chmod 644 "$TMP/clave"
run2 env ANTHROPIC_KEY_FILE="$TMP/clave" "$ROOT/scripts/cargar-secretos.sh" prod
check "un archivo con permisos 644 se rechaza sin cargar" '[ "$rc" -eq 1 ] && [ ! -e "$TMP/cap/anthropic-api-key" ] && grep -q "chmod 600" <<<"$out"'

run2 env ANTHROPIC_KEY_FILE="$TMP/no-existe" "$ROOT/scripts/cargar-secretos.sh" prod
check "un archivo inexistente falla" '[ "$rc" -eq 1 ] && grep -q "No existe" <<<"$out"'

printf '%s\notra-linea\n' "$KEY" >"$TMP/clave"
chmod 600 "$TMP/clave"
run2 env ANTHROPIC_KEY_FILE="$TMP/clave" "$ROOT/scripts/cargar-secretos.sh" prod
check "un archivo con más de una línea se rechaza sin imprimir la clave" '[ "$rc" -eq 1 ] && [ ! -e "$TMP/cap/anthropic-api-key" ] && ! grep -q "$KEY" <<<"$out"'

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
