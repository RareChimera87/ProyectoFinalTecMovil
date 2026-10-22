#!/usr/bin/env bash
# Pruebas de scripts/commit-lint.sh (shell puro, sin dependencias).
# Uso: scripts/test/commit-lint.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LINT="$DIR/commit-lint.sh"
fallos=0

acepta() {
  if "$LINT" --titulo "$1" >/dev/null 2>&1; then echo "ok   acepta:  $1"
  else echo "FALLA debía aceptar: $1"; fallos=$((fallos + 1)); fi
}
rechaza() {
  if "$LINT" --titulo "$1" >/dev/null 2>&1; then echo "FALLA debía rechazar: $1"; fallos=$((fallos + 1))
  else echo "ok   rechaza: $1"; fi
}

acepta "Agrega esqueleto del backend"
acepta "Corrige cálculo de cuota semanal"
acepta "Documenta decisión de privacidad"
acepta "E0.1 Agrega monorepo y git flow"
acepta "Merge branch 'dev' into feature/x"
rechaza "Added backend"
rechaza "agregado backend"
rechaza "Agregando backend"
rechaza "agrega backend"
rechaza "Agrega backend."
rechaza "Agrega"
rechaza ""
rechaza "Agrega $(printf 'x%.0s' $(seq 1 70))"

# Modo archivo (hook commit-msg): solo cuenta la primera línea.
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
printf 'Agrega prueba\n\nCuerpo largo sin reglas.\n\nCo-Authored-By: X <x@y.z>\n' >"$tmp"
if "$LINT" "$tmp" >/dev/null 2>&1; then echo "ok   archivo válido"; else echo "FALLA archivo válido"; fallos=$((fallos + 1)); fi
printf 'Added thing\n' >"$tmp"
if "$LINT" "$tmp" >/dev/null 2>&1; then echo "FALLA archivo inválido aceptado"; fallos=$((fallos + 1)); else echo "ok   archivo inválido"; fi

if (( fallos > 0 )); then echo "$fallos prueba(s) fallaron"; exit 1; fi
echo "Todas las pruebas pasaron"
