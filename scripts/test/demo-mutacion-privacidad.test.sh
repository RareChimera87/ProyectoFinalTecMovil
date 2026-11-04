#!/usr/bin/env bash
# Pruebas rápidas (sin Gradle) de scripts/demo-mutacion-privacidad.sh: las anclas de las mutaciones
# existen exactamente una vez en el código y un modo desconocido sale con 2 sin tocar el árbol.
# La corrida completa (≠ 0 con mutación, 0 sin ella) está documentada en docs/desarrollo-local.md.
# Uso: scripts/test/demo-mutacion-privacidad.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="$DIR/demo-mutacion-privacidad.sh"
fallos=0

"$DEMO" --verificar-anclas >/dev/null
if [[ $? -eq 0 ]]; then echo "ok   anclas de mutación presentes"; else echo "FALLA anclas de mutación"; fallos=$((fallos + 1)); fi

antes="$(git -C "$DIR/.." status --porcelain)"
"$DEMO" desconocida >/dev/null 2>&1
rc=$?
despues="$(git -C "$DIR/.." status --porcelain)"
if [[ $rc -eq 2 && "$antes" == "$despues" ]]; then echo "ok   modo desconocido -> 2 sin cambios"; else echo "FALLA modo desconocido (rc=$rc)"; fallos=$((fallos + 1)); fi

[[ $fallos -eq 0 ]] && echo "Todas las pruebas pasaron" || echo "$fallos prueba(s) fallaron"
exit "$fallos"
