# Cuentas de servicio de mínimo privilegio (arquitectura.md §12). Ninguna tiene
# roles/owner ni roles/editor: lo verifica scripts/check-iam.sh sobre el plan.

resource "google_service_account" "api" {
  project      = var.project_id
  account_id   = "sa-api"
  display_name = "ParcheU API"
  description  = "Identidad de Cloud Run api y de los Cloud Run Jobs (migrate, seed-demo, purge-demo)."
  depends_on   = [google_project_service.apis]
}

resource "google_service_account" "tasks" {
  project      = var.project_id
  account_id   = "sa-tasks"
  display_name = "ParcheU tasks"
  description  = "Identidad OIDC de Cloud Tasks, Cloud Scheduler y Pub/Sub hacia /internal/*."
  depends_on   = [google_project_service.apis]
}

locals {
  api_project_roles = [
    "roles/aiplatform.user",
    "roles/cloudsql.client",
    "roles/cloudsql.instanceUser",
    "roles/cloudtasks.enqueuer",
    "roles/cloudtrace.agent",
    "roles/datastore.user",
    "roles/firebaseauth.admin",
    "roles/firebasecloudmessaging.admin",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
  ]
}

resource "google_project_iam_member" "api" {
  for_each = toset(local.api_project_roles)
  project  = var.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.api.email}"
}

# La API crea tareas con token OIDC de sa-tasks: necesita actAs sobre ella.
resource "google_service_account_iam_member" "api_acts_as_tasks" {
  service_account_id = google_service_account.tasks.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.api.email}"
}

# CI despliega la API y los jobs que corren como sa-api.
resource "google_service_account_iam_member" "deployer_acts_as_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${var.deployer_service_account_email}"
}
