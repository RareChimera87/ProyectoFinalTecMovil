#!/usr/bin/env bash
# Parámetros y validaciones de android-release (E10.2). Se ejecuta SIN secretos, antes de entrar al
# environment `play`, y escribe `clave=valor` en la salida estándar (el workflow las pasa a GITHUB_OUTPUT).
#
# Entrada por variables de entorno:
#   EVENTO       workflow_dispatch | workflow_run
#   REF          refs/heads/dev, refs/heads/main o refs/tags/vX.Y.Z (GITHUB_REF; solo workflow_dispatch)
#   TAG          head_branch de la corrida de deploy-prod (solo workflow_run)
#   TAG_SHA      commit al que apunta refs/tags/$TAG en el repositorio (solo workflow_run)
#   HEAD_SHA     head_sha de la corrida de deploy-prod (solo workflow_run)
#   PISTA, ESTADO, VERSION_NAME   entradas del disparo manual (VERSION_NAME puede ir vacío)
#   ULTIMO_TAG   último tag v* alcanzable (puede ir vacío; se usa 0.0.1)
#   EN_MAIN      true si el commit del tag es alcanzable desde origin/main (obligatorio cuando hay tag)
#   RUN_NUMBER   número de ejecución (para el sufijo -int.<n>)
#
# Reglas: solo se publica código revisado (dev, main o un tag vX.Y.Z), la pista cerrada (alpha)
# solo sale de un tag vX.Y.Z, nunca de una rama, y un tag solo publica si apunta a un commit de main
# (misma regla que deploy-prod).
set -euo pipefail

error() { echo "::error::$1" >&2; exit 1; }
re_tag='^v[0-9]+\.[0-9]+\.[0-9]+$'

evento="${EVENTO:-}"
case "$evento" in
  workflow_run)
    tag="${TAG:-}"
    [[ "$tag" =~ $re_tag ]] || error "Tag inválido: $tag"
    [[ -n "${TAG_SHA:-}" && "${TAG_SHA}" == "${HEAD_SHA:-}" ]] ||
      error "$tag no es un tag que apunte al commit desplegado (¿rama con nombre de tag?)."
    nombre="${tag#v}"
    pista=alpha
    estado=completed
    ;;
  workflow_dispatch)
    ref="${REF:-}"
    tag=""
    case "$ref" in
      refs/heads/dev | refs/heads/main) ;;
      refs/tags/*)
        [[ "${ref#refs/tags/}" =~ $re_tag ]] || error "Tag no permitido: ${ref#refs/tags/}"
        tag="${ref#refs/tags/}"
        ;;
      *) error "Solo se publica desde dev, main o un tag vX.Y.Z (ref: ${ref:-vacía})." ;;
    esac
    pista="${PISTA:-internal}"
    estado="${ESTADO:-completed}"
    if [[ "$pista" == alpha && -z "$tag" ]]; then
      error "La pista cerrada (alpha) solo se publica desde un tag vX.Y.Z (ref: $ref)."
    fi
    if [[ -n "${VERSION_NAME:-}" ]]; then
      nombre="$VERSION_NAME"
    elif [[ -n "$tag" ]]; then
      nombre="${tag#v}"
    else
      ultimo="${ULTIMO_TAG:-v0.0.1}"
      nombre="${ultimo#v}-int.${RUN_NUMBER:?falta RUN_NUMBER}"
    fi
    ;;
  *) error "Evento no admitido para publicar: ${evento:-vacío}" ;;
esac

if [[ -n "$tag" && "${EN_MAIN:-}" != true ]]; then
  error "El tag $tag no apunta a un commit de main: solo se publican tags de main (EN_MAIN=true)."
fi

[[ "$nombre" =~ ^[0-9A-Za-z.+-]{1,50}$ ]] || error "versionName inválido: $nombre"
[[ "$pista" =~ ^(internal|alpha)$ ]] || error "Pista inválida: $pista"
[[ "$estado" =~ ^(completed|draft)$ ]] || error "Estado inválido: $estado"

echo "version_name=$nombre"
echo "pista=$pista"
echo "estado=$estado"
