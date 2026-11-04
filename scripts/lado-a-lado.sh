#!/usr/bin/env bash
# Arma las imágenes lado a lado para la revisión humana de E10.4 (ADR-0022): a la izquierda el PNG
# exportado del mockup de Claude Design y a la derecha la captura Roborazzi en claro (I1-I4).
#
# Uso: scripts/lado-a-lado.sh [directorio-de-salida]
#
# Entradas:
#   docs/mockups/png/<ID>.png   mockup exportado (I1.png, I2.png, I3.png, I4.png; ver docs/mockups/png/README.md)
#   android/app/src/test/screenshots/...  captura Roborazzi de la pantalla
# Salida (por defecto docs/mockups/png/lado-a-lado/): <ID>_lado-a-lado.png con rótulos.
# Si falta el mockup de una pantalla se avisa y se sigue con las demás; si falta una captura, falla.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SALIDA="${1:-$RAIZ/docs/mockups/png/lado-a-lado}"
MOCKUPS="$RAIZ/docs/mockups/png"
CAPTURAS="$RAIZ/android/app/src/test/screenshots"

if command -v magick >/dev/null; then IM=(magick); elif command -v convert >/dev/null; then IM=(convert); else
  echo "Se necesita ImageMagick (magick o convert)" >&2; exit 2
fi

# ID -> captura Roborazzi en claro
declare -A CAPTURA=(
  [I1]="home/i1_3_de_5.png"
  [I2]="parche/d1_grafo_light.png"
  [I3]="results/i3_light.png"
  [I4]="plus/g1_paywall_anual_light.png"
)

mkdir -p "$SALIDA"
faltan=0
for id in I1 I2 I3 I4; do
  mockup="$MOCKUPS/$id.png"
  captura="$CAPTURAS/${CAPTURA[$id]}"
  [[ -f "$captura" ]] || { echo "Falta la captura $captura" >&2; exit 1; }
  if [[ ! -f "$mockup" ]]; then
    echo "AVISO: falta el mockup $mockup (exportarlo de Claude Design); se omite $id" >&2
    faltan=1
    continue
  fi
  # Ambas a la misma altura (la de la captura) para que los espaciados se comparen a ojo.
  alto="$("${IM[@]}" identify -format '%h' "$captura")"
  "${IM[@]}" \
    \( "$mockup" -resize "x$alto" -background white -gravity north -splice 0x40 -pointsize 22 -annotate +0+8 "Mockup $id" \) \
    \( -size 24x1 xc:'#999999' \) \
    \( "$captura" -resize "x$alto" -background white -gravity north -splice 0x40 -pointsize 22 -annotate +0+8 "Roborazzi (claro)" \) \
    +append "$SALIDA/${id}_lado-a-lado.png"
  echo "Generado $SALIDA/${id}_lado-a-lado.png"
done
exit "$faltan"
