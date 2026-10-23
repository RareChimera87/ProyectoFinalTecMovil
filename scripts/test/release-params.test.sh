#!/usr/bin/env bash
# Pruebas de scripts/release-params.sh (E10.2): qué refs y pistas puede publicar android-release.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
rp="$raiz/scripts/release-params.sh"
fallos=0
ok() { echo "ok   - $1"; }
falla() { echo "FALLA - $1"; fallos=$((fallos + 1)); }

# ejecutar <VAR=valor>...: corre el script con un entorno limpio.
ejecutar() { env -i PATH="$PATH" "$@" "$rp" 2>/dev/null; }

permite() { # <nombre> <esperado (clave=valor)> <VAR=valor>...
  local nombre="$1" esperado="$2" salida
  shift 2
  if salida="$(ejecutar "$@")" && grep -qxF "$esperado" <<<"$salida"; then ok "$nombre"; else falla "$nombre: faltó '$esperado'"; fi
}
rechaza() { # <nombre> <VAR=valor>...
  local nombre="$1"
  shift
  if ejecutar "$@" >/dev/null; then falla "$nombre: debía fallar"; else ok "$nombre"; fi
}

D=EVENTO=workflow_dispatch
# Disparo manual: dev, main y tags vX.Y.Z; internal por defecto.
permite "dev + internal" pista=internal "$D" REF=refs/heads/dev PISTA=internal ESTADO=completed RUN_NUMBER=7 ULTIMO_TAG=v0.1.0
permite "sufijo -int.<n> con el último tag" version_name=0.1.0-int.7 "$D" REF=refs/heads/dev PISTA=internal ESTADO=completed RUN_NUMBER=7 ULTIMO_TAG=v0.1.0
permite "sin tags usa 0.0.1" version_name=0.0.1-int.3 "$D" REF=refs/heads/main PISTA=internal ESTADO=completed RUN_NUMBER=3
permite "main + internal" pista=internal "$D" REF=refs/heads/main PISTA=internal ESTADO=draft RUN_NUMBER=1
permite "tag + alpha" pista=alpha "$D" REF=refs/tags/v0.1.0 PISTA=alpha ESTADO=completed RUN_NUMBER=1 EN_MAIN=true
permite "tag: versionName = tag sin la v" version_name=0.1.0 "$D" REF=refs/tags/v0.1.0 PISTA=alpha ESTADO=completed RUN_NUMBER=1 EN_MAIN=true
permite "versionName explícito" version_name=0.2.0-rc.1 "$D" REF=refs/heads/dev PISTA=internal ESTADO=completed RUN_NUMBER=1 VERSION_NAME=0.2.0-rc.1

# Rechazos: ramas de feature, PR, alpha desde rama, tags mal formados.
rechaza "rama de feature" "$D" REF=refs/heads/feature/x PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "rama de feature con alpha" "$D" REF=refs/heads/feature/x PISTA=alpha ESTADO=completed RUN_NUMBER=1
rechaza "alpha desde dev" "$D" REF=refs/heads/dev PISTA=alpha ESTADO=completed RUN_NUMBER=1
rechaza "alpha desde main" "$D" REF=refs/heads/main PISTA=alpha ESTADO=completed RUN_NUMBER=1
rechaza "ref de pull request" "$D" REF=refs/pull/24/merge PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "tag fuera de main" "$D" REF=refs/tags/v0.1.0 PISTA=alpha ESTADO=completed RUN_NUMBER=1 EN_MAIN=false
rechaza "tag sin EN_MAIN" "$D" REF=refs/tags/v0.1.0 PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "tag mal formado" "$D" REF=refs/tags/v1 PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "tag no v*" "$D" REF=refs/tags/prueba PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "ref vacía" "$D" PISTA=internal ESTADO=completed RUN_NUMBER=1
rechaza "pista inválida" "$D" REF=refs/heads/dev PISTA=production ESTADO=completed RUN_NUMBER=1
rechaza "estado inválido" "$D" REF=refs/heads/dev PISTA=internal ESTADO=inProgress RUN_NUMBER=1
rechaza "versionName inválido" "$D" REF=refs/heads/dev PISTA=internal ESTADO=completed RUN_NUMBER=1 'VERSION_NAME=1.0 $(x)'
rechaza "evento pull_request" EVENTO=pull_request REF=refs/heads/dev PISTA=internal ESTADO=completed RUN_NUMBER=1

# Tras deploy-prod: alpha, solo si el tag es de verdad un tag que apunta al commit desplegado.
R=EVENTO=workflow_run
permite "workflow_run + tag válido → alpha" pista=alpha "$R" TAG=v0.1.0 TAG_SHA=abc HEAD_SHA=abc EN_MAIN=true
permite "workflow_run → estado completed" estado=completed "$R" TAG=v0.1.0 TAG_SHA=abc HEAD_SHA=abc EN_MAIN=true
permite "workflow_run → versionName" version_name=0.1.0 "$R" TAG=v0.1.0 TAG_SHA=abc HEAD_SHA=abc EN_MAIN=true
rechaza "workflow_run con rama llamada v0.1.0 (sin tag)" "$R" TAG=v0.1.0 HEAD_SHA=abc
rechaza "workflow_run con tag en otro commit" "$R" TAG=v0.1.0 TAG_SHA=zzz HEAD_SHA=abc
rechaza "workflow_run con rama dev" "$R" TAG=dev TAG_SHA=abc HEAD_SHA=abc
if [[ "$(ejecutar "$R" TAG=v0.1.0 TAG_SHA=abc HEAD_SHA=abc EN_MAIN=true PISTA=internal | grep '^pista=')" == pista=alpha ]]; then
  ok "workflow_run siempre alpha"
else falla "workflow_run debía forzar alpha"; fi

if ((fallos > 0)); then
  echo "$fallos prueba(s) fallaron"
  exit 1
fi
echo "todas las pruebas pasaron"
