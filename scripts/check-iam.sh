#!/usr/bin/env bash
# Verifica el IAM de un plan de Terraform en JSON (terraform show -json plan).
#
# Uso: scripts/check-iam.sh plan.json
#
# Entrada: solo el archivo JSON de `terraform show -json plan`, la forma que este script
# ya usaba. No llama a gcloud ni a GCP, así que las pruebas (scripts/test/check-iam.test.sh)
# corren con planes JSON embebidos y sin credenciales. Solo revisa lo que el plan contiene:
# no ve la política real del proyecto.
#
# Lee las asignaciones de planned_values (google_*_iam_member, _iam_binding y _iam_policy,
# estas últimas expandidas desde policy_data) y los valores desconocidos hasta el apply
# (resource_changes[].change.after_unknown).
#
# Códigos de salida: 0 sin errores (puede haber avisos), 1 hay ERROR, 2 uso o archivo ilegible,
# 5 el archivo no es un plan de terraform show -json.
#
# ERROR (sale con 1):
#   - roles/owner o roles/editor para cualquier principal;
#   - allUsers o allAuthenticatedUsers con cualquier rol, salvo allUsers con PUBLIC_ALLOWED_ROLES;
#   - roles/iam.serviceAccountKeyAdmin, securityAdmin o roleAdmin para cualquiera;
#   - roles/iam.serviceAccountTokenCreator, serviceAccountOpenIdTokenCreator o
#     serviceAccountUser a nivel de PROYECTO para una cuenta fuera de EXPECTED_SA_NAMES,
#     comparando el correo completo con PROJECT del plan;
#   - esos mismos roles a nivel de CUENTA (google_service_account_iam_*) sobre una cuenta
#     objetivo cuyo par "actúa>objetivo" no está en EXPECTED_SA_PAIRS. El destino se toma de
#     service_account_id o, si el plan lo deja null porque la cuenta se crea en el mismo plan,
#     de la referencia de configuration (google_service_account.X[i]) resuelta al account_id
#     del plan. Si no se resuelve, ERROR salvo que el actor tenga algún par esperado (AVISO);
#   - roles/iam.serviceAccountAdmin para una cuenta fuera de SA_ADMIN_EXPECTED_NAMES;
#   - roles/resourcemanager.projectIamAdmin sin condition, o con una condition que no contenga
#     modifiedGrantsByRole o que nombre owner, editor o projectIamAdmin. La comprobación es
#     textual (una búsqueda de subcadena en la expresión CEL), no evalúa la expresión;
#   - una asignación de rol de escalada con miembro o rol desconocido hasta el apply;
#   - si el plan es de bootstrap: sa-api sin sus roles mínimos, sa-deployer sin los roles de
#     Firebase y Cloud Run, sa-terraform-plan con roles de datos o de facturación para cuentas
#     de servicio (ver las secciones 4-6 abajo).
# AVISO (no cambia el código de salida):
#   - principal humano (user:, group: o domain:) con cualquier rol distinto de VIEWER_ROLES;
#   - asignación con miembro o rol desconocido hasta el apply, si el rol no es de escalada
#     (o es serviceAccountUser a nivel de cuenta sobre una cuenta esperada);
#   - destino de cuenta no resoluble en el plan, si el actor tiene algún par esperado.
set -euo pipefail

PLAN="${1:?Uso: $0 plan.json}"
[ -r "$PLAN" ] || { echo "ERROR: no se puede leer $PLAN" >&2; exit 2; }
command -v jq >/dev/null || { echo "Se necesita jq" >&2; exit 2; }
jq -e 'type == "object" and has("planned_values")' "$PLAN" >/dev/null 2>&1 \
  || { echo "ERROR: $PLAN no es un plan de terraform show -json (falta planned_values)"; exit 5; }

# --- Listas blancas -------------------------------------------------------------------
# Roles que allUsers puede tener. La API de Cloud Run es pública por diseño
# (docs/arquitectura.md §12): run.invoker sobre el servicio `api`.
PUBLIC_ALLOWED_ROLES=(roles/run.invoker)
# Cuentas de servicio esperadas con roles/iam.serviceAccountTokenCreator,
# roles/iam.serviceAccountOpenIdTokenCreator y roles/iam.serviceAccountUser a nivel de
# proyecto (nombre antes de @; se compara el correo completo del proyecto del plan).
EXPECTED_SA_NAMES=(sa-terraform sa-deployer)
# Cuentas que pueden tener roles/iam.serviceAccountAdmin: sa-terraform crea las cuentas
# de la plataforma en el bootstrap (ver docs/arquitectura.md §12).
SA_ADMIN_EXPECTED_NAMES=(sa-terraform)
# Pares "cuenta_que_actúa>cuenta_objetivo" esperados para TokenCreator, OpenIdTokenCreator
# y serviceAccountUser a nivel de cuenta (google_service_account_iam_*). Cualquier otro par es ERROR.
EXPECTED_SA_PAIRS=(
  "sa-api>sa-play"            # ADR-0013: la API suplanta a sa-play (modules/platform/play.tf, api_impersonates_play)
  "sa-deployer>sa-deployer"   # bootstrap/main.tf, deployer_signs_own_tokens
  "sa-api>sa-tasks"           # serviceAccountUser: identidad OIDC de Tasks (iam.tf, api_acts_as_tasks)
  "sa-deployer>sa-api"        # serviceAccountUser: el deployer despliega la API (iam.tf, deployer_acts_as_api)
)
# Roles de solo lectura que no generan aviso para un principal humano.
VIEWER_ROLES=(roles/viewer roles/logging.viewer roles/monitoring.viewer)
# Roles que cuentan como escalada si el miembro es desconocido hasta el apply.
ESCALATION_ROLES=(roles/owner roles/editor roles/iam.serviceAccountKeyAdmin roles/iam.securityAdmin
  roles/iam.roleAdmin roles/resourcemanager.projectIamAdmin roles/iam.serviceAccountTokenCreator
  roles/iam.serviceAccountOpenIdTokenCreator roles/iam.serviceAccountUser roles/iam.serviceAccountAdmin)

# Proyecto del plan: el del primer recurso con `project` (cuentas de servicio o IAM).
PROJECT="$(jq -r '[.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?
  | select(.type == "google_service_account" or (.type | test("_iam_"))) | .values.project // empty]
  | first // ""' "$PLAN")"
errors=0
avisos=0
error() { echo "ERROR: $*"; errors=$((errors + 1)); }
aviso() { echo "AVISO: $*"; avisos=$((avisos + 1)); }
[ -n "$PROJECT" ] || aviso "no se pudo derivar el proyecto del plan; las cuentas esperadas no se reconocen"

en_lista() { # en_lista <valor> <elementos...>: verdadero si el valor está en la lista
  local v="$1" x; shift
  for x in "$@"; do [ "$v" = "$x" ] && return 0; done
  return 1
}
es_sa_de() { # es_sa_de <miembro> <nombres...>: verdadero si el miembro es exactamente una de esas cuentas del proyecto
  local m="$1" n; shift
  for n in "$@"; do [ "$m" = "serviceAccount:$n@$PROJECT.iam.gserviceaccount.com" ] && return 0; done
  return 1
}

# Filas TSV: rol, miembro, tipo de recurso, estado de la condición (sin|ok|mal), origen, dirección
# y destino (cuenta objetivo "nombre@proyecto" en recursos google_service_account_iam_*; "-" si no aplica).
# "(desconocido)" marca el rol o el miembro que Terraform calcula en el apply.
# Estado de la condición: "sin" si no hay; "ok" si limita a modifiedGrantsByRole y no nombra
# owner, editor ni projectIamAdmin; "mal" en cualquier otro caso.
bindings() {
  jq -r --arg proj "$PROJECT" '
    . as $plan
    | def unk(x): x == true or ((x | type) == "array" and (x | tostring | contains("true")));
      def cn(c):
        (if (c | type) == "object" then [c] else (c // []) end) as $a
        | if ($a | length) == 0 then "sin"
          else ([$a[] | (.expression // "")] | join(" ")) as $e
            | if ($e | contains("modifiedGrantsByRole")) and (($e | test("roles/owner|roles/editor|projectIamAdmin")) | not)
              then "ok" else "mal" end
          end;
      def correo(id): id + "@" + $proj + ".iam.gserviceaccount.com";
      def recursos: [$plan.planned_values.root_module | recurse(.child_modules[]?) | .resources[]?];
      # Destino de una asignación a nivel de cuenta: la referencia de configuration
      # (google_service_account.X[i]) se resuelve al account_id del recurso del plan.
      # Sin configuración o sin referencia resoluble, "(desconocido)".
      def destino_resuelto(addr):
        (addr // "") as $a
        | if $a == "" then "(desconocido)"
          else
            ($a | capture("^(?<p>(module\\.[^.]+\\.)*)(?<r>.*)$")) as $c
            | ($c.r | sub("\\[[^]]*\\]$"; "")) as $sinidx
            | ($sinidx | split(".")[0]) as $tipo
            | ($sinidx | split(".")[1]) as $nombre
            | ([$c.p | scan("module\\.([^.]+)\\.") | .[0]]) as $mods
            | (reduce $mods[] as $m ($plan.configuration.root_module; .module_calls[$m].module)) as $cm
            | ([($cm.resources // [])[] | select(.type == $tipo and .name == $nombre)][0].expressions.service_account_id.references // []) as $refs
            | ([ $refs[] | select(test("^google_[a-z0-9_]+\\.[^.]+"))
                 | capture("^(?<t>google_[a-z0-9_]+)\\.(?<n>[^.]+)") | ($c.p + .t + "." + .n) ]) as $dirs
            | ([ $dirs[] as $d | recursos[] | select(.address == $d) | .values.account_id ][0]) as $id
            | if $id == null then "(desconocido)" else correo($id) end
          end;
      def destino_de(addr; sid):
        if sid != null then (sid | sub("^.*/serviceAccounts/"; "")) else destino_resuelto(addr) end;
      def filas_plan:
        [recursos[] | select(.type | test("_iam_(member|binding|policy)$")) | . as $r
         | ($r.address // $r.type) as $dir
         | (if ($r.type | startswith("google_service_account_iam_"))
            then destino_de($r.address; $r.values.service_account_id) else "-" end) as $dest
         | if ($r.type | endswith("_iam_policy")) then
             (($r.values.policy_data // "{}") | fromjson | (.bindings // []))[]
             | . as $b | ($b.members // [])[]
             | [($b.role // "(desconocido)"), ., $r.type, cn($b.condition), "plan", $dir, $dest] | @tsv
           else
             ((($r.values.members // []) + [($r.values.member // empty)])[]) as $m
             | [($r.values.role // "(desconocido)"), $m, $r.type, cn($r.values.condition), "plan", $dir, $dest] | @tsv
           end];
      def filas_desconocidas:
        [.resource_changes[]? | select(.type | test("_iam_(member|binding|policy)$")) | . as $rc
         | select(($rc.change.actions // []) | any(. == "create" or . == "update"))
         | ($rc.change.after_unknown // {}) as $u | ($rc.change.after // {}) as $a
         | select(unk($u.member) or unk($u.members) or unk($u.role) or unk($u.policy_data))
         | (if unk($u.role) then "(desconocido)" else ($a.role // "(desconocido)") end) as $rol
         | (if ($rc.type | endswith("_iam_policy")) or unk($u.member) or unk($u.members) or unk($u.policy_data)
            then ["(desconocido)"]
            else ([($a.member // empty)] + ($a.members // [])) | if length == 0 then ["(desconocido)"] else . end
            end)[] as $m
         | (if ($rc.type | startswith("google_service_account_iam_"))
            then (if unk($u.service_account_id) then "(desconocido)"
                  else destino_de($rc.address; $a.service_account_id) end)
            else "-" end) as $dest
         | [$rol, $m, $rc.type, "sin", "desconocido", ($rc.address // $rc.type), $dest] | @tsv];
      [filas_plan, filas_desconocidas] | add | unique | .[]' "$PLAN"
}

en_par() { # en_par <miembro> <destino>: verdadero si el par está en EXPECTED_SA_PAIRS y ambos son del proyecto
  local m="${1#serviceAccount:}" t="$2" p
  m="${m%@*}"; t="${t%@*}"
  [ "$1" = "serviceAccount:$m@$PROJECT.iam.gserviceaccount.com" ] || return 1
  [ "$2" = "$t@$PROJECT.iam.gserviceaccount.com" ] || return 1
  for p in "${EXPECTED_SA_PAIRS[@]}"; do [ "$p" = "$m>$t" ] && return 0; done
  return 1
}

es_actor_de_par() { # es_actor_de_par <miembro>: verdadero si el actor aparece en algún par esperado
  local a="${1#serviceAccount:}" p; a="${a%@*}"
  for p in "${EXPECTED_SA_PAIRS[@]}"; do [ "${p%%>*}" = "$a" ] && return 0; done
  return 1
}

all="$(bindings)"

# 1-3. Reglas por asignación (planned_values y valores desconocidos).
while IFS=$'\t' read -r role member tipo estado origen addr destino; do
  [ -z "$role" ] && continue
  cuenta=no; [[ "$tipo" == google_service_account_iam_* ]] && cuenta=si
  destino_nombre="${destino%@*}"
  # Valores desconocidos hasta el apply.
  if [ "$role" = "(desconocido)" ] || [ "$member" = "(desconocido)" ]; then
    if [ "$member" = "(desconocido)" ] && [ "$role" = roles/iam.serviceAccountUser ] \
       && [ "$cuenta" = si ] && en_lista "$destino_nombre" "${EXPECTED_SA_NAMES[@]}"; then
      aviso "miembro desconocido hasta el apply sobre una cuenta esperada ($addr): $member puede actuar como $destino"
    elif en_lista "$role" "${ESCALATION_ROLES[@]}"; then
      error "asignación con valores desconocidos hasta el apply ($addr): rol $role, miembro $member, destino $destino"
    else
      aviso "asignación con valores desconocidos hasta el apply ($addr): rol $role, miembro $member, destino $destino"
    fi
    continue
  fi
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
    roles/iam.serviceAccountKeyAdmin|roles/iam.securityAdmin|roles/iam.roleAdmin)
      error "$member tiene $role (escalada)" ;;
    roles/iam.serviceAccountTokenCreator|roles/iam.serviceAccountOpenIdTokenCreator|roles/iam.serviceAccountUser)
      if [ "$cuenta" = si ]; then
        # A nivel de cuenta: el par debe estar en EXPECTED_SA_PAIRS.
        if [ "$destino" = "(desconocido)" ]; then
          # Destino no resoluble en el plan: si el actor tiene algún par esperado es AVISO; si no, ERROR.
          if es_actor_de_par "$member"; then
            aviso "destino no resoluble en el plan: $member tiene $role ($addr; ver EXPECTED_SA_PAIRS)"
          else
            error "$member tiene $role sobre un destino no resoluble en el plan ($addr): par no esperado"
          fi
        else
          en_par "$member" "$destino" || error "$member tiene $role sobre $destino: par no esperado (ver EXPECTED_SA_PAIRS)"
        fi
      elif [ "$role" = roles/iam.serviceAccountUser ]; then
        # A nivel de proyecto: solo cuentas esperadas.
        es_sa_de "$member" "${EXPECTED_SA_NAMES[@]}" || error "$member tiene $role a nivel de proyecto"
      else
        es_sa_de "$member" "${EXPECTED_SA_NAMES[@]}" || error "$member tiene $role (escalada)"
      fi ;;
    roles/iam.serviceAccountAdmin)
      es_sa_de "$member" "${SA_ADMIN_EXPECTED_NAMES[@]}" || error "$member tiene $role (escalada)" ;;
    roles/resourcemanager.projectIamAdmin)
      case "$estado" in
        ok) ;;
        sin) error "$member tiene $role sin condition (permitiría otorgarse owner)" ;;
        *) error "$member tiene $role con una condition que no limita a modifiedGrantsByRole (permitiría otorgarse owner)" ;;
      esac ;;
  esac
  # Principales humanos o de grupo: aviso, no error (owner y editor ya son error arriba).
  case "$member" in
    user:*|group:*|domain:*)
      case "$role" in
        roles/owner|roles/editor) ;;
        *) en_lista "$role" "${VIEWER_ROLES[@]}" || aviso "$member tiene $role; decide si es necesario" ;;
      esac ;;
  esac
done <<<"$all"

has_role() { awk -F'\t' -v r="$1" -v m="$2" '$1 == r && $2 == m {f = 1} END {exit !f}' <<<"$all"; }
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

# 6. Bootstrap: sa-terraform-plan de solo metadatos, sin facturación. (projectIamAdmin con
# condición se comprueba arriba, en la regla por asignación.)
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
fi

if [ "$errors" -gt 0 ]; then
  echo "check-iam: $errors error(es), $avisos aviso(s)"
  exit 1
fi
echo "check-iam: OK ($avisos aviso(s))"
