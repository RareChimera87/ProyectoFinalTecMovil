#!/usr/bin/env bash
# Pruebas de scripts/check-links.sh con árboles temporales.
# Uso: scripts/test/check-links.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$DIR/check-links.sh"
fallos=0
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

arbol() { mkdir -p "$tmp/$1/docs"; printf '# Titulo\n\n## Sección uno · (0.1)\n' >"$tmp/$1/docs/a.md"; : >"$tmp/$1/README.md"; }
caso() { # caso <esperado: acepta|rechaza> <nombre>
  "$CHECK" "$tmp/$2" >/dev/null 2>&1
  local rc=$?
  if [[ "$1" == acepta && $rc -eq 0 ]] || [[ "$1" == rechaza && $rc -eq 1 ]]; then echo "ok   $1: $2"
  else echo "FALLA debía $1 (rc=$rc): $2"; fallos=$((fallos + 1)); fi
}

arbol bien;      printf '[a](docs/a.md) [b](docs/a.md#sección-uno--01) [web](https://x.y/z) [c](#nada)\n' >"$tmp/bien/README.md"
printf '# Nada\n' >>"$tmp/bien/README.md"; caso acepta bien
arbol roto;      printf '[a](docs/no-existe.md)\n' >"$tmp/roto/README.md"; caso rechaza roto
arbol ancla;     printf '[a](docs/a.md#no-hay)\n' >"$tmp/ancla/README.md"; caso rechaza ancla
arbol codigo;    printf '```\n[a](docs/no-existe.md)\n```\n`[b](x.md)`\n' >"$tmp/codigo/README.md"; caso acepta codigo
arbol desbalance; printf '```\nabierto sin cerrar\n' >"$tmp/desbalance/docs/b.md"; caso acepta desbalance
arbol dir;       printf '[d](docs/)\n' >"$tmp/dir/README.md"; caso acepta dir

"$CHECK" >/dev/null 2>&1 && echo "ok   repositorio real" || { echo "FALLA repositorio real"; fallos=$((fallos + 1)); }

((fallos == 0)) && echo "check-links.test: todo en orden" || { echo "check-links.test: $fallos fallo(s)"; exit 1; }
