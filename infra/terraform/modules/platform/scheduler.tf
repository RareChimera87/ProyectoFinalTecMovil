# Trabajos periódicos (arquitectura.md §12), hora de Bogotá. Son los mismos que programa el
# InProcessScheduler del homeserver (AppContainer.inlineBackground, ADR-0019): todo job nuevo va
# en los dos lados. Cloud Scheduler no baja de un minuto; los barridos de 60 s quedan cada minuto.
locals {
  scheduled_jobs = {
    "communities"         = { schedule = "0 3 * * 1", description = "Recalcula comunidades (lunes 03:00)" }
    "memories"            = { schedule = "0 2 * * *", description = "Extrae memorias pendientes (diario 02:00)" }
    "twin-accuracy"       = { schedule = "0 23 * * 0", description = "Precisión del gemelo (domingos 23:00)" }
    "retention"           = { schedule = "30 3 * * *", description = "Aplica la retención de datos (diario 03:30)" }
    "purge-sweep"         = { schedule = "15 * * * *", description = "Reencola las purgas de cuentas atascadas (cada hora)" }
    "match-sweep"         = { schedule = "* * * * *", description = "Vence tickets y matches y reintenta simulaciones del match (cada minuto)" }
    "scenario-sweep"      = { schedule = "* * * * *", description = "Cierra escenarios vencidos y reencola evaluaciones (cada minuto)" }
    "simulation-recovery" = { schedule = "*/15 * * * *", description = "Reencola simulaciones QUEUED/RUNNING atascadas (cada 15 min)" }
    "moderation-sweep"    = { schedule = "*/15 * * * *", description = "Reencola moderaciones y avisos de ayuda pendientes (cada 15 min)" }
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
    uri         = "${local.service_url}/internal/jobs/${each.key}"

    oidc_token {
      service_account_email = data.google_service_account.tasks.email
      audience              = local.internal_audience
    }
  }

  depends_on = [google_project_service.apis, google_cloud_run_v2_service.api]
}
