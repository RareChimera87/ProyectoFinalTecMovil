#!/usr/bin/env bash
# versionCode del AAB que sube android-release (E10.2), derivado de la ejecución del workflow.
#
#   scripts/version-code.sh <número de ejecución> [intento]
#
# Fórmula: 100 + número × 10 + (intento − 1). El número de ejecución (GITHUB_RUN_NUMBER) crece en
# cada corrida del workflow y el intento (GITHUB_RUN_ATTEMPT) en cada re-ejecución, así que el
# resultado es estrictamente creciente entre ejecuciones y entre reintentos (Play rechaza un
# versionCode repetido). El desplazamiento de 100 deja libres los códigos bajos para subidas
# manuales (la 0.0.1 de E7.0 usa 1). Se admiten hasta 10 intentos por ejecución.
set -euo pipefail

readonly BASE=100
readonly MAX_INTENTOS=10
# Límite de Play para versionCode.
readonly MAX_VERSION_CODE=2100000000

uso() {
  echo "uso: $0 <número de ejecución ≥ 1> [intento 1-$MAX_INTENTOS]" >&2
  exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || uso
ejecucion="$1"
intento="${2:-1}"
[[ "$ejecucion" =~ ^[1-9][0-9]{0,8}$ ]] || uso
[[ "$intento" =~ ^[1-9][0-9]?$ ]] || uso
if ((intento > MAX_INTENTOS)); then
  echo "Intento $intento: se admiten hasta $MAX_INTENTOS por ejecución; lanza una ejecución nueva." >&2
  exit 1
fi

codigo=$((BASE + ejecucion * 10 + intento - 1))
if ((codigo > MAX_VERSION_CODE)); then
  echo "versionCode $codigo supera el máximo de Play ($MAX_VERSION_CODE)." >&2
  exit 1
fi
echo "$codigo"
