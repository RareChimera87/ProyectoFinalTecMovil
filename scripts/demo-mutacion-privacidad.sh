#!/usr/bin/env bash
# Demo de mutación de la suite de invariantes de privacidad (E3.5, ADR-0012 y ADR-0032).
#
# Aplica a propósito un defecto de privacidad en el código del backend, corre la suite
# (backend/src/test/.../privacy) y restaura el archivo, pase lo que pase. El código de salida es el
# de la suite: ≠ 0 con una mutación (la suite la detectó) y 0 sin mutación. El árbol queda limpio.
#
# Uso: scripts/demo-mutacion-privacidad.sh [pareja|memorias|ninguna|--verificar-anclas]
#   pareja    (por defecto) GraphVisibilityPolicy deja de filtrar las aristas de Pareja ajenas.
#   memorias  TwinPromptBuilder deja de filtrar las memorias por modo.
#   ninguna   corre la suite sin tocar nada (debe terminar en 0).
#   --verificar-anclas  solo comprueba que el texto a mutar existe una vez (sin Gradle).
# Variables: PRIVACY_SEED=<n> reproduce una corrida; GRADLE_ARGS agrega opciones a Gradle.
# Requiere que el archivo a mutar no tenga cambios sin commit (se restaura con git checkout).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="backend/src/main/kotlin/me/santiagoreina/parcheu/api"
POLICY="$PKG/graph/GraphVisibilityPolicy.kt"
BUILDER="$PKG/simulation/TwinPromptBuilder.kt"

# mutacion -> archivo, texto original, texto mutado
declare -A ARCHIVO=([pareja]="$POLICY" [memorias]="$BUILDER")
declare -A ORIGINAL=(
  [pareja]='mode != Mode.PAREJA || viewer == endpointA || viewer == endpointB'
  [memorias]='.filter { it.mode == mode }'
)
declare -A MUTADO=(
  [pareja]='true /* MUTACIÓN: Pareja visible para terceros */'
  [memorias]='.filter { true /* MUTACIÓN: memorias de cualquier modo */ }'
)

# reemplazar <archivo> <original> <nuevo>: exige exactamente una aparición.
reemplazar() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys
path, old, new = sys.argv[1:4]
text = open(path, encoding="utf-8").read()
count = text.count(old)
if count != 1:
    sys.exit(f"Se esperaba 1 aparición de '{old}' en {path} y hay {count}")
open(path, "w", encoding="utf-8").write(text.replace(old, new))
PY
}

contar() { python3 -c 'import sys; print(open(sys.argv[1], encoding="utf-8").read().count(sys.argv[2]))' "$1" "$2"; }

modo="${1:-pareja}"

if [[ "$modo" == "--verificar-anclas" ]]; then
  fallos=0
  for m in "${!ARCHIVO[@]}"; do
    n="$(contar "$ROOT/${ARCHIVO[$m]}" "${ORIGINAL[$m]}")"
    if [[ "$n" == 1 ]]; then echo "ok   ancla '$m' en ${ARCHIVO[$m]}"; else echo "FALLA ancla '$m': $n apariciones en ${ARCHIVO[$m]}"; fallos=1; fi
  done
  exit "$fallos"
fi

case "$modo" in
  pareja|memorias|ninguna) ;;
  *) echo "Uso: $0 [pareja|memorias|ninguna|--verificar-anclas]" >&2; exit 2 ;;
esac

cd "$ROOT" || exit 2
archivo="${ARCHIVO[$modo]:-}"

restaurar() {
  [[ -n "$archivo" ]] || return 0
  git checkout --quiet -- "$archivo"
  if [[ -n "$(git status --porcelain -- "$archivo")" ]]; then
    echo "ERROR: $archivo no quedó limpio" >&2
  else
    echo "== Restaurado $archivo (árbol limpio)"
  fi
}

if [[ -n "$archivo" ]]; then
  if [[ -n "$(git status --porcelain -- "$archivo")" ]]; then
    echo "ERROR: $archivo tiene cambios sin commit; la demo lo restaura con git checkout y los perdería." >&2
    exit 2
  fi
  trap restaurar EXIT INT TERM
  reemplazar "$archivo" "${ORIGINAL[$modo]}" "${MUTADO[$modo]}" || exit 2
  echo "== Mutación '$modo' aplicada en $archivo:"
  git --no-pager diff --no-color -U0 -- "$archivo" | sed -n '/^[-+][^-+]/p'
else
  echo "== Sin mutación"
fi

echo "== Corriendo la suite de invariantes de privacidad..."
resultados="$ROOT/backend/build/test-results/test"
rm -f "$resultados"/TEST-me.santiagoreina.parcheu.api.privacy.*.xml
log="$(mktemp)"
# shellcheck disable=SC2086
(cd backend && ./gradlew test -PsinContenedores --tests 'me.santiagoreina.parcheu.api.privacy.*' --console=plain ${GRADLE_ARGS:-}) >"$log" 2>&1
rc=$?
grep -E "FAILED|tests completed|BUILD|^    [^ -]" "$log" | head -15
rm -f "$log"

echo "== Semillas (repite con PRIVACY_SEED=<semilla>):"
grep -ho '\[privacidad\][^]]*semilla=[0-9-]*' "$resultados"/TEST-me.santiagoreina.parcheu.api.privacy.*.xml 2>/dev/null | sort -u || true

if [[ -n "$archivo" ]]; then
  if [[ $rc -ne 0 ]]; then
    echo "== Resultado: la suite DETECTÓ la mutación '$modo' (código $rc)."
  else
    echo "== Resultado: ALERTA, la suite NO detectó la mutación '$modo'."
  fi
else
  [[ $rc -eq 0 ]] && echo "== Resultado: suite en verde sin mutación." || echo "== Resultado: la suite falla sin mutación (código $rc)."
fi
exit "$rc"
