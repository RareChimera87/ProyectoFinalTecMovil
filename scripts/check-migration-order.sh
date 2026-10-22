#!/usr/bin/env bash
# Verifica el orden de las migraciones Flyway de un PR (ADR-0011, E0.4).
#
# Falla si el PR agrega una migración cuya versión es menor o igual que la máxima de la base
# (por defecto origin/dev), o si su nombre no sigue V<yyyyMMdd>_<HHmm>__<descripcion>.sql.
# Así prod, que aplica en lote sin outOfOrder, siempre recibe las migraciones en orden.
#
# Uso:
#   scripts/check-migration-order.sh                       (compara el árbol de trabajo con origin/dev)
#   scripts/check-migration-order.sh --base-ref origin/dev (otra referencia de git como base)
#   scripts/check-migration-order.sh --base-dir A --head-dir B   (dos carpetas; lo usan las pruebas)
#
# Variables: MIGRATIONS_DIR (ruta relativa a la raíz del repo; por defecto
# backend/src/main/resources/db/migration).
# Salida: 0 si todo está en orden, 1 si hay errores, 2 si el uso es incorrecto.
set -euo pipefail
export LC_ALL=C

MIGRATIONS_DIR="${MIGRATIONS_DIR:-backend/src/main/resources/db/migration}"
base_ref="origin/dev"
base_dir=""
head_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-ref) base_ref="${2:?falta la referencia}"; shift 2 ;;
    --base-dir) base_dir="${2:?falta la carpeta}"; shift 2 ;;
    --head-dir) head_dir="${2:?falta la carpeta}"; shift 2 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "Argumento desconocido: $1" >&2; exit 2 ;;
  esac
done

# Lista los nombres de archivo .sql de migración de una carpeta (vacío si no existe).
listar_carpeta() {
  [[ -d "$1" ]] || return 0
  find "$1" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' | sort
}

if [[ -n "$base_dir" || -n "$head_dir" ]]; then
  if [[ -z "$base_dir" || -z "$head_dir" ]]; then
    echo "--base-dir y --head-dir van juntos" >&2; exit 2
  fi
  base_files="$(listar_carpeta "$base_dir")"
  head_files="$(listar_carpeta "$head_dir")"
else
  raiz="$(git rev-parse --show-toplevel)"
  if ! git rev-parse --verify --quiet "$base_ref^{commit}" >/dev/null; then
    echo "No existe la referencia base '$base_ref' (¿falta git fetch origin dev?)" >&2; exit 2
  fi
  base_files="$(git ls-tree --name-only "$base_ref" -- "$MIGRATIONS_DIR/" 2>/dev/null \
    | sed 's|.*/||' | grep -E '^V.*__.*\.sql$' | sort || true)"
  head_files="$(listar_carpeta "$raiz/$MIGRATIONS_DIR")"
fi

# Versión Flyway de un archivo: "V20261027_1430__x.sql" -> "20261027.1430"; "V2__x.sql" -> "2".
version() {
  local v="${1#V}"
  v="${v%%__*}"
  echo "${v//_/.}"
}

# Verdadero si la versión $1 es menor o igual que $2 (comparación numérica por partes).
menor_o_igual() {
  [[ "$1" == "$2" ]] && return 0
  [[ "$(printf '%s\n%s\n' "$1" "$2" | sort -t. -k1,1n -k2,2n -k3,3n | head -1)" == "$1" ]]
}

max_base=""
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  v="$(version "$f")"
  if [[ -z "$max_base" ]] || menor_o_igual "$max_base" "$v"; then max_base="$v"; fi
done <<<"$base_files"

nuevas="$(comm -13 <(printf '%s\n' "$base_files" | sed '/^$/d') <(printf '%s\n' "$head_files" | sed '/^$/d'))"

if [[ -z "$nuevas" ]]; then
  echo "ok: el PR no agrega migraciones (máxima de la base: ${max_base:-ninguna})"
  exit 0
fi

patron='^V20[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])_([01][0-9]|2[0-3])[0-5][0-9]__[a-z0-9_]+\.sql$'
errores=0
vistas=""
while IFS= read -r f; do
  v="$(version "$f")"
  if [[ -z "$base_files" && ( "$f" == "V1__esquema.sql" || "$f" == "V2__semillas.sql" ) ]]; then
    : # Primera entrega del esquema (E0.4).
  elif [[ ! "$f" =~ $patron ]]; then
    echo "ERROR: $f no sigue V<yyyyMMdd>_<HHmm>__<descripcion>.sql (minúsculas, números y _)"
    errores=$((errores + 1)); continue
  fi
  if [[ -n "$max_base" ]] && menor_o_igual "$v" "$max_base"; then
    echo "ERROR: $f (versión $v) no es mayor que la última de la base ($max_base)."
    echo "       Renómbrala con la fecha y hora actuales, p. ej. V$(date -u +%Y%m%d_%H%M)__${f#*__}"
    errores=$((errores + 1))
  fi
  if grep -qxF "$v" <<<"$vistas"; then
    echo "ERROR: versión $v repetida entre las migraciones nuevas ($f)"
    errores=$((errores + 1))
  fi
  vistas+="$v"$'\n'
done <<<"$nuevas"

if [[ $errores -gt 0 ]]; then
  echo "check-migration-order: $errores error(es)"
  exit 1
fi
echo "ok: $(wc -l <<<"$nuevas" | tr -d ' ') migración(es) nueva(s) en orden (máxima de la base: ${max_base:-ninguna})"
