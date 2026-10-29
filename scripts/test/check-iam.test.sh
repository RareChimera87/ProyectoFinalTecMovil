#!/usr/bin/env bash
# Pruebas de scripts/check-iam.sh con planes JSON mínimos embebidos (heredoc o inline).
# No necesita gcloud ni credenciales. Uso: scripts/test/check-iam.test.sh
# Cada caso comprueba el código de salida y, si se indica, una línea de la salida:
# el patrón es un texto fijo; "!" delante significa que NO debe aparecer.
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$DIR/check-iam.sh"
fallos=0
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

# Plan base limpio: una asignación de sa-api en parcheu-dev (el proyecto se toma de aquí).
cat >"$tmp/base.json" <<'JSON'
{"planned_values":{"root_module":{"resources":[
  {"type":"google_project_iam_member","values":{"project":"parcheu-dev","role":"roles/cloudsql.client","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com","condition":[]}}
]}}}
JSON

n=0
con() { # con <recurso json>: plan base con un recurso más; devuelve su ruta
  n=$((n + 1)); local out="$tmp/caso$n.json"
  jq --argjson r "$1" '.planned_values.root_module.resources += [$r]' "$tmp/base.json" >"$out"
  echo "$out"
}
mk() { # mk <recursos json> [resource_changes json]: plan con esos recursos; devuelve su ruta
  n=$((n + 1)); local out="$tmp/caso$n.json"
  jq -n --argjson r "$1" --argjson c "${2:-[]}" \
    '{planned_values:{root_module:{resources:$r}}, resource_changes:$c}' >"$out"
  echo "$out"
}
caso() { # caso <rc esperado> <descripción> <plan> [patrón]
  local salida rc pat="${4:-}" ok=1
  salida="$("$CHECK" "$3" 2>&1)"; rc=$?
  [ "$rc" = "$1" ] || ok=0
  if [ -n "$pat" ]; then
    if [[ "$pat" == !* ]]; then grep -qF -- "${pat#!}" <<<"$salida" && ok=0
    else grep -qF -- "$pat" <<<"$salida" || ok=0; fi
  fi
  if [ "$ok" = 1 ]; then
    echo "ok   rc=$rc: $2"
  else
    echo "FALLA (rc=$rc, esperaba $1, patrón '${pat:-ninguno}'): $2"; sed 's/^/     /' <<<"$salida"
    fallos=$((fallos + 1))
  fi
}
SA_DEPLOYER='serviceAccount:sa-deployer@parcheu-dev.iam.gserviceaccount.com'
SA_OTRA='serviceAccount:sa-otra@parcheu-dev.iam.gserviceaccount.com'
EXPR_REAL="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/aiplatform.user','roles/cloudsql.client'])"

echo "reglas por asignación"
caso 0 "política limpia" "$tmp/base.json" "!ERROR:"
caso 0 "allUsers con roles/run.invoker" "$(con '{"type":"google_cloud_run_v2_service_iam_member","values":{"role":"roles/run.invoker","member":"allUsers"}}')" "!ERROR:"
caso 1 "allUsers con roles/viewer" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/viewer","member":"allUsers"}}')" "ERROR: allUsers tiene roles/viewer"
caso 1 "allAuthenticatedUsers con roles/run.invoker" "$(con '{"type":"google_cloud_run_v2_service_iam_member","values":{"role":"roles/run.invoker","member":"allAuthenticatedUsers"}}')" "ERROR: allAuthenticatedUsers tiene roles/run.invoker"
caso 1 "serviceAccountKeyAdmin" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.serviceAccountKeyAdmin","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}')" "ERROR: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountKeyAdmin"
caso 1 "roleAdmin" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.roleAdmin","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}')" "ERROR: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.roleAdmin"
caso 0 "serviceAccountTokenCreator para sa-deployer" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"$SA_DEPLOYER\"}}")" "!ERROR:"
caso 1 "serviceAccountTokenCreator para otra SA" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"$SA_OTRA\"}}")" "ERROR: $SA_OTRA tiene roles/iam.serviceAccountTokenCreator"
caso 1 "serviceAccountTokenCreator para sa-terraform de otro proyecto" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.serviceAccountTokenCreator","member":"serviceAccount:sa-terraform@otro-proyecto.iam.gserviceaccount.com"}}')" "ERROR: serviceAccount:sa-terraform@otro-proyecto.iam.gserviceaccount.com tiene roles/iam.serviceAccountTokenCreator"
caso 0 "serviceAccountOpenIdTokenCreator para sa-deployer" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountOpenIdTokenCreator\",\"member\":\"$SA_DEPLOYER\"}}")" "!ERROR:"
caso 1 "serviceAccountOpenIdTokenCreator para otra SA" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountOpenIdTokenCreator\",\"member\":\"$SA_OTRA\"}}")" "ERROR: $SA_OTRA tiene roles/iam.serviceAccountOpenIdTokenCreator"
caso 0 "serviceAccountAdmin para sa-terraform (bootstrap)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.serviceAccountAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com"}}')" "!ERROR:"
caso 1 "serviceAccountAdmin para sa-deployer" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountAdmin\",\"member\":\"$SA_DEPLOYER\"}}")" "ERROR: $SA_DEPLOYER tiene roles/iam.serviceAccountAdmin"
caso 1 "serviceAccountUser a nivel de proyecto para otra SA" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SA_OTRA\"}}")" "ERROR: $SA_OTRA tiene roles/iam.serviceAccountUser a nivel de proyecto"
caso 1 "serviceAccountUser a nivel de cuenta con actor no esperado y destino no resoluble" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SA_OTRA\"}}")" "ERROR: $SA_OTRA tiene roles/iam.serviceAccountUser sobre un destino no resoluble en el plan"
caso 0 "serviceAccountUser a nivel de cuenta con actor esperado y destino no resoluble (aviso)" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SA_DEPLOYER\"}}")" "AVISO: destino no resoluble en el plan: $SA_DEPLOYER tiene roles/iam.serviceAccountUser"

echo "projectIamAdmin"
caso 1 "projectIamAdmin sin condition" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/resourcemanager.projectIamAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com","condition":[]}}')" "ERROR: serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com tiene roles/resourcemanager.projectIamAdmin sin condition"
caso 0 "projectIamAdmin con la condición real (modifiedGrantsByRole)" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/resourcemanager.projectIamAdmin\",\"member\":\"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com\",\"condition\":[{\"title\":\"solo-roles\",\"expression\":\"$EXPR_REAL\"}]}}")" "!ERROR:"
caso 1 "projectIamAdmin con expression \"true\"" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/resourcemanager.projectIamAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com","condition":[{"title":"t","expression":"true"}]}}')" "ERROR: serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com tiene roles/resourcemanager.projectIamAdmin con una condition que no limita"

echo "pares de cuenta (google_service_account_iam_*)"
SA_PLAY='projects/parcheu-dev/serviceAccounts/sa-play@parcheu-dev.iam.gserviceaccount.com'
SA_TERRAFORM='projects/parcheu-dev/serviceAccounts/sa-terraform@parcheu-dev.iam.gserviceaccount.com'
SA_OTRA_CUENTA='projects/parcheu-dev/serviceAccounts/sa-otra@parcheu-dev.iam.gserviceaccount.com'
caso 0 "sa-api suplanta a sa-play a nivel de cuenta (ADR-0013)" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"service_account_id\":\"$SA_PLAY\",\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com\"}}")" "!ERROR:"
caso 1 "sa-api suplanta a sa-terraform a nivel de cuenta" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"service_account_id\":\"$SA_TERRAFORM\",\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com\"}}")" "ERROR: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountTokenCreator sobre sa-terraform@parcheu-dev.iam.gserviceaccount.com: par no esperado"
caso 1 "TokenCreator a nivel de cuenta con destino de otro proyecto" "$(con '{"type":"google_service_account_iam_member","values":{"service_account_id":"projects/otro/serviceAccounts/sa-play@otro.iam.gserviceaccount.com","role":"roles/iam.serviceAccountTokenCreator","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}')" "par no esperado"
caso 0 "TokenCreator a nivel de cuenta con actor esperado y destino no resoluble (aviso)" "$(mk '[{"type":"google_project_iam_member","values":{"project":"parcheu-dev"}},{"type":"google_service_account_iam_member","values":{"role":"roles/iam.serviceAccountTokenCreator","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}]')" "AVISO: destino no resoluble en el plan: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountTokenCreator"
caso 1 "serviceAccountUser a nivel de cuenta sobre una cuenta no esperada" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"service_account_id\":\"$SA_OTRA_CUENTA\",\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com\"}}")" "ERROR: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountUser sobre sa-otra@parcheu-dev.iam.gserviceaccount.com: par no esperado"
DESC_TC_CUENTA='[{"type":"google_service_account_iam_member","address":"google_service_account_iam_member.tc","change":{"actions":["create"],"after":{"role":"roles/iam.serviceAccountTokenCreator","service_account_id":"projects/parcheu-dev/serviceAccounts/sa-play@parcheu-dev.iam.gserviceaccount.com"},"after_unknown":{"member":true}}}]'
caso 1 "TokenCreator a nivel de cuenta con miembro desconocido" "$(mk '[{"type":"google_project_iam_member","values":{"project":"parcheu-dev"}}]' "$DESC_TC_CUENTA")" "ERROR: asignación con valores desconocidos hasta el apply (google_service_account_iam_member.tc)"
DESC_SAU_CUENTA='[{"type":"google_service_account_iam_member","address":"google_service_account_iam_member.su","change":{"actions":["create"],"after":{"role":"roles/iam.serviceAccountUser","service_account_id":"projects/parcheu-dev/serviceAccounts/sa-terraform@parcheu-dev.iam.gserviceaccount.com"},"after_unknown":{"member":true}}}]'
caso 0 "serviceAccountUser a nivel de cuenta con miembro desconocido sobre cuenta esperada (solo aviso)" "$(mk '[{"type":"google_project_iam_member","values":{"project":"parcheu-dev"}}]' "$DESC_SAU_CUENTA")" "AVISO: miembro desconocido hasta el apply sobre una cuenta esperada (google_service_account_iam_member.su)"

echo "destino creado en el mismo plan (configuration.references)"
cat >"$tmp/ref.json" <<'JSON'
{"planned_values":{"root_module":{"resources":[],"child_modules":[{"address":"module.platform","resources":[
  {"address":"module.platform.google_service_account.play[0]","type":"google_service_account","values":{"account_id":"sa-play","project":"parcheu-dev"}},
  {"address":"module.platform.google_service_account_iam_member.api_impersonates_play[0]","type":"google_service_account_iam_member","values":{"role":"roles/iam.serviceAccountTokenCreator","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com","condition":[]}}
]}]}},
 "configuration":{"root_module":{"module_calls":{"platform":{"module":{"resources":[
  {"type":"google_service_account_iam_member","name":"api_impersonates_play","expressions":{"service_account_id":{"references":["google_service_account.play[0].name","google_service_account.play[0]"]}}}
]}}}}}}
JSON
caso 0 "sa-api suplanta a sa-play creado en el mismo plan (referencia resoluble)" "$tmp/ref.json" "!ERROR:"
sed 's/play/terraform/g; s/sa-play/sa-terraform/g' "$tmp/ref.json" >"$tmp/ref-terraform.json"
caso 1 "sa-api suplanta a sa-terraform creado en el mismo plan" "$tmp/ref-terraform.json" "ERROR: serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountTokenCreator sobre sa-terraform@parcheu-dev.iam.gserviceaccount.com: par no esperado"
sed 's/"member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"/"member":"serviceAccount:sa-otra@parcheu-dev.iam.gserviceaccount.com"/' "$tmp/ref.json" >"$tmp/ref-otra.json"
caso 1 "actor no esperado con destino resoluble (sa-play)" "$tmp/ref-otra.json" "ERROR: serviceAccount:sa-otra@parcheu-dev.iam.gserviceaccount.com tiene roles/iam.serviceAccountTokenCreator sobre sa-play@parcheu-dev.iam.gserviceaccount.com: par no esperado"

echo "principales humanos y de grupo"
caso 1 "user: con roles/editor" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/editor","member":"user:persona@ejemplo.com","condition":[]}}')" "ERROR: user:persona@ejemplo.com tiene roles/editor"
caso 0 "user: con roles/viewer (sin aviso)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/viewer","member":"user:persona@ejemplo.com","condition":[]}}')" "!AVISO: user:persona"
caso 0 "user: con otro rol (solo aviso)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.securityReviewer","member":"user:persona@ejemplo.com","condition":[]}}')" "AVISO: user:persona@ejemplo.com tiene roles/iam.securityReviewer"
caso 0 "domain: con roles/storage.admin (solo aviso)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/storage.admin","member":"domain:x.com","condition":[]}}')" "AVISO: domain:x.com tiene roles/storage.admin"
caso 0 "group: con roles/storage.objectAdmin (solo aviso)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/storage.objectAdmin","member":"group:equipo@ejemplo.com","condition":[]}}')" "AVISO: group:equipo@ejemplo.com tiene roles/storage.objectAdmin"

echo "valores desconocidos hasta el apply (resource_changes)"
DESC_KADMIN='[{"type":"google_project_iam_member","values":{"project":"parcheu-dev","role":"roles/iam.serviceAccountKeyAdmin","condition":[]}}]'
DESC_KADMIN_CAMBIO='[{"type":"google_project_iam_member","address":"google_project_iam_member.kadmin","change":{"actions":["create"],"after":{"role":"roles/iam.serviceAccountKeyAdmin"},"after_unknown":{"member":true}}}]'
caso 1 "miembro desconocido con roles/iam.serviceAccountKeyAdmin" "$(mk "$DESC_KADMIN" "$DESC_KADMIN_CAMBIO")" "ERROR: asignación con valores desconocidos hasta el apply (google_project_iam_member.kadmin): rol roles/iam.serviceAccountKeyAdmin, miembro (desconocido)"
DESC_VIEWER_CAMBIO='[{"type":"google_project_iam_member","address":"google_project_iam_member.viewer","change":{"actions":["create"],"after":{"role":"roles/viewer"},"after_unknown":{"member":true}}}]'
caso 0 "miembro desconocido con roles/viewer (solo aviso)" "$(mk '[{"type":"google_project_iam_member","values":{"project":"parcheu-dev"}}]' "$DESC_VIEWER_CAMBIO")" "AVISO: asignación con valores desconocidos hasta el apply (google_project_iam_member.viewer): rol roles/viewer, miembro (desconocido)"
DESC_POL_CAMBIO='[{"type":"google_project_iam_policy","address":"google_project_iam_policy.pol","change":{"actions":["create"],"after":{},"after_unknown":{"policy_data":true}}}]'
caso 0 "política con policy_data desconocido (solo aviso)" "$(mk '[{"type":"google_project_iam_policy","values":{"project":"parcheu-dev"}}]' "$DESC_POL_CAMBIO")" "AVISO: asignación con valores desconocidos hasta el apply (google_project_iam_policy.pol)"

echo "políticas completas (*_iam_policy)"
caso 1 "google_project_iam_policy con allUsers y roles/owner" "$(mk '[{"type":"google_project_iam_policy","values":{"project":"parcheu-dev","policy_data":"{\"bindings\":[{\"role\":\"roles/owner\",\"members\":[\"allUsers\"]}]}"}}]')" "ERROR: allUsers tiene roles/owner"
caso 1 "google_cloud_run_v2_service_iam_policy con allUsers y roles/run.admin" "$(mk '[{"type":"google_cloud_run_v2_service_iam_policy","values":{"project":"parcheu-dev","policy_data":"{\"bindings\":[{\"role\":\"roles/run.admin\",\"members\":[\"allUsers\"]}]}"}}]')" "ERROR: allUsers tiene roles/run.admin"
caso 0 "google_project_iam_policy con sa-deployer como TokenCreator" "$(mk "[{\"type\":\"google_project_iam_policy\",\"values\":{\"project\":\"parcheu-dev\",\"policy_data\":\"{\\\"bindings\\\":[{\\\"role\\\":\\\"roles/iam.serviceAccountTokenCreator\\\",\\\"members\\\":[\\\"$SA_DEPLOYER\\\"]}]}\"}}]")" "!ERROR:"

echo "entrada inválida"
printf '' >"$tmp/vacio.json"
caso 5 "archivo vacío" "$tmp/vacio.json" "no es un plan"
echo '{"foo":1}' >"$tmp/sin_planned.json"
caso 5 "objeto sin planned_values" "$tmp/sin_planned.json" "no es un plan"
echo '{}' >"$tmp/vacio_obj.json"
caso 5 "{} como plan" "$tmp/vacio_obj.json" "no es un plan"
caso 2 "archivo inexistente" "$tmp/no-existe.json" "no se puede leer"

echo "$fallos fallo(s)"
((fallos == 0)) && echo "check-iam.test: todo en orden" || { echo "check-iam.test: $fallos fallo(s)"; exit 1; }
