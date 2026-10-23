# Colas de Cloud Tasks (ADR-0019). Mapa de tareas (ver README):
#   simulations ← simulate, judge-scenario
#   ai-light    ← build-persona, extract-memories, moderate, name-community
#   maintenance ← purge-user (sin IA)

resource "google_cloud_tasks_queue" "this" {
  for_each = toset(["simulations", "ai-light", "maintenance"])
  project  = var.project_id
  location = var.region
  name     = each.value

  rate_limits {
    max_dispatches_per_second = var.queue_limits[each.value].max_dispatches_per_second
    max_concurrent_dispatches = var.queue_limits[each.value].max_concurrent_dispatches
  }

  retry_config {
    max_attempts  = 5
    min_backoff   = "10s"
    max_backoff   = "300s"
    max_doublings = 4
  }

  stackdriver_logging_config {
    sampling_ratio = 1.0
  }

  depends_on = [google_project_service.apis]
}
