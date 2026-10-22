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

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
