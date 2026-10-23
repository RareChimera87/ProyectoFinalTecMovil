#!/usr/bin/env bash
# Verifica el IAM de un plan de Terraform en JSON (terraform show -json plan).
#
# Uso: scripts/check-iam.sh plan.json
#
# Falla si:
#   - alguna cuenta de servicio recibe roles/owner o roles/editor (en particular sa-api);
#   - el plan crea sa-api y le falta alguno de sus roles mínimos;
#   - el plan es de bootstrap (crea sa-deployer) y a sa-deployer le faltan los
#     roles de Firebase que usa E0.8 (reglas, índices y Hosting);
#   - el plan es de bootstrap (crea sa-terraform-plan) y: sa-terraform-plan tiene un
#     rol con acceso a datos (viewer, securityReviewer, firebase.viewer...) o su rol
#     personalizado incluye datastore.entities.*, logging.logEntries.* o similares;
#     alguna cuenta de servicio tiene roles de facturación; projectIamAdmin se
#     asigna sin condición (permitiría otorgarse owner).
set -euo pipefail

PLAN="${1:?Uso: $0 plan.json}"
command -v jq >/dev/null || { echo "Se necesita jq" >&2; exit 2; }

# Todas las asignaciones (member/members + role) del plan, en raíz y módulos.
bindings() {
  jq -r '
    [.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
      | select(.type | test("^google_.*_iam_(member|binding)$"))
      | {role: .values.role, members: ((.values.members // []) + [(.values.member // empty)])}
      | .role as $r | .members[] | "\($r)\t\(.)"] | unique | .[]' "$PLAN"
}

errors=0
fail() { echo "FALLA: $*" >&2; errors=$((errors + 1)); }

all="$(bindings)"

# 1. Nada de owner/editor para cuentas de servicio.
while IFS=$'\t' read -r role member; do
  [ -z "$role" ] && continue
  case "$role" in
    roles/owner|roles/editor)
      case "$member" in
        serviceAccount:*) fail "$member tiene $role" ;;
      esac ;;
  esac
done <<<"$all"

has_role() { grep -qxF "$1"$'\t'"$2" <<<"$all"; }
creates_sa() {
  jq -e --arg id "$1" '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_service_account" and .values.account_id == $id)] | length > 0' "$PLAN" >/dev/null
}
sa_member() {
  jq -r --arg id "$1" '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_service_account" and .values.account_id == $id)
    | "serviceAccount:\(.values.account_id)@\(.values.project).iam.gserviceaccount.com"][0]' "$PLAN"
}

# 2. sa-api: roles mínimos de arquitectura.md §12.
if creates_sa sa-api; then
  api="$(sa_member sa-api)"
  for r in roles/cloudsql.client roles/cloudsql.instanceUser roles/aiplatform.user \
           roles/cloudtasks.enqueuer roles/datastore.user roles/firebaseauth.admin \
           roles/firebasecloudmessaging.admin; do
    has_role "$r" "$api" || fail "$api no tiene $r"
  done
  echo "sa-api: $(grep -cF "$api" <<<"$all") asignaciones, ninguna owner/editor"
fi

# 3. sa-deployer (bootstrap): roles para desplegar Firebase (E0.8) y la API.
if creates_sa sa-deployer; then
  dep="$(sa_member sa-deployer)"
  for r in roles/firebaserules.admin roles/datastore.indexAdmin roles/firebasehosting.admin \
           roles/run.admin roles/artifactregistry.writer; do
    has_role "$r" "$dep" || fail "$dep no tiene $r"
  done
  echo "sa-deployer: roles de Firebase y Cloud Run presentes"
fi

# 4. Bootstrap: sa-terraform-plan de solo metadatos, sin facturación, projectIamAdmin con condición.
if creates_sa sa-terraform-plan; then
  plan_sa="$(sa_member sa-terraform-plan)"
  for r in roles/viewer roles/editor roles/owner roles/iam.securityReviewer roles/firebase.viewer \
           roles/datastore.viewer roles/datastore.owner roles/logging.viewer roles/logging.privateLogViewer \
           roles/secretmanager.secretAccessor; do
    if has_role "$r" "$plan_sa"; then fail "$plan_sa no debe tener $r (da acceso a datos)"; fi
  done
  # El rol personalizado no puede leer entidades, logs ni payloads.
  bad="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_project_iam_custom_role") | .values.permissions[]?
    | select(test("^(datastore\\.entities|datastore\\.backups|logging\\.logEntries|secretmanager\\.versions\\.access|storage\\.objects\\.get|artifactregistry\\.(files|repositories\\.download))"))] | join(",")' "$PLAN")"
  [ -z "$bad" ] || fail "el rol personalizado de plan incluye permisos de datos: $bad"
  echo "sa-terraform-plan: solo metadatos"

  # Nada de roles sobre la cuenta de facturación para cuentas de servicio.
  billing="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type | test("^google_billing_account_iam_"))] | length' "$PLAN")"
  [ "$billing" = "0" ] || fail "el plan asigna $billing rol(es) sobre la cuenta de facturación; el presupuesto lo crea el usuario"

  # projectIamAdmin siempre con condición (el plan completo trae un bloque `condition`).
  uncond="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_project_iam_member" and .values.role == "roles/resourcemanager.projectIamAdmin"
             and ((.values.condition // []) | length) == 0)] | length' "$PLAN")"
  [ "$uncond" = "0" ] || fail "projectIamAdmin sin condición: permitiría otorgarse roles/owner"
fi

if [ "$errors" -gt 0 ]; then
  echo "check-iam: $errors problema(s)" >&2
  exit 1
fi
echo "check-iam: OK"
