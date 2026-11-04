#!/usr/bin/env bash
# Pruebas de scripts/version-code.sh (E10.2): el versionCode es estrictamente creciente entre dos
# ejecuciones del workflow android-release (y entre reintentos de una misma ejecución).
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
vc="$raiz/scripts/version-code.sh"
fallos=0

ok() { echo "ok   - $1"; }
falla() { echo "FALLA - $1"; fallos=$((fallos + 1)); }

esperar_igual() { # <nombre> <esperado> <obtenido>
  if [[ "$2" == "$3" ]]; then ok "$1"; else falla "$1: esperaba '$2', obtuve '$3'"; fi
}

esperar_error() { # <nombre> <args...>
  local nombre="$1"
  shift
  if "$vc" "$@" >/dev/null 2>&1; then falla "$nombre: debía fallar"; else ok "$nombre"; fi
}

esperar_igual "ejecución 1, intento 1" 110 "$("$vc" 1 1)"
esperar_igual "el intento por defecto es 1" "$("$vc" 7 1)" "$("$vc" 7)"
esperar_igual "re-ejecución (intento 2) suma 1" 111 "$("$vc" 1 2)"
esperar_igual "último intento admitido" 119 "$("$vc" 1 10)"

# Estrictamente creciente: dos ejecuciones consecutivas, con cualquier combinación de reintentos.
previo=0
creciente=true
for ejecucion in 1 2 3 4 5 99 100 101 9999 10000; do
  for intento in 1 2 3 10; do
    actual="$("$vc" "$ejecucion" "$intento")"
    if ((actual <= previo)); then
      creciente=false
      echo "  ejecución $ejecucion intento $intento → $actual (previo $previo)"
    fi
    previo="$actual"
  done
done
if $creciente; then ok "estrictamente creciente entre ejecuciones y reintentos"; else falla "no es creciente"; fi

if (("$("$vc" 2 1)" > "$("$vc" 1 10)")); then
  ok "la ejecución siguiente supera al último reintento de la anterior"
else
  falla "la ejecución siguiente no supera al último reintento"
fi
if (("$("$vc" 1 1)" > 1)); then ok "supera al versionCode 1 de la subida manual (E7.0)"; else falla "choca con la subida manual"; fi

esperar_error "sin argumentos"
esperar_error "ejecución 0" 0
esperar_error "ejecución no numérica" abc
esperar_error "ejecución negativa" -3
esperar_error "intento 0" 1 0
esperar_error "intento 11 (más de 10)" 1 11
esperar_error "supera el máximo de Play" 999999999 1
esperar_error "demasiados argumentos" 1 1 1

if ((fallos > 0)); then
  echo "$fallos prueba(s) fallaron"
  exit 1
fi
echo "todas las pruebas pasaron"
