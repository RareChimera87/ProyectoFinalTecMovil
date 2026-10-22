#!/usr/bin/env bash
# Valida un mensaje de commit o título de PR (ADR-0001, PLAN.md §5).
#
# Uso:
#   scripts/commit-lint.sh <archivo-de-mensaje>     (hook commit-msg: usa la primera línea)
#   scripts/commit-lint.sh --titulo "Agrega algo"   (título de PR o asunto suelto)
#
# Reglas sobre el asunto (primera línea):
#   1. Empieza con mayúscula.
#   2. No termina en punto.
#   3. Mide como máximo 72 caracteres.
#   4. La primera palabra está en la lista blanca scripts/verbos-imperativo.txt
#      y va seguida de al menos una palabra más.
# Opcionalmente puede ir un prefijo de feature ("E0.1 ") antes del verbo.
# Los commits "Merge ..." y "fixup!/squash!" se aceptan sin validar.
# No pretende analizar el español en general: solo formato y lista blanca.
set -euo pipefail
export LC_ALL=C.UTF-8

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LISTA="${VERBOS_IMPERATIVO:-$DIR/verbos-imperativo.txt}"

if [[ "${1:-}" == "--titulo" ]]; then
  asunto="${2-}"
elif [[ -n "${1:-}" && -f "$1" ]]; then
  asunto="$(grep -v '^#' "$1" | sed -n '1p' || true)"
else
  echo "Uso: commit-lint.sh <archivo> | --titulo \"texto\"" >&2
  exit 2
fi

asunto="${asunto%$'\r'}"

if [[ "$asunto" =~ ^(Merge|fixup!|squash!) ]]; then
  exit 0
fi

error() {
  echo "commit-lint: \"$asunto\"" >&2
  echo "  -> $1" >&2
  echo "  Formato esperado: verbo en imperativo + descripción en español, p. ej. \"Agrega esqueleto del backend\"." >&2
  exit 1
}

[[ -n "$asunto" ]] || error "el asunto está vacío."
(( ${#asunto} <= 72 )) || error "el asunto mide ${#asunto} caracteres (máximo 72)."
[[ "$asunto" != *. ]] || error "el asunto no debe terminar en punto."

# Prefijo opcional de feature: "E0.1 " o "E10.3: ".
sin_prefijo="$(printf '%s' "$asunto" | sed -E 's/^E[0-9]+\.[0-9]+[: ] ?//')"

[[ "$sin_prefijo" =~ ^[[:upper:]] ]] || error "debe empezar con mayúscula."

read -r primera resto <<<"$sin_prefijo"
[[ -n "${resto:-}" ]] || error "debe tener una descripción después del verbo."

if ! grep -qxF -- "$primera" <(grep -v -e '^#' -e '^[[:space:]]*$' "$LISTA"); then
  error "\"$primera\" no está en la lista blanca de verbos (scripts/verbos-imperativo.txt)."
fi
