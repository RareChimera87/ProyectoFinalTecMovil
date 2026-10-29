#!/usr/bin/env bash
# Pruebas de deploy/homelab/reversion.sh con un repositorio temporal (sin SSH ni Docker).
# Uso: scripts/test/desplegar-reversion.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
# shellcheck source=../../deploy/homelab/reversion.sh
source "$DIR/deploy/homelab/reversion.sh"

git -C "$tmp" init -q -b dev
git -C "$tmp" config user.email t@example.com
git -C "$tmp" config user.name t
for i in 1 2 3 4; do git -C "$tmp" commit -q --allow-empty -m "c$i"; done
sha() { git -C "$tmp" rev-parse --short=12 "HEAD~$1"; }
VIEJO="$(sha 3)"; ANTES="$(sha 2)"; EL_103="$(sha 1)"; DESPUES="$(sha 0)"
COMMIT_103="$(git -C "$tmp" rev-parse "HEAD~1")"

fallos=0
caso() { # caso <descripción> <etiqueta> <patrón o "-" si no debe haber aviso>
  local salida; salida="$(aviso_reversion_anterior_a_103 "$tmp" "$2" 2>&1)"
  if [ "$3" = "-" ]; then
    [ -z "$salida" ] && echo "ok   $1" || { echo "FALLA $1: $salida"; fallos=$((fallos + 1)); }
  else
    grep -qF -- "$3" <<<"$salida" && echo "ok   $1" || { echo "FALLA $1: '$salida'"; fallos=$((fallos + 1)); }
  fi
}
caso "sin imagen anterior: sin aviso" "" "-"
caso "imagen anterior a #103: avisa" "$VIEJO" "es anterior a #103"
caso "otra imagen anterior a #103: avisa" "$ANTES" "es anterior a #103"
caso "la imagen de #103: sin aviso" "$EL_103" "-"
caso "imagen posterior a #103: sin aviso" "$DESPUES" "-"
caso "etiqueta dirty: avisa que no se sabe" "abc123def456-dirty" "no se puede saber"
caso "etiqueta homelab: avisa que no se sabe" "homelab" "no se puede saber"
caso "SHA fuera del historial: avisa que no se sabe" "0123456789ab" "no está en el historial"
salida="$(aviso_reversion_anterior_a_103 "$tmp" "$VIEJO" 2>/dev/null)"; rc=$?
[ "$rc" = 0 ] && [ -z "$salida" ] && echo "ok   solo escribe en stderr y sale con 0" || { echo "FALLA salida/rc"; fallos=$((fallos + 1)); }

echo "$fallos fallo(s)"
((fallos == 0)) && echo "desplegar-reversion.test: todo en orden" || exit 1
