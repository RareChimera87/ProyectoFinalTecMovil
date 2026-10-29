# RTDN de Google Play (ADR-0013).
resource "google_pubsub_topic" "play_rtdn" {
  project = var.project_id
  name    = "play-rtdn"
  labels  = local.labels

  message_retention_duration = "604800s"

  depends_on = [google_project_service.apis]
}

# Google Play publica las notificaciones con esta cuenta de sistema.
resource "google_pubsub_topic_iam_member" "play_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.play_rtdn.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:google-play-developer-notifications@system.gserviceaccount.com"
}

resource "google_pubsub_subscription" "play_rtdn_push" {
  count   = var.rtdn_push_enabled ? 1 : 0
  project = var.project_id
  name    = "play-rtdn-push"
  topic   = google_pubsub_topic.play_rtdn.id
  labels  = local.labels

  ack_deadline_seconds       = 60
  message_retention_duration = "604800s"

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.api.uri}/internal/billing/rtdn"

    oidc_token {
      service_account_email = data.google_service_account.tasks.email
      audience              = local.internal_audience
    }
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  expiration_policy {
    ttl = ""
  }
}
