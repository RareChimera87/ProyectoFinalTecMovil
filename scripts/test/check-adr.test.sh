#!/usr/bin/env bash
# Pruebas de scripts/check-adr.sh con directorios temporales de ADR.
# Uso: scripts/test/check-adr.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$DIR/check-adr.sh"
fallos=0
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

nuevo() { # nuevo <caso>: crea un ADR válido con su README
  mkdir -p "$tmp/$1"
  printf '# ADR-0001: Uno\n\n- **Estado:** Aceptada · 1 nov 2026\n\n## Contexto\nx\n' >"$tmp/$1/0001-uno.md"
  printf '| [0001](0001-uno.md) | Uno |\n' >"$tmp/$1/README.md"
}
caso() { # caso <esperado: acepta|rechaza> <nombre>
  "$CHECK" "$tmp/$2" >/dev/null 2>&1
  local rc=$?
  if [[ "$1" == acepta && $rc -eq 0 ]] || [[ "$1" == rechaza && $rc -eq 1 ]]; then echo "ok   $1: $2"
  else echo "FALLA debía $1 (rc=$rc): $2"; fallos=$((fallos + 1)); fi
}

nuevo valido;            caso acepta valido
nuevo completar;         printf '\n## Resultado\n_Completar_\n' >>"$tmp/completar/0001-uno.md"; caso rechaza completar
nuevo completar-real;    printf '\n## Resultado del spike\n- **Fecha:** 1 nov.\n_Completar en E0.7, **a más tardar el 25 oct 2026**: versión del plugin._\n' >>"$tmp/completar-real/0001-uno.md"; caso rechaza completar-real
nuevo completar-seguim;  printf '\n## Seguimiento\n_Completar en E10.9: medir el costo real._\n' >>"$tmp/completar-seguim/0001-uno.md"; caso rechaza completar-seguim
nuevo sin-estado;        printf '# ADR-0001\n\n## Contexto\nx\n' >"$tmp/sin-estado/0001-uno.md"; caso rechaza sin-estado
nuevo estado-raro;       sed -i 's/Aceptada/Quizá/' "$tmp/estado-raro/0001-uno.md"; caso rechaza estado-raro
nuevo propuesta;         sed -i 's/Aceptada/**Propuesta, pendiente**/' "$tmp/propuesta/0001-uno.md"; caso acepta propuesta
nuevo spike-sin-result;  printf '\nHay un spike el día 1.\n' >>"$tmp/spike-sin-result/0001-uno.md"; caso rechaza spike-sin-result
nuevo spike-vacio;       printf '\nHay un spike el día 1.\n\n## Resultado del spike\n' >>"$tmp/spike-vacio/0001-uno.md"; caso rechaza spike-vacio
nuevo spike-completo;    printf '\nHay un spike el día 1.\n\n## Resultado del spike\n- **Fecha:** 1 nov.\n- **Resultado:** funciona.\n' >>"$tmp/spike-completo/0001-uno.md"; caso acepta spike-completo
nuevo fuera-de-indice;   : >"$tmp/fuera-de-indice/README.md"; caso rechaza fuera-de-indice
mkdir -p "$tmp/vacio"; "$CHECK" "$tmp/vacio" >/dev/null 2>&1; [[ $? -eq 2 ]] && echo "ok   uso: directorio sin ADR" || { echo "FALLA directorio sin ADR"; fallos=$((fallos + 1)); }

# El repositorio real debe pasar.
"$CHECK" >/dev/null 2>&1 && echo "ok   repositorio real" || { echo "FALLA repositorio real"; fallos=$((fallos + 1)); }

((fallos == 0)) && echo "check-adr.test: todo en orden" || { echo "check-adr.test: $fallos fallo(s)"; exit 1; }
