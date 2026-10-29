# Contenedores de secretos SIN valor. Los valores los carga
# scripts/cargar-secretos.sh (I1 en dev, el usuario en prod); nunca pasan por
# Terraform ni por el repo. scripts/check-plan.sh falla si aparece secret_data.

locals {
  secrets = toset(["hmac-anon-key", "play-sa"])
}

resource "google_secret_manager_secret" "this" {
  for_each  = local.secrets
  project   = var.project_id
  secret_id = each.value
  labels    = local.labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "api_accessor" {
  for_each  = google_secret_manager_secret.this
  project   = var.project_id
  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_service_account.api.email}"
}
