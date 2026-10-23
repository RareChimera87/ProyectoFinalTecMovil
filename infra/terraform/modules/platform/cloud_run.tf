locals {
  run_env = {
    ENV                    = var.environment
    GCP_PROJECT            = var.project_id
    GCP_REGION             = var.region
    CLOUD_SQL_INSTANCE     = google_sql_database_instance.main.connection_name
    DB_NAME                = google_sql_database.main.name
    DB_USER                = google_sql_user.api.name
    DB_IAM_AUTH            = "true"
    TASKS_SERVICE_ACCOUNT  = google_service_account.tasks.email
    TASKS_QUEUE_SIM        = google_cloud_tasks_queue.this["simulations"].name
    TASKS_QUEUE_AI_LIGHT   = google_cloud_tasks_queue.this["ai-light"].name
    TASKS_QUEUE_MAINT      = google_cloud_tasks_queue.this["maintenance"].name
    INTERNAL_OIDC_AUDIENCE = local.internal_audience
    # Nombres completos de los secretos; la API lee la versión `latest` con la
    # API de Secret Manager (no se montan para que Cloud Run no falle mientras
    # el secreto no tenga versión).
    SECRET_HMAC_ANON_KEY = google_secret_manager_secret.this["hmac-anon-key"].id
    SECRET_PLAY_SA       = google_secret_manager_secret.this["play-sa"].id
  }

  jobs = {
    "migrate"    = { args = ["migrate"], timeout = "600s" }
    "seed-demo"  = { args = ["seed-demo"], timeout = "900s" }
    "purge-demo" = { args = ["purge-demo"], timeout = "900s" }
  }
}

resource "google_cloud_run_v2_service" "api" {
  project             = var.project_id
  name                = "api"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = var.deletion_protection
  labels              = local.labels
  custom_audiences    = [local.internal_audience]

  template {
    service_account                  = google_service_account.api.email
    timeout                          = "${var.api_timeout_seconds}s"
    max_instance_request_concurrency = var.api_concurrency

    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }

    containers {
      image = var.image
      args  = ["serve"]

      resources {
        limits = {
          cpu    = var.api_cpu
          memory = var.api_memory
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = local.run_env
        content {
          name  = env.key
          value = env.value
        }
      }
    }
  }

  lifecycle {
    # La imagen y la revisión las cambia deploy-dev/deploy-prod (E0.6).
    ignore_changes = [
      template[0].containers[0].image,
      template[0].containers[0].args,
      template[0].revision,
      template[0].labels,
      client,
      client_version,
    ]
  }

  depends_on = [
    google_project_service.apis,
    google_project_iam_member.api,
    google_secret_manager_secret_iam_member.api_accessor,
  ]
}

# La app llama a la API con el ID token de Firebase; la autorización la hace el
# backend. /internal/* exige además OIDC de sa-tasks (verificado en Ktor).
resource "google_cloud_run_v2_service_iam_member" "public" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "tasks_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.tasks.email}"
}

# Tres Cloud Run Jobs con la misma imagen y distinto argumento (ADR-0011, ADR-0027).
resource "google_cloud_run_v2_job" "this" {
  for_each            = local.jobs
  project             = var.project_id
  name                = each.key
  location            = var.region
  deletion_protection = var.deletion_protection
  labels              = local.labels

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account = google_service_account.api.email
      timeout         = each.value.timeout
      max_retries     = 0

      containers {
        image = var.job_placeholder_image
        args  = each.value.args

        resources {
          limits = {
            cpu    = "1"
            memory = "1Gi"
          }
        }

        dynamic "env" {
          for_each = local.run_env
          content {
            name  = env.key
            value = env.value
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].template[0].containers[0].image,
      template[0].labels,
      client,
      client_version,
    ]
  }

  depends_on = [google_project_service.apis, google_project_iam_member.api]
}
