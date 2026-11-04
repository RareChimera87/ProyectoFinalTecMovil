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
# En modo archivo, "Merge branch|pull request|remote-tracking branch ...", "fixup! " y
# "squash! " se aceptan sin validar; en modo --titulo no hay excepciones.
# No pretende analizar el español en general: solo formato y lista blanca.
set -euo pipefail

# ${#var} cuenta caracteres solo con un locale UTF-8; con uno de bytes un asunto con tildes
# mide de más. C.UTF-8 no existe en macOS, así que se elige el primero disponible y, si no
# hay ninguno, se cuentan los bytes que no son de continuación UTF-8 (abajo).
LOCALE_UTF8=""
for l in C.UTF-8 C.utf8 en_US.UTF-8 en_US.utf8 UTF-8; do
  if locale -a 2>/dev/null | grep -qixF -- "$l"; then LOCALE_UTF8="$l"; break; fi
done
[[ -n "$LOCALE_UTF8" ]] && export LC_ALL="$LOCALE_UTF8"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LISTA="${VERBOS_IMPERATIVO:-$DIR/verbos-imperativo.txt}"

modo="archivo"
if [[ "${1:-}" == "--titulo" ]]; then
  modo="titulo"
  asunto="${2-}"
elif [[ -n "${1:-}" && -f "$1" ]]; then
  asunto="$(grep -v '^#' "$1" | sed -n '1p' || true)"
else
  echo "Uso: commit-lint.sh <archivo> | --titulo \"texto\"" >&2
  exit 2
fi

asunto="${asunto%$'\r'}"

# La excepción solo aplica a commits de archivo, nunca a títulos de PR (--titulo).
if [[ "$modo" == "archivo" && "$asunto" =~ ^(Merge\ (branch|pull\ request|remote-tracking\ branch)\ |fixup!\ |squash!\ ) ]]; then
  exit 0
fi

error() {
  echo "commit-lint: \"$asunto\"" >&2
  echo "  -> $1" >&2
  echo "  Formato esperado: verbo en imperativo + descripción en español, p. ej. \"Agrega esqueleto del backend\"." >&2
  exit 1
}

[[ -n "$asunto" ]] || error "el asunto está vacío."
if [[ -n "$LOCALE_UTF8" ]]; then
  largo=${#asunto}
else
  largo="$(printf '%s' "$asunto" | LC_ALL=C tr -d '\200-\277' | wc -c | tr -d '[:space:]')"
fi
(( largo <= 72 )) || error "el asunto mide $largo caracteres (máximo 72)."
[[ "$asunto" != *. ]] || error "el asunto no debe terminar en punto."

# Prefijo opcional de feature: "E0.1 " o "E10.3: ".
sin_prefijo="$(printf '%s' "$asunto" | sed -E 's/^E[0-9]+\.[0-9]+[: ] ?//')"

[[ "$sin_prefijo" =~ ^[[:upper:]] ]] || error "debe empezar con mayúscula."

read -r primera resto <<<"$sin_prefijo"
[[ -n "${resto:-}" ]] || error "debe tener una descripción después del verbo."

if ! grep -qxF -- "$primera" <(grep -v -e '^#' -e '^[[:space:]]*$' "$LISTA"); then
  error "\"$primera\" no está en la lista blanca de verbos (scripts/verbos-imperativo.txt)."
fi
