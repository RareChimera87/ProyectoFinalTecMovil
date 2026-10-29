#!/usr/bin/env bash
# Pruebas de scripts/check-plan.sh y scripts/check-iam.sh con planes reales
# recortados (fixtures/plan-*.json, generados con `terraform show -json` y sin
# la cuenta de facturación) y mutaciones que deben fallar.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIX="$ROOT/scripts/test/fixtures"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass=0; failed=0
expect() { # expect <ok|fail> <descripción> <comando...>
  local want="$1" desc="$2"; shift 2
  if "$@" >"$TMP/out" 2>&1; then got=ok; else got=fail; fi
  if [ "$got" = "$want" ]; then
    pass=$((pass + 1)); echo "  ✓ $desc"
  else
    failed=$((failed + 1)); echo "  ✗ $desc (esperaba $want, obtuvo $got)"; sed 's/^/      /' "$TMP/out"
  fi
}
mutate() { # mutate <fixture> <filtro jq sobre cada recurso> → ruta
  local out="$TMP/m$RANDOM.json"
  jq "(.planned_values.root_module.resources) |= map($2)" "$FIX/$1" >"$out"; echo "$out"
}
drop() { # drop <fixture> <condición jq> → ruta
  local out="$TMP/d$RANDOM.json"
  jq "(.planned_values.root_module.resources) |= map(select(($2) | not))" "$FIX/$1" >"$out"; echo "$out"
}
add() { # add <fixture> <recurso json> → ruta
  local out="$TMP/a$RANDOM.json"
  jq --argjson r "$2" '.planned_values.root_module.resources += [$r]' "$FIX/$1" >"$out"; echo "$out"
}

echo "check-plan.sh"
expect ok   "plan de dev válido"  "$ROOT/scripts/check-plan.sh" "$FIX/plan-dev.json"
expect ok   "plan de prod válido" "$ROOT/scripts/check-plan.sh" "$FIX/plan-prod.json"
expect fail "falta el job seed-demo" "$ROOT/scripts/check-plan.sh" \
  "$(drop plan-dev.json '.type=="google_cloud_run_v2_job" and .values.name=="seed-demo"')"
expect fail "job purge-demo con otro argumento" "$ROOT/scripts/check-plan.sh" \
  "$(mutate plan-dev.json 'if .type=="google_cloud_run_v2_job" and .values.name=="purge-demo" then .values.template[0].template[0].containers[0].args=["serve"] else . end')"
expect fail "falta el job de Scheduler purge-sweep (E8.3)" "$ROOT/scripts/check-plan.sh" \
  "$(drop plan-dev.json '.type=="google_cloud_scheduler_job" and .values.name=="purge-sweep"')"
expect fail "cola simulations sin max_concurrent_dispatches" "$ROOT/scripts/check-plan.sh" \
  "$(mutate plan-dev.json 'if .type=="google_cloud_tasks_queue" and .values.name=="simulations" then .values.rate_limits[0].max_concurrent_dispatches=null else . end')"
expect fail "falta la cola maintenance" "$ROOT/scripts/check-plan.sh" \
  "$(drop plan-dev.json '.type=="google_cloud_tasks_queue" and .values.name=="maintenance"')"
expect fail "secreto con secret_data" "$ROOT/scripts/check-plan.sh" \
  "$(add plan-dev.json '{"address":"x","type":"google_secret_manager_secret_version","name":"x","values":{"secret_data":"no"}}')"
expect fail "falta el secreto play-sa" "$ROOT/scripts/check-plan.sh" \
  "$(drop plan-dev.json '.type=="google_secret_manager_secret" and .values.secret_id=="play-sa"')"
expect fail "dev con min_instance_count=1" "$ROOT/scripts/check-plan.sh" \
  "$(mutate plan-dev.json 'if .type=="google_cloud_run_v2_service" then .values.template[0].scaling[0].min_instance_count=1 else . end')"
expect fail "prod con máximo 10" "$ROOT/scripts/check-plan.sh" \
  "$(mutate plan-prod.json 'if .type=="google_cloud_run_v2_service" then .values.template[0].scaling[0].max_instance_count=10 else . end')"
expect fail "Firestore fuera de nam5" "$ROOT/scripts/check-plan.sh" \
  "$(mutate plan-dev.json 'if .type=="google_firestore_database" then .values.location_id="us-east1" else . end')"
cp "$ROOT/infra/terraform/README.md" "$TMP/README.md"
sed -i '/^| `ai-light`/s/`moderate`, //' "$TMP/README.md"
expect fail "README sin moderate en ai-light" "$ROOT/scripts/check-plan.sh" "$FIX/plan-dev.json" "$TMP/README.md"

echo "check-iam.sh"
expect ok   "plan de dev sin owner/editor" "$ROOT/scripts/check-iam.sh" "$FIX/plan-dev.json"
expect ok   "plan de prod sin owner/editor" "$ROOT/scripts/check-iam.sh" "$FIX/plan-prod.json"
expect ok   "bootstrap con roles de Firebase para sa-deployer" "$ROOT/scripts/check-iam.sh" "$FIX/plan-bootstrap-dev.json"
expect fail "sa-api con roles/editor" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-dev.json '{"address":"x","type":"google_project_iam_member","name":"x","values":{"role":"roles/editor","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}')"
expect fail "sa-api con roles/owner vía binding" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-dev.json '{"address":"x","type":"google_project_iam_binding","name":"x","values":{"role":"roles/owner","members":["serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"]}}')"
expect fail "sa-api sin cloudsql.instanceUser" "$ROOT/scripts/check-iam.sh" \
  "$(drop plan-dev.json '.type=="google_project_iam_member" and .values.role=="roles/cloudsql.instanceUser"')"
expect fail "sa-deployer sin firebaserules.admin" "$ROOT/scripts/check-iam.sh" \
  "$(drop plan-bootstrap-dev.json '.type=="google_project_iam_member" and .values.role=="roles/firebaserules.admin"')"

echo "check-iam.sh (bootstrap: identidades de CI)"
SAP='serviceAccount:sa-terraform-plan@parcheu-dev.iam.gserviceaccount.com'
expect fail "sa-terraform-plan con roles/viewer" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/viewer\",\"member\":\"$SAP\"}}")"
expect fail "sa-terraform-plan con securityReviewer" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/iam.securityReviewer\",\"member\":\"$SAP\"}}")"
expect fail "rol de plan con datastore.entities.list" "$ROOT/scripts/check-iam.sh" \
  "$(mutate plan-bootstrap-dev.json 'if .type=="google_project_iam_custom_role" then .values.permissions += ["datastore.entities.list"] else . end')"
expect fail "rol de plan con logging.logEntries.list" "$ROOT/scripts/check-iam.sh" \
  "$(mutate plan-bootstrap-dev.json 'if .type=="google_project_iam_custom_role" then .values.permissions += ["logging.logEntries.list"] else . end')"
expect fail "sa-terraform con costsManager en la facturación" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json '{"address":"x","type":"google_billing_account_iam_member","name":"x","values":{"role":"roles/billing.costsManager","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com"}}')"
expect fail "projectIamAdmin sin condición" "$ROOT/scripts/check-iam.sh" \
  "$(mutate plan-bootstrap-dev.json 'if .values.role=="roles/resourcemanager.projectIamAdmin" then .values.condition=[] else . end')"

echo "check-plan.sh (bootstrap y presupuesto)"
expect ok   "bootstrap con presupuesto al 50 % y 90 %" "$ROOT/scripts/check-plan.sh" "$FIX/plan-bootstrap-dev.json"
expect fail "bootstrap sin presupuesto" "$ROOT/scripts/check-plan.sh" \
  "$(drop plan-bootstrap-dev.json '.type=="google_billing_budget"')"
expect fail "plataforma con presupuesto (CI sin roles de facturación)" "$ROOT/scripts/check-plan.sh" \
  "$(add plan-dev.json '{"address":"x","type":"google_billing_budget","name":"x","values":{"threshold_rules":[{"threshold_percent":0.5},{"threshold_percent":0.9}]}}')"

# Los principals de WIF dependen del pool y no se conocen en el plan: su restricción
# a la rama dev se verifica en el código.
echo "bootstrap/main.tf (WIF)"
TF="$ROOT/infra/terraform/bootstrap/main.tf"
block() { awk -v n="$1" '$0 ~ "resource \"google_service_account_iam_member\" \"" n "\"" {p=1} p {print} p && /^}/ {exit}' "$TF"; }
expect ok   "sa-terraform (apply) solo desde la rama dev" grep -q 'local.dev_branch_principal' <(block terraform_wif)
expect fail "sa-terraform no acepta el principal de todo el repo" grep -q 'local.repo_principal' <(block terraform_wif)
expect ok   "el principal dev apunta a refs/heads/dev" grep -q 'dev_branch_principal = .*attribute.ref/refs/heads/dev' "$TF"
expect ok   "sa-terraform-plan en prod solo desde la rama dev" grep -q 'plan_principal = local.is_prod ? local.dev_branch_principal : local.repo_principal' "$TF"
# C-A3 / C-M7: sa-deployer de dev solo desde la rama dev; el de prod exige environment y tag.
expect ok   "sa-deployer de dev solo desde la rama dev" grep -qE 'deployer_principal = local.is_prod \? .* : local.dev_branch_principal$' "$TF"
expect fail "sa-deployer no acepta el principal de todo el repo" grep -qE 'deployer_principal = .*local.repo_principal' "$TF"
expect ok   "sa-deployer de prod exige production y tag v*" grep -q 'attribute.env_ref/production@tag' "$TF"
expect ok   "env_ref distingue los tags v*" grep -q "assertion.ref.startsWith('refs/tags/v') ? 'tag'" "$TF"
# C-B10: la condición del pool usa IDs, no el nombre del repo.
expect ok   "el pool exige repository_id y repository_owner_id" grep -q "attribute_condition = \"assertion.repository_id == .* && assertion.repository_owner_id == " "$TF"
expect fail "el pool no confía en el nombre del repo" grep -q "attribute_condition = \"assertion.repository ==" "$TF"
# C-M1: sa-terraform no administra cuentas de servicio ni el IAM del proyecto.
roles_tf="$(awk '/terraform_roles_dev = \[/{f=1} f{print} f&&/\]/{exit}' "$TF")"
for r in iam.serviceAccountAdmin iam.serviceAccountUser secretmanager.admin datastore.owner firebase.admin; do
  expect fail "sa-terraform sin roles/$r de proyecto" grep -q "roles/$r" <<<"$roles_tf"
done
expect fail "sa-terraform sin projectIamAdmin" grep -q 'roles/resourcemanager.projectIamAdmin' "$TF"
expect fail "parcheuTerraformApply no lee valores de secretos ni documentos" grep -qE '"(secretmanager\.versions|datastore\.entities)\.' <(awk '/"google_project_iam_custom_role" "terraform_apply"/{f=1} f{print} f&&/^}/{exit}' "$TF")
expect ok   "sa-terraform tiene actAs solo sobre sa-api y sa-tasks" grep -q '{ api = google_service_account.api.name, tasks = google_service_account.tasks.name }' <(block terraform_acts_as)

# C-M7: sa-play (prod) por WIF, con environment y ref.
PLAY="$ROOT/infra/terraform/modules/platform/play.tf"
expect ok   "sa-play acepta play solo desde dev, main o tag" grep -q '"play@refs/heads/dev",' "$PLAY"
expect fail "sa-play no acepta solo el environment" grep -q 'attribute.environment/play' "$PLAY"

# C-B5: las alertas siempre tienen destinatario.
PROD="$ROOT/infra/terraform/envs/prod/main.tf"
alert_prod="$(awk '/variable "alert_emails"/{f=1} f{print} f&&/^}/{exit}' "$PROD")"
expect fail "alert_emails de prod sin valor por defecto" grep -q 'default' <<<"$alert_prod"
expect ok   "alert_emails de prod exige al menos un correo" grep -q 'length(var.alert_emails) > 0' <<<"$alert_prod"
expect ok   "alert_emails de dev exige al menos un correo" grep -q 'length(var.alert_emails) > 0' "$ROOT/infra/terraform/envs/dev/main.tf"

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
