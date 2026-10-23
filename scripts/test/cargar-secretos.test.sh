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

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
