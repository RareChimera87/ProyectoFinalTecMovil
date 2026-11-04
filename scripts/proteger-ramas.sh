#!/usr/bin/env bash
# Aplica la protección de ramas de ADR-0001 con gh. Lo ejecuta el dueño del repo (admin).
#
#   scripts/proteger-ramas.sh dev    -> PR obligatorio + checks requeridos + auto-merge squash
#   scripts/proteger-ramas.sh main   -> PR obligatorio (sin checks requeridos)
#
# Ambas ramas: enforce_admins:true, PR obligatorio con 0 aprobaciones (hay una sola
# cuenta y GitHub no deja aprobar el PR propio), sin force push ni borrado.
#
# CHECKS por defecto para dev: los jobs "resultado" que SIEMPRE reportan en cada PR
# (los workflows por ruta tienen un job final con el mismo nombre que corre aunque no
# haya cambios en su ruta). Si agregas o renombras uno, actualiza la lista y vuelve a
# ejecutar el script. Se puede sobrescribir: CHECKS="pr-title,gitleaks" scripts/...
# Cada check se fija al app_id 15368 (GitHub Actions): sin él cualquier status con el mismo nombre
# (publicado por otra app o con un token de la API de commit statuses) satisface el check requerido.
# strict:false en dev: los PR no tienen que estar al día con dev para mezclarse; así
# los PR paralelos con auto-merge no quedan BEHIND esperando un update-branch manual.
set -euo pipefail
REPO="${REPO:-RareChimera87/ProyectoFinalTecMovil}"
RAMA="${1:?Uso: proteger-ramas.sh <dev|main>}"
APP_ID_ACTIONS=15368   # id de la GitHub App "GitHub Actions"
CHECKS_DEV="pr-title,gitleaks,major-gate,revision-agente,android-ci,backend-ci,contract-ci,infra-ci,rules-ci,migration-order"

case "$RAMA" in
  dev)
    CHECKS="${CHECKS:-$CHECKS_DEV}"
    CHECKS="${CHECKS//[[:space:]]/}"
    contextos="$(printf '%s' "$CHECKS" | jq -R --argjson app "$APP_ID_ACTIONS" 'split(",") | map(select(length > 0) | {context: ., app_id: $app})')"
    gh repo edit "$REPO" --enable-auto-merge --enable-squash-merge --delete-branch-on-merge
    jq -n --argjson c "$contextos" '{
      required_status_checks: {strict: false, checks: $c},
      enforce_admins: true,
      required_pull_request_reviews: {required_approving_review_count: 0},
      restrictions: null, allow_force_pushes: false, allow_deletions: false
    }' | gh api -X PUT "repos/$REPO/branches/dev/protection" --input -
    ;;
  main)
    # Sin checks requeridos: el PR de release dev -> main ya pasó la CI en dev y los
    # gates de etiquetas (major-gate, revision-agente) no aplican a un PR de release.
    # El usuario lo mezcla él mismo; ese merge es su aprobación explícita.
    jq -n '{
      required_status_checks: null,
      enforce_admins: true,
      required_pull_request_reviews: {required_approving_review_count: 0},
      restrictions: null, allow_force_pushes: false, allow_deletions: false
    }' | gh api -X PUT "repos/$REPO/branches/main/protection" --input -
    ;;
  *) echo "Rama no soportada: $RAMA" >&2; exit 2 ;;
esac
echo "Protección aplicada a $RAMA"
