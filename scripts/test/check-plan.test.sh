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
expect ok   "prod con sa-play: sa-api suplanta a sa-play (ADR-0013)" "$ROOT/scripts/check-iam.sh" "$FIX/plan-prod-play.json"
expect fail "prod con sa-play: sa-api suplanta a sa-terraform (destino conocido)" "$ROOT/scripts/check-iam.sh" \
  "$(jq '(.. | objects | select(.type? == "google_service_account_iam_member" and .values.role? == "roles/iam.serviceAccountTokenCreator")).values.service_account_id = "projects/parcheu-prod/serviceAccounts/sa-terraform@parcheu-prod.iam.gserviceaccount.com"' "$FIX/plan-prod-play.json" >"$TMP/prod-mut.json"; echo "$TMP/prod-mut.json")"
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
# #105: sa-terraform no recibe estos roles de proyecto, ni siquiera con la condición de sa-api.
SAT='serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com'
EXPR_API="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/cloudsql.client'])"
expect fail "sa-terraform con projectIamAdmin sin condición" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/resourcemanager.projectIamAdmin\",\"member\":\"$SAT\",\"condition\":[]}}")"
expect fail "sa-terraform con projectIamAdmin condicionado" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/resourcemanager.projectIamAdmin\",\"member\":\"$SAT\",\"condition\":[{\"title\":\"t\",\"expression\":\"$EXPR_API\"}]}}")"
expect fail "sa-terraform con serviceAccountAdmin de proyecto" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/iam.serviceAccountAdmin\",\"member\":\"$SAT\"}}")"
expect fail "sa-terraform con serviceAccountUser de proyecto" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_member\",\"name\":\"x\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SAT\"}}")"
expect fail "sa-terraform con serviceAccountAdmin vía binding" "$ROOT/scripts/check-iam.sh" \
  "$(add plan-bootstrap-dev.json "{\"address\":\"x\",\"type\":\"google_project_iam_binding\",\"name\":\"x\",\"values\":{\"role\":\"roles/iam.serviceAccountAdmin\",\"members\":[\"$SAT\"]}}")"

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
# C-M1: sa-terraform no administra cuentas de servicio ni el IAM del proyecto. Cada extracto
# debe existir antes de buscar en él: si el awk no lo encuentra, las negativas pasarían solas.
roles_tf="$(awk '/terraform_roles_dev = \[/{f=1} f{print} f&&/\]/{exit}' "$TF")"
apply_role="$(awk '/"google_project_iam_custom_role" "terraform_apply"/{f=1} f{print} f&&/^}/{exit}' "$TF")"
acts_as="$(block terraform_acts_as_api; block terraform_acts_as_tasks)"
expect ok   "extracto de terraform_roles_dev no vacío" grep -q 'roles/run.admin' <<<"$roles_tf"
expect ok   "extracto de parcheuTerraformApply no vacío" grep -q 'secretmanager.secrets.create' <<<"$apply_role"
expect ok   "extracto de terraform_acts_as no vacío" test -n "$acts_as"
for r in iam.serviceAccountAdmin iam.serviceAccountUser secretmanager.admin datastore.owner firebase.admin logging.configWriter; do
  expect fail "sa-terraform sin roles/$r de proyecto" grep -q "roles/$r" <<<"$roles_tf"
done
expect fail "sa-terraform sin projectIamAdmin" grep -q 'roles/resourcemanager.projectIamAdmin' "$TF"
expect fail "parcheuTerraformApply no lee secretos, documentos ni exporta logs" grep -qE '"(secretmanager\.versions|datastore\.entities|logging\.(sinks|buckets|links|logEntries))\.' <<<"$apply_role"
expect ok   "parcheuTerraformApply administra las métricas de logs" grep -q '"logging.logMetrics.create"' <<<"$apply_role"
expect ok   "sa-terraform tiene actAs sobre sa-api" grep -q 'service_account_id = google_service_account.api.name' <<<"$acts_as"
expect ok   "sa-terraform tiene actAs sobre sa-tasks" grep -q 'service_account_id = google_service_account.tasks.name' <<<"$acts_as"
# Ningún otro enlace serviceAccountUser/TokenCreator apunta a sa-terraform.
otros="$(awk '/^resource /{b=$0; r=""} /role +=/{r=$0} /member +=.*google_service_account.terraform\[0\]/{print b " " r}' "$TF" | grep -E 'serviceAccount(User|TokenCreator|Admin)' | grep -vE '"terraform_acts_as_(api|tasks)"')"
expect ok   "sin otros actAs/TokenCreator para sa-terraform" test -z "$otros"

# C-M7: el claim environment es opcional (jobs sin environment): has() lo cubre.
expect ok   "env_ref usa has(assertion.environment)" grep -q "has(assertion.environment) ? assertion.environment : '-'" "$TF"

# C-M7: sa-play (prod) por WIF, con environment y ref: el conjunto exacto de env_ref.
PLAY="$ROOT/infra/terraform/modules/platform/play.tf"
refs="$(awk '/play_wif_env_refs = /{f=1} f{print} f&&/\]\)/{exit}' "$PLAY" | grep -oE '"[a-z-]+@[^"]+"' | sort | tr '\n' ' ')"
expect ok   "sa-play acepta exactamente play@{dev,main,tag} y play-alpha@{main,tag}" \
  test "$refs" = '"play-alpha@refs/heads/main" "play-alpha@tag" "play@refs/heads/dev" "play@refs/heads/main" "play@tag" '
expect fail "ningún principal usa solo attribute.environment" grep -rq 'attribute.environment/' "$ROOT/infra/terraform/modules" "$ROOT/infra/terraform/bootstrap"

# fix/wif-deployer-dev: los 5 removed {} en cada env (no destruir sa-api/sa-tasks al migrar).
for e in dev prod; do
  for addr in google_service_account.api google_service_account.tasks google_project_iam_member.api \
              google_service_account_iam_member.api_acts_as_tasks google_service_account_iam_member.deployer_acts_as_api; do
    expect ok "envs/$e: removed de module.platform.$addr con destroy = false" \
      grep -qzP "removed \\{\\n  from = module\\.platform\\.${addr//./\\.}\\n  lifecycle \\{\\n    destroy = false" "$ROOT/infra/terraform/envs/$e/main.tf"
  done
done

# C-B5: las alertas siempre tienen destinatario. Se prueba el comportamiento de la validación
# con `terraform plan` (sin recursos; no lee stdin, que colgaba con el wrapper de CI) sobre un directorio que solo tiene la variable (sin providers ni GCP).
PROD="$ROOT/infra/terraform/envs/prod/main.tf"
alert_prod="$(awk '/variable "alert_emails"/{f=1} f{print} f&&/^}/{exit}' "$PROD")"
expect ok   "extracto de alert_emails de prod no vacío" grep -q 'validation' <<<"$alert_prod"
expect fail "alert_emails de prod sin valor por defecto" grep -q 'default' <<<"$alert_prod"
if command -v terraform >/dev/null; then
  for e in dev prod; do
    d="$TMP/alertas-$e"; mkdir -p "$d"
    awk '/^variable "alert_emails"/{f=1} f{print} f&&/^}/{exit}' "$ROOT/infra/terraform/envs/$e/main.tf" >"$d/v.tf"
    # Sin pipe hacia grep -q: con pipefail, el SIGPIPE de terraform falsearía el resultado.
    valida() { local out; out="$(cd "$d" && timeout 120 terraform plan -input=false -lock=false -var "alert_emails=$1" </dev/null 2>&1)"; ! grep -q 'Invalid value for variable' <<<"$out"; }
    expect ok   "$e: alert_emails acepta un correo válido" valida '["equipo@parcheu.co"]'
    expect fail "$e: alert_emails rechaza un correo inválido" valida '["equipo@parcheu"]'
    expect fail "$e: alert_emails rechaza la lista vacía" valida '[]'
  done
else
  echo "  (terraform no instalado: se omiten las pruebas de comportamiento de alert_emails)"
fi

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
