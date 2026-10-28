#!/usr/bin/env bash
# Comprueba que los bloques ```mermaid de un Markdown se analicen sin error (E10.7).
#
# Uso: scripts/check-mermaid.sh [archivo.md]   (por defecto docs/c4.md)
# Usa @mermaid-js/mermaid-cli con npx (descarga la primera vez; necesita red y Chromium para renderizar,
# por eso solo se corre en CI). Sin npx o sin red sale 0 con un aviso si MERMAID_OPCIONAL=1.
# Siempre verifica, sin red, que el archivo tenga al menos un bloque mermaid y que cada uno abra y cierre.
set -uo pipefail
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVO="${1:-$RAIZ/docs/c4.md}"
[[ -f "$ARCHIVO" ]] || { echo "No existe $ARCHIVO" >&2; exit 2; }

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
python3 -I - "$ARCHIVO" "$tmp" <<'PY' || exit 1
import re, sys, os
texto = open(sys.argv[1], encoding="utf-8").read()
bloques = re.findall(r"^```mermaid\n(.*?)^```", texto, flags=re.S | re.M)
if not bloques:
    sys.exit("FALLA: no hay bloques mermaid en " + sys.argv[1])
abiertos = len(re.findall(r"^```mermaid\s*$", texto, flags=re.M))
if abiertos != len(bloques):
    sys.exit("FALLA: un bloque mermaid no cierra")
for i, b in enumerate(bloques, 1):
    if not re.match(r"\s*(flowchart|graph|sequenceDiagram|C4Context|C4Container|stateDiagram|classDiagram|erDiagram)", b):
        sys.exit(f"FALLA: el bloque {i} no empieza con un tipo de diagrama conocido")
    open(os.path.join(sys.argv[2], f"d{i}.mmd"), "w", encoding="utf-8").write(b)
print(f"{len(bloques)} bloque(s) mermaid con forma válida")
PY

if ! command -v npx >/dev/null; then
  [[ "${MERMAID_OPCIONAL:-0}" == 1 ]] && { echo "aviso: sin npx, no se renderizó"; exit 0; }
  echo "Se necesita npx" >&2; exit 2
fi
cat >"$tmp/puppeteer.json" <<'JSON'
{"args": ["--no-sandbox"]}
JSON
rc=0
for f in "$tmp"/d*.mmd; do
  if ! npx --yes @mermaid-js/mermaid-cli@11.4.2 -p "$tmp/puppeteer.json" -i "$f" -o "${f%.mmd}.svg" >"$tmp/log" 2>&1; then
    echo "FALLA: $(basename "$f") no se renderiza" >&2; cat "$tmp/log" >&2; rc=1
  fi
done
((rc == 0)) && echo "check-mermaid: todos los diagramas se renderizan"
exit $rc
