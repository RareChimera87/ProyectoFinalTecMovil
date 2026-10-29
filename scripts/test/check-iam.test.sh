#!/usr/bin/env bash
# Pruebas de scripts/check-iam.sh con planes JSON mínimos embebidos (heredoc).
# No necesita gcloud ni credenciales. Uso: scripts/test/check-iam.test.sh
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$DIR/check-iam.sh"
fallos=0
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

# Plan base limpio: una asignación de sa-api que cumple todo.
cat >"$tmp/base.json" <<'JSON'
{"planned_values":{"root_module":{"resources":[
  {"type":"google_project_iam_member","values":{"role":"roles/cloudsql.client","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com","condition":[]}}
]}}}
JSON

n=0
con() { # con <recurso json>: plan base con un recurso más; devuelve su ruta
  n=$((n + 1)); local out="$tmp/caso$n.json"
  jq --argjson r "$1" '.planned_values.root_module.resources += [$r]' "$tmp/base.json" >"$out"
  echo "$out"
}
caso() { # caso <acepta|rechaza> <descripción> <plan>
  local salida rc
  salida="$("$CHECK" "$3" 2>&1)"; rc=$?
  if [[ "$1" == acepta && $rc -eq 0 ]] || [[ "$1" == rechaza && $rc -eq 1 ]]; then
    echo "ok   $1: $2"
  else
    echo "FALLA debía $1 (rc=$rc): $2"; sed 's/^/     /' <<<"$salida"; fallos=$((fallos + 1))
  fi
}
SA_DEPLOYER='serviceAccount:sa-deployer@parcheu-dev.iam.gserviceaccount.com'
SA_OTRA='serviceAccount:sa-otra@parcheu-dev.iam.gserviceaccount.com'

caso acepta "política limpia" "$tmp/base.json"

caso acepta "allUsers con roles/run.invoker" "$(con '{"type":"google_cloud_run_v2_service_iam_member","values":{"role":"roles/run.invoker","member":"allUsers"}}')"
caso rechaza "allUsers con roles/viewer" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/viewer","member":"allUsers"}}')"
caso rechaza "allAuthenticatedUsers con roles/run.invoker" "$(con '{"type":"google_cloud_run_v2_service_iam_member","values":{"role":"roles/run.invoker","member":"allAuthenticatedUsers"}}')"

caso rechaza "serviceAccountKeyAdmin" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.serviceAccountKeyAdmin","member":"serviceAccount:sa-api@parcheu-dev.iam.gserviceaccount.com"}}')"
caso acepta "serviceAccountTokenCreator para sa-deployer" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"$SA_DEPLOYER\"}}")"
caso rechaza "serviceAccountTokenCreator para otra SA" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountTokenCreator\",\"member\":\"$SA_OTRA\"}}")"
caso acepta "serviceAccountAdmin para sa-terraform (bootstrap)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.serviceAccountAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com"}}')"
caso rechaza "serviceAccountAdmin para sa-deployer" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountAdmin\",\"member\":\"$SA_DEPLOYER\"}}")"
caso rechaza "serviceAccountUser a nivel de proyecto para otra SA" "$(con "{\"type\":\"google_project_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SA_OTRA\"}}")"
caso acepta "serviceAccountUser a nivel de SA (no es de proyecto)" "$(con "{\"type\":\"google_service_account_iam_member\",\"values\":{\"role\":\"roles/iam.serviceAccountUser\",\"member\":\"$SA_OTRA\"}}")"

caso rechaza "projectIamAdmin sin condition" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/resourcemanager.projectIamAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com","condition":[]}}')"
caso acepta "projectIamAdmin con condition" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/resourcemanager.projectIamAdmin","member":"serviceAccount:sa-terraform@parcheu-dev.iam.gserviceaccount.com","condition":[{"title":"solo-roles-de-la-plataforma","expression":"true"}]}}')"

caso rechaza "user: con roles/editor" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/editor","member":"user:persona@ejemplo.com","condition":[]}}')"
caso acepta "user: con roles/viewer" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/viewer","member":"user:persona@ejemplo.com","condition":[]}}')"
caso acepta "user: con otro rol (solo aviso)" "$(con '{"type":"google_project_iam_member","values":{"role":"roles/iam.securityReviewer","member":"user:persona@ejemplo.com","condition":[]}}')"

echo "$((fallos)) fallo(s)"
((fallos == 0)) && echo "check-iam.test: todo en orden" || { echo "check-iam.test: $fallos fallo(s)"; exit 1; }
