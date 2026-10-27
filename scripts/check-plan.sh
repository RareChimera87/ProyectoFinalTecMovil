#!/usr/bin/env bash
# Verifica un plan de modules/platform en JSON (terraform show -json plan).
#
# Uso: scripts/check-plan.sh plan.json [README]
#   README (por defecto infra/terraform/README.md) debe documentar el mapa de colas.
#
# Comprueba los criterios de E0.5:
#   - los tres Cloud Run Jobs (migrate, seed-demo, purge-demo) con su argumento;
#   - las tres colas con max_dispatches_per_second y max_concurrent_dispatches;
#   - el mapa de tareas → colas documentado en el README;
#   - los secretos hmac-anon-key y play-sa sin valor (ninguna secret_version ni secret_data);
#   - escalado de la API: dev min 0; prod min 1 y máx 5;
#   - Firestore nativo en nam5, Firebase, Pub/Sub play-rtdn, 5 trabajos de Scheduler (con purge-sweep de E8.3),
#     uptime check. El presupuesto (50 % y 90 %) lo crea el bootstrap, no la
#     plataforma; para un plan de bootstrap solo se verifica eso.
set -euo pipefail

PLAN="${1:?Uso: $0 plan.json [README]}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
README="${2:-$ROOT/infra/terraform/README.md}"
command -v jq >/dev/null || { echo "Se necesita jq" >&2; exit 2; }

errors=0
fail() { echo "FALLA: $*" >&2; errors=$((errors + 1)); }
ok() { echo "ok: $*"; }

# Recursos planeados (raíz y módulos) como un arreglo plano.
RES="$(jq -c '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?]' "$PLAN")"
q() { jq -r "$1" <<<"$RES"; }

# --- Plan de bootstrap: solo presupuesto ---------------------------------------
if [ "$(q '[.[] | select(.type=="google_service_account" and .values.account_id=="sa-deployer")] | length')" != "0" ]; then
  thr="$(q '[.[] | select(.type=="google_billing_budget") | .values.threshold_rules[].threshold_percent] | sort | map(tostring) | join(",")')"
  if [ "$thr" = "0.5,0.9" ]; then echo "check-plan (bootstrap): presupuesto al 50 % y 90 %: OK"; exit 0; fi
  echo "FALLA: el presupuesto debe avisar al 50 % y 90 % (tiene '$thr')" >&2; exit 1
fi

# --- Cloud Run Jobs --------------------------------------------------------
for job in migrate seed-demo purge-demo; do
  args="$(q "[.[] | select(.type==\"google_cloud_run_v2_job\" and .values.name==\"$job\")
            | .values.template[0].template[0].containers[0].args] | first | if . == null then \"\" else join(\" \") end")"
  if [ "$args" = "$job" ]; then ok "job $job con argumento '$job'"; else fail "falta el job $job (o su argumento es '$args')"; fi
done
images="$(q '[.[] | select(.type=="google_cloud_run_v2_job") | .values.template[0].template[0].containers[0].image] | unique | length')"
[ "$images" = "1" ] && ok "los jobs usan la misma imagen" || fail "los jobs usan $images imágenes distintas"

# --- Colas -------------------------------------------------------------------
for queue in simulations ai-light maintenance; do
  limits="$(q "[.[] | select(.type==\"google_cloud_tasks_queue\" and .values.name==\"$queue\") | .values.rate_limits[0]] | first")"
  if [ "$limits" = "null" ] || [ -z "$limits" ]; then
    fail "falta la cola $queue"
    continue
  fi
  rate="$(jq -r '.max_dispatches_per_second // empty' <<<"$limits")"
  conc="$(jq -r '.max_concurrent_dispatches // empty' <<<"$limits")"
  if [ -n "$rate" ] && [ -n "$conc" ]; then ok "cola $queue: $rate/s, $conc concurrentes"; else fail "cola $queue sin tasa máxima definida"; fi
done

# --- Mapa de tareas en el README ---------------------------------------------
if [ -f "$README" ]; then
  check_map() {
    local queue="$1"; shift
    local line
    line="$(grep -E "^\|[[:space:]]*\`$queue\`" "$README" || true)"
    [ -n "$line" ] || { fail "el README no documenta la cola $queue"; return; }
    for task in "$@"; do
      grep -qF "\`$task\`" <<<"$line" || fail "el README no asigna $task a la cola $queue"
    done
  }
  check_map simulations simulate judge-scenario
  check_map ai-light build-persona extract-memories moderate name-community
  check_map maintenance purge-user
  ok "mapa de colas revisado en $(basename "$README")"
else
  fail "no existe $README"
fi

# --- Secretos sin valor ------------------------------------------------------
for secret in hmac-anon-key play-sa; do
  n="$(q "[.[] | select(.type==\"google_secret_manager_secret\" and .values.secret_id==\"$secret\")] | length")"
  [ "$n" = "1" ] && ok "secreto $secret (contenedor)" || fail "falta el secreto $secret"
done
versions="$(q '[.[] | select(.type | test("secret_manager_secret_version"))] | length')"
[ "$versions" = "0" ] || fail "el plan crea $versions versión(es) de secreto: los valores no pasan por Terraform"
if jq -e '[.planned_values, .resource_changes] | [.. | objects | select(has("secret_data") or has("secret_data_wo"))] | length > 0' "$PLAN" >/dev/null; then
  fail "el plan contiene secret_data"
else
  ok "ningún secret_data en el plan"
fi

# --- Escalado de la API --------------------------------------------------------
api="$(q '[.[] | select(.type=="google_cloud_run_v2_service" and .values.name=="api")] | first')"
if [ "$api" = "null" ]; then
  fail "falta el servicio Cloud Run api"
else
  env="$(jq -r '.values.labels.env // empty' <<<"$api")"
  min="$(jq -r '.values.template[0].scaling[0].min_instance_count' <<<"$api")"
  max="$(jq -r '.values.template[0].scaling[0].max_instance_count' <<<"$api")"
  case "$env" in
    dev)  [ "$min" = "0" ] && ok "api dev: min $min, máx $max" || fail "api dev debe tener min 0 (tiene $min)" ;;
    prod) { [ "$min" = "1" ] && [ "$max" = "5" ]; } && ok "api prod: min $min, máx $max" || fail "api prod debe tener min 1 y máx 5 (tiene $min/$max)" ;;
    *)    fail "la api no tiene la etiqueta env (dev/prod)" ;;
  esac
fi

# --- Resto de la plataforma -------------------------------------------------------
fs="$(q '[.[] | select(.type=="google_firestore_database") | "\(.values.type) \(.values.location_id)"] | first')"
[ "$fs" = "FIRESTORE_NATIVE nam5" ] && ok "Firestore nativo en nam5" || fail "Firestore debe ser FIRESTORE_NATIVE en nam5 (es '$fs')"
[ "$(q '[.[] | select(.type=="google_firebase_project")] | length')" = "1" ] && ok "google_firebase_project" || fail "falta google_firebase_project"
[ "$(q '[.[] | select(.type=="google_pubsub_topic" and .values.name=="play-rtdn")] | length')" = "1" ] && ok "Pub/Sub play-rtdn" || fail "falta el tema play-rtdn"
sched="$(q '[.[] | select(.type=="google_cloud_scheduler_job") | .values.name] | sort | join(",")')"
[ "$sched" = "communities,memories,purge-sweep,retention,twin-accuracy" ] && ok "Scheduler: $sched" || fail "Scheduler esperado communities,memories,purge-sweep,retention,twin-accuracy (es '$sched')"
[ "$(q '[.[] | select(.type=="google_billing_budget")] | length')" = "0" ] \
  && ok "sin presupuesto en la plataforma (vive en bootstrap)" \
  || fail "el presupuesto no debe estar en la plataforma: CI no tiene roles de facturación (va en bootstrap)"
[ "$(q '[.[] | select(.type=="google_monitoring_uptime_check_config")] | length')" -ge 1 ] && ok "uptime check" || fail "falta el uptime check"
# E10.5: alertas (5xx, p95, AiBudgetExceeded, report_created, simulaciones FAILED, uptime) y dashboard.
[ "$(q '[.[] | select(.type=="google_monitoring_alert_policy")] | length')" -ge 6 ] && ok "políticas de alerta" || fail "faltan políticas de alerta (se esperan 6 o más)"
[ "$(q '[.[] | select(.type=="google_monitoring_dashboard")] | length')" -ge 1 ] && ok "dashboard" || fail "falta el dashboard"
sql="$(q '[.[] | select(.type=="google_sql_database_instance") | .values.database_version] | first')"
[ "$sql" = "POSTGRES_16" ] && ok "Cloud SQL Postgres 16" || fail "Cloud SQL debe ser POSTGRES_16 (es '$sql')"

if [ "$errors" -gt 0 ]; then
  echo "check-plan: $errors problema(s)" >&2
  exit 1
fi
echo "check-plan: OK"
