# Trabajos periódicos (arquitectura.md §12), hora de Bogotá.
locals {
  scheduled_jobs = {
    "communities"   = { schedule = "0 3 * * 1", description = "Recalcula comunidades (lunes 03:00)" }
    "memories"      = { schedule = "0 2 * * *", description = "Extrae memorias pendientes (diario 02:00)" }
    "twin-accuracy" = { schedule = "0 23 * * 0", description = "Precisión del gemelo (domingos 23:00)" }
    "retention"     = { schedule = "30 3 * * *", description = "Aplica la retención de datos (diario 03:30)" }
  }
}

resource "google_cloud_scheduler_job" "this" {
  for_each         = local.scheduled_jobs
  project          = var.project_id
  region           = var.region
  name             = each.key
  description      = each.value.description
  schedule         = each.value.schedule
  time_zone        = var.scheduler_time_zone
  attempt_deadline = "320s"

  retry_config {
    retry_count = 1
  }

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.api.uri}/internal/jobs/${each.key}"

    oidc_token {
      service_account_email = google_service_account.tasks.email
      audience              = local.internal_audience
    }
  }

  depends_on = [google_project_service.apis]
}
