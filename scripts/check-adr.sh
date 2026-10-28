#!/usr/bin/env bash
# Verifica los ADR de docs/adr (E10.7).
#
# Uso: scripts/check-adr.sh [directorio-de-adr]   (por defecto docs/adr)
#
# Falla (código 1) si algún ADR:
#   - conserva una marca '_Completar_' o '_Completar en E0.x, ...: ..._' (sin distinguir mayúsculas);
#   - no tiene la línea '- **Estado:**' con un estado válido (Aceptada, Propuesta, Reemplazada o Rechazada);
#   - menciona un spike pero no tiene la sección '## Resultado' con contenido;
#   - no figura en la tabla de docs/adr/README.md.
# Código 2: uso incorrecto.
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="${1:-$RAIZ/docs/adr}"
[[ -d "$DIR" ]] || { echo "No existe el directorio $DIR" >&2; exit 2; }

errores=0
fallo() { echo "FALLA: $*" >&2; errores=$((errores + 1)); }

shopt -s nullglob
adrs=("$DIR"/[0-9][0-9][0-9][0-9]-*.md)
((${#adrs[@]} > 0)) || { echo "No hay ADR en $DIR" >&2; exit 2; }

for f in "${adrs[@]}"; do
  nombre="$(basename "$f")"

  if grep -qiE '_Completar([[:space:]_.,:]|$)' "$f"; then
    fallo "$nombre: queda una marca '_Completar_' o '_Completar en ...'"
  fi

  estado="$(grep -m1 -E '^- \*\*Estado:\*\*' "$f" || true)"
  if [[ -z "$estado" ]]; then
    fallo "$nombre: falta la línea '- **Estado:**'"
  elif ! grep -qE '^- \*\*Estado:\*\* \*{0,2}(Aceptada|Propuesta|Reemplazada|Rechazada)' <<<"$estado"; then
    fallo "$nombre: estado no reconocido ($estado)"
  fi

  # Texto sin la sección de resultado, para detectar si el ADR habla de un spike.
  if awk '/^## Resultado/{salir=1} !salir' "$f" | grep -qi 'spike'; then
    if ! grep -qE '^## Resultado' "$f"; then
      fallo "$nombre: menciona un spike y no tiene la sección '## Resultado'"
    else
      cuerpo="$(awk '/^## Resultado/{dentro=1; next} /^## /{dentro=0} dentro' "$f" | grep -cE '[[:alnum:]]')"
      if ((cuerpo < 2)); then
        fallo "$nombre: la sección '## Resultado' está vacía o es muy corta"
      fi
    fi
  fi

  if [[ -f "$DIR/README.md" ]] && ! grep -q "($nombre)" "$DIR/README.md"; then
    fallo "$nombre: no está en la tabla de README.md"
  fi
done

if ((errores > 0)); then
  echo "check-adr: $errores problema(s)" >&2
  exit 1
fi
echo "check-adr: ${#adrs[@]} ADR en orden"
