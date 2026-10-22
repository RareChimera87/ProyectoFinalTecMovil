#!/usr/bin/env bash
# Pruebas de scripts/check-migration-order.sh con las fixtures de scripts/test/fixtures/migration-order
# (cada caso tiene base/ = migraciones de origin/dev y head/ = migraciones del PR) y con un repo
# git temporal para el modo por defecto.
# Uso: scripts/test/check-migration-order.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$DIR/check-migration-order.sh"
FIX="$DIR/test/fixtures/migration-order"
fallos=0

caso() { # caso <esperado: acepta|rechaza> <fixture>
  "$CHECK" --base-dir "$FIX/$2/base" --head-dir "$FIX/$2/head" >/dev/null 2>&1
  local rc=$?
  if [[ "$1" == acepta && $rc -eq 0 ]] || [[ "$1" == rechaza && $rc -eq 1 ]]; then
    echo "ok   $1: $2"
  else
    echo "FALLA debía $1 (rc=$rc): $2"; fallos=$((fallos + 1))
  fi
}

caso acepta  vacia-a-esquema
caso acepta  nueva-en-orden
caso acepta  sin-cambios
caso rechaza nueva-menor
caso rechaza misma-fecha-hora-menor
caso rechaza version-igual
caso rechaza secuencial
caso rechaza mal-nombre
caso rechaza duplicadas-nuevas

# El mensaje de rechazo nombra el archivo y la versión máxima de la base.
salida="$("$CHECK" --base-dir "$FIX/nueva-menor/base" --head-dir "$FIX/nueva-menor/head" 2>&1)"
if grep -q "V20261028_0900__agrega_cuotas.sql" <<<"$salida" && grep -q "20261030.1015" <<<"$salida"; then
  echo "ok   mensaje de rechazo"
else
  echo "FALLA mensaje de rechazo: $salida"; fallos=$((fallos + 1))
fi

# Uso incorrecto: 2.
"$CHECK" --base-dir "$FIX/nueva-menor/base" >/dev/null 2>&1
if [[ $? -eq 2 ]]; then echo "ok   uso incorrecto"; else echo "FALLA uso incorrecto"; fallos=$((fallos + 1)); fi

# Modo git: la base es una rama (simula origin/dev) y el PR es el árbol de trabajo.
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
(
  set -e
  cd "$tmp"
  git init -q -b dev
  git config user.email t@t.t; git config user.name t
  mkdir -p backend/src/main/resources/db/migration
  m=backend/src/main/resources/db/migration
  echo x >"$m/V1__esquema.sql"; echo x >"$m/V2__semillas.sql"; echo x >"$m/V20261030_1015__agrega_grafo.sql"
  git add -A; git commit -qm base
  git checkout -qb feature/x
  echo x >"$m/V20261028_0900__agrega_cuotas.sql"
) >/dev/null 2>&1
(cd "$tmp" && "$CHECK" --base-ref dev >/dev/null 2>&1)
if [[ $? -eq 1 ]]; then echo "ok   rechaza: modo git con versión menor"; else echo "FALLA modo git debía rechazar"; fallos=$((fallos + 1)); fi
(cd "$tmp" && mv backend/src/main/resources/db/migration/V20261028_0900__agrega_cuotas.sql \
  backend/src/main/resources/db/migration/V20261101_0800__agrega_cuotas.sql)
(cd "$tmp" && "$CHECK" --base-ref dev >/dev/null 2>&1)
if [[ $? -eq 0 ]]; then echo "ok   acepta: modo git tras renombrar"; else echo "FALLA modo git debía aceptar"; fallos=$((fallos + 1)); fi
# Referencia base posicional (como la invoca migration-order.yml).
(cd "$tmp" && "$CHECK" dev >/dev/null 2>&1)
if [[ $? -eq 0 ]]; then echo "ok   acepta: ref base posicional"; else echo "FALLA ref base posicional"; fallos=$((fallos + 1)); fi
(cd "$tmp" && "$CHECK" no-existe >/dev/null 2>&1)
if [[ $? -eq 2 ]]; then echo "ok   posicional inexistente"; else echo "FALLA posicional inexistente"; fallos=$((fallos + 1)); fi
(cd "$tmp" && "$CHECK" dev otra >/dev/null 2>&1)
if [[ $? -eq 2 ]]; then echo "ok   dos posicionales rechazados"; else echo "FALLA dos posicionales"; fallos=$((fallos + 1)); fi
(cd "$tmp" && echo x >backend/src/main/resources/db/migration/V20260101_0000__vieja.sql && "$CHECK" dev >/dev/null 2>&1)
if [[ $? -eq 1 ]]; then echo "ok   rechaza: posicional con versión menor"; else echo "FALLA posicional con versión menor"; fallos=$((fallos + 1)); fi
(cd "$tmp" && rm backend/src/main/resources/db/migration/V20260101_0000__vieja.sql)
(cd "$tmp" && "$CHECK" --base-ref no-existe >/dev/null 2>&1)
if [[ $? -eq 2 ]]; then echo "ok   referencia inexistente"; else echo "FALLA referencia inexistente"; fallos=$((fallos + 1)); fi

if [[ $fallos -gt 0 ]]; then echo "$fallos prueba(s) fallaron"; exit 1; fi
echo "Todas las pruebas pasaron"
