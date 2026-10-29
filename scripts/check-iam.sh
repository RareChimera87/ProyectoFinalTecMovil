#!/usr/bin/env bash
# Verifica el IAM de un plan de Terraform en JSON (terraform show -json plan).
#
# Uso: scripts/check-iam.sh plan.json
#
# Entrada: solo el archivo JSON de `terraform show -json plan`, la forma que este script
# ya usaba. No llama a gcloud ni a GCP, así que las pruebas (scripts/test/check-iam.test.sh)
# corren con planes JSON embebidos y sin credenciales.
#
# ERROR (sale con 1):
#   - roles/owner o roles/editor para cualquier principal (cuenta de servicio, usuario...);
#   - allUsers o allAuthenticatedUsers con cualquier rol, salvo allUsers con PUBLIC_ALLOWED_ROLES;
#   - roles/iam.serviceAccountKeyAdmin o roles/iam.securityAdmin para cualquiera;
#   - roles/iam.serviceAccountTokenCreator o roles/iam.serviceAccountUser (a nivel de
#     proyecto) para una cuenta fuera de EXPECTED_SA_NAMES;
#   - roles/iam.serviceAccountAdmin para una cuenta fuera de SA_ADMIN_EXPECTED_NAMES;
#   - roles/resourcemanager.projectIamAdmin sin condition (permitiría otorgarse owner);
#   - si el plan es de bootstrap: sa-api sin sus roles mínimos, sa-deployer sin los roles de
#     Firebase y Cloud Run, sa-terraform-plan con roles de datos o facturación para cuentas de
#     servicio (ver las secciones 2-4 abajo).
# AVISO (no cambia el código de salida):
#   - principal humano (user:) con cualquier rol distinto de VIEWER_ROLES, para que se decida.
#
# Las listas blancas están al inicio del script; cambiarlas requiere revisión.
set -euo pipefail

PLAN="${1:?Uso: $0 plan.json}"
command -v jq >/dev/null || { echo "Se necesita jq" >&2; exit 2; }

# --- Listas blancas -------------------------------------------------------------------
# Roles que allUsers puede tener. La API de Cloud Run es pública por diseño
# (docs/arquitectura.md §12): run.invoker sobre el servicio `api`.
PUBLIC_ALLOWED_ROLES=(roles/run.invoker)
# Cuentas de servicio esperadas con roles/iam.serviceAccountTokenCreator y
# roles/iam.serviceAccountUser a nivel de proyecto (nombre antes de @).
EXPECTED_SA_NAMES=(sa-terraform sa-deployer)
# Cuentas que pueden tener roles/iam.serviceAccountAdmin: sa-terraform crea las cuentas
# de la plataforma en el bootstrap (ver docs/arquitectura.md §12).
SA_ADMIN_EXPECTED_NAMES=(sa-terraform)
# Roles de solo lectura que no generan aviso para un usuario humano.
VIEWER_ROLES=(roles/viewer roles/logging.viewer roles/monitoring.viewer)

# Todas las asignaciones del plan (raíz y módulos) como: rol, miembro, tipo de recurso,
# y si lleva condition (si/no). Un rol desconocido en el plan se escribe como "-".
bindings() {
  jq -r '
    [.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
      | select(.type | test("^google_.*_iam_(member|binding)$"))
      | . as $r
      | (($r.values.members // []) + [($r.values.member // empty)])[]
      | [($r.values.role // "-"), ., $r.type,
         (if (($r.values.condition // []) | length) > 0 then "si" else "no" end)]
      | @tsv] | unique | .[]' "$PLAN"
}

errors=0
avisos=0
error() { echo "ERROR: $*"; errors=$((errors + 1)); }
aviso() { echo "AVISO: $*"; avisos=$((avisos + 1)); }

en_lista() { # en_lista <valor> <elementos...>: verdadero si el valor está en la lista
  local v="$1" x; shift
  for x in "$@"; do [ "$v" = "$x" ] && return 0; done
  return 1
}
es_sa_de() { # es_sa_de <miembro> <nombres...>: verdadero si el miembro es una de esas cuentas
  local m="$1" n; shift
  for n in "$@"; do [[ "$m" == "serviceAccount:$n@"* ]] && return 0; done
  return 1
}

all="$(bindings)"

# 1-3. Reglas por asignación (cualquier recurso iam_member/iam_binding).
while IFS=$'\t' read -r role member tipo condicion; do
  [ -z "$role" ] && continue
  # Owner y editor: para cualquier principal.
  case "$role" in
    roles/owner|roles/editor) error "$member tiene $role" ;;
  esac
  # Miembros públicos.
  case "$member" in
    allUsers)
      en_lista "$role" "${PUBLIC_ALLOWED_ROLES[@]}" \
        || error "allUsers tiene $role (solo se permite ${PUBLIC_ALLOWED_ROLES[*]})" ;;
    allAuthenticatedUsers) error "allAuthenticatedUsers tiene $role" ;;
  esac
  # Roles de escalada.
  case "$role" in
    roles/iam.serviceAccountKeyAdmin|roles/iam.securityAdmin)
      error "$member tiene $role (escalada)" ;;
    roles/iam.serviceAccountTokenCreator)
      es_sa_de "$member" "${EXPECTED_SA_NAMES[@]}" || error "$member tiene $role (escalada)" ;;
    roles/iam.serviceAccountUser)
      if [[ "$tipo" == google_project_iam_* ]]; then
        es_sa_de "$member" "${EXPECTED_SA_NAMES[@]}" || error "$member tiene $role a nivel de proyecto"
      fi ;;
    roles/iam.serviceAccountAdmin)
      es_sa_de "$member" "${SA_ADMIN_EXPECTED_NAMES[@]}" || error "$member tiene $role (escalada)" ;;
    roles/resourcemanager.projectIamAdmin)
      [ "$condicion" = si ] || error "$member tiene $role sin condition (permitiría otorgarse owner)" ;;
  esac
  # Principales humanos: aviso, no error (owner y editor ya son error arriba).
  case "$member" in
    user:*)
      case "$role" in
        roles/owner|roles/editor) ;;
        *) en_lista "$role" "${VIEWER_ROLES[@]}" || aviso "$member tiene $role; decide si es necesario" ;;
      esac ;;
  esac
done <<<"$all"

has_role() { awk -F'\t' -v r="$1" -v m="$2" '$1==r && $2==m {f=1} END {exit !f}' <<<"$all"; }
creates_sa() {
  jq -e --arg id "$1" '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_service_account" and .values.account_id == $id)] | length > 0' "$PLAN" >/dev/null
}
sa_member() {
  jq -r --arg id "$1" '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_service_account" and .values.account_id == $id)
    | "serviceAccount:\(.values.account_id)@\(.values.project).iam.gserviceaccount.com"][0]' "$PLAN"
}

# 4. sa-api: roles mínimos de arquitectura.md §12.
if creates_sa sa-api; then
  api="$(sa_member sa-api)"
  for r in roles/cloudsql.client roles/cloudsql.instanceUser roles/aiplatform.user \
           roles/cloudtasks.enqueuer roles/datastore.user roles/firebaseauth.admin \
           roles/firebasecloudmessaging.admin; do
    has_role "$r" "$api" || error "$api no tiene $r"
  done
  echo "sa-api: $(grep -cF "$api" <<<"$all") asignaciones"
fi

# 5. sa-deployer (bootstrap): roles para desplegar Firebase (E0.8) y la API.
if creates_sa sa-deployer; then
  dep="$(sa_member sa-deployer)"
  for r in roles/firebaserules.admin roles/datastore.indexAdmin roles/firebasehosting.admin \
           roles/run.admin roles/artifactregistry.writer; do
    has_role "$r" "$dep" || error "$dep no tiene $r"
  done
  echo "sa-deployer: roles de Firebase y Cloud Run presentes"
fi

# 6. Bootstrap: sa-terraform-plan de solo metadatos, sin facturación.
if creates_sa sa-terraform-plan; then
  plan_sa="$(sa_member sa-terraform-plan)"
  for r in roles/viewer roles/editor roles/owner roles/iam.securityReviewer roles/firebase.viewer \
           roles/datastore.viewer roles/datastore.owner roles/logging.viewer roles/logging.privateLogViewer \
           roles/secretmanager.secretAccessor; do
    if has_role "$r" "$plan_sa"; then error "$plan_sa no debe tener $r (da acceso a datos)"; fi
  done
  # El rol personalizado no puede leer entidades, logs ni payloads.
  bad="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_project_iam_custom_role") | .values.permissions[]?
    | select(test("^(datastore\\.entities|datastore\\.backups|logging\\.logEntries|secretmanager\\.versions\\.access|storage\\.objects\\.get|artifactregistry\\.(files|repositories\\.download))"))] | join(",")' "$PLAN")"
  [ -z "$bad" ] || error "el rol personalizado de plan incluye permisos de datos: $bad"
  echo "sa-terraform-plan: solo metadatos"

  # Nada de roles sobre la cuenta de facturación para cuentas de servicio.
  billing="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type | test("^google_billing_account_iam_"))] | length' "$PLAN")"
  [ "$billing" = "0" ] || error "el plan asigna $billing rol(es) sobre la cuenta de facturación; el presupuesto lo crea el usuario"

  # projectIamAdmin siempre con condición (el plan completo trae un bloque `condition`).
  uncond="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
    | select(.type == "google_project_iam_member" and .values.role == "roles/resourcemanager.projectIamAdmin"
             and ((.values.condition // []) | length) == 0)] | length' "$PLAN")"
  [ "$uncond" = "0" ] || error "projectIamAdmin sin condición: permitiría otorgarse roles/owner"
fi

if [ "$errors" -gt 0 ]; then
  echo "check-iam: $errors error(es), $avisos aviso(s)"
  exit 1
fi
echo "check-iam: OK ($avisos aviso(s))"
