#!/usr/bin/env bash
# Aplica la protección de ramas de ADR-0001 con gh. Lo ejecuta el dueño del repo (admin).
#
#   scripts/proteger-ramas.sh dev    -> checks requeridos + auto-merge squash
#   scripts/proteger-ramas.sh main   -> PR con aprobación del usuario
#
# Ejecútalo para `dev` solo cuando los checks existan (hayan corrido al menos una vez);
# strict:false en dev: así los PR paralelos con auto-merge no quedan BEHIND esperando
# un update-branch manual.
# `revision-agente` y las CI por ruta llegan con E0.6. Hasta entonces usa CHECKS para
# indicar los disponibles, p. ej.: CHECKS="pr-title,gitleaks,major-gate".
set -euo pipefail
REPO="${REPO:-RareChimera87/ProyectoFinalTecMovil}"
RAMA="${1:?Uso: proteger-ramas.sh <dev|main>}"

case "$RAMA" in
  dev)
    CHECKS="${CHECKS:-pr-title,gitleaks,major-gate}"
    contextos="$(printf '%s' "$CHECKS" | jq -R 'split(",") | map({context: .})')"
    gh repo edit "$REPO" --enable-auto-merge --enable-squash-merge --delete-branch-on-merge
    jq -n --argjson c "$contextos" '{
      required_status_checks: {strict: false, checks: $c},
      enforce_admins: true,
      required_pull_request_reviews: {required_approving_review_count: 0},
      restrictions: null, allow_force_pushes: false, allow_deletions: false
    }' | gh api -X PUT "repos/$REPO/branches/dev/protection" --input -
    ;;
  main)
    # Con una sola cuenta (autora y admin) GitHub no deja aprobar el PR propio, y con
    # enforce_admins:true nadie podría mezclar. Por eso enforce_admins:false: el PR exige
    # 1 aprobación, pero el usuario lo mezcla con `gh pr merge --admin`, que es su
    # aprobación explícita. Cuando haya un segundo colaborador se puede pasar a true.
    jq -n '{
      required_status_checks: null,
      enforce_admins: false,
      required_pull_request_reviews: {required_approving_review_count: 1},
      restrictions: null, allow_force_pushes: false, allow_deletions: false
    }' | gh api -X PUT "repos/$REPO/branches/main/protection" --input -
    ;;
  *) echo "Rama no soportada: $RAMA" >&2; exit 2 ;;
esac
echo "Protección aplicada a $RAMA"
