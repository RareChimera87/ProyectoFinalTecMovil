#!/usr/bin/env bash
# Verifica que sa-terraform-plan pueda refrescar cada tipo *_iam_member de modules/platform:
# cada uno necesita su permiso getIamPolicy en los roles de plan del bootstrap.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BOOT="$ROOT/infra/terraform/bootstrap/main.tf"
PLATFORM="$ROOT/infra/terraform/modules/platform"

# tipo de recurso -> "permiso|origen" (origen: custom, o el rol predefinido que lo incluye)
declare -A MAPA=(
  [google_pubsub_topic_iam_member]="pubsub.topics.getIamPolicy|custom"
  [google_project_iam_member]="resourcemanager.projects.getIamPolicy|custom"
  [google_secret_manager_secret_iam_member]="secretmanager.secrets.getIamPolicy|roles/secretmanager.viewer"
  [google_cloud_run_v2_service_iam_member]="run.services.getIamPolicy|roles/run.viewer"
  [google_service_account_iam_member]="iam.serviceAccounts.getIamPolicy|roles/iam.serviceAccountViewer"
)

pass=0; failed=0
check() { if eval "$2"; then pass=$((pass + 1)); echo "  ✓ $1"; else failed=$((failed + 1)); echo "  ✗ $1"; fi; }

custom_block="$(awk '/resource "google_project_iam_custom_role" "terraform_plan"/{f=1} f{print} f&&/^}/{exit}' "$BOOT")"
roles_block="$(awk '/terraform_plan_roles = \[/{f=1} f{print} f&&/\]/{exit}' "$BOOT")"

echo "permisos getIamPolicy de sa-terraform-plan"
tipos="$(grep -rhoE 'resource "google_[a-z0-9_]+_iam_(member|binding)"' "$PLATFORM" | sed -E 's/resource "(.*)"/\1/; s/_binding$/_member/' | sort -u)"
for tipo in $tipos; do
  entrada="${MAPA[$tipo]:-}"
  if [ -z "$entrada" ]; then check "$tipo tiene permiso de lectura asignado (agregar a MAPA)" 'false'; continue; fi
  permiso="${entrada%%|*}"; origen="${entrada##*|}"
  if [ "$origen" = custom ]; then
    check "$tipo -> $permiso en parcheuTerraformPlan" 'grep -q "\"$permiso\"" <<<"$custom_block"'
  else
    check "$tipo -> $permiso vía $origen" 'grep -q "\"$origen\"" <<<"$roles_block"'
  fi
done

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
