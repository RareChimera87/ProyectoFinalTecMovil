locals {
  base_run_env = {
    ENV                = var.environment
    GCP_PROJECT        = var.project_id
    GCP_REGION         = var.region
    CLOUD_SQL_INSTANCE = google_sql_database_instance.main.connection_name
    DB_NAME            = google_sql_database.main.name
    DB_USER            = google_sql_user.api.name
    DB_IAM_AUTH        = "true"
    # Tope del pool por instancia (los jobs usan min(2, este valor)); ver db_max_pool_size.
    DB_MAX_POOL_SIZE       = tostring(var.db_max_pool_size)
    TASKS_SERVICE_ACCOUNT  = data.google_service_account.tasks.email
    TASKS_QUEUE_SIM        = google_cloud_tasks_queue.this["simulations"].name
    TASKS_QUEUE_AI_LIGHT   = google_cloud_tasks_queue.this["ai-light"].name
    TASKS_QUEUE_MAINT      = google_cloud_tasks_queue.this["maintenance"].name
    INTERNAL_OIDC_AUDIENCE = local.internal_audience
    SERVICE_URL            = local.service_url
    DEMO_USERS_ENABLED     = tostring(var.demo_users_enabled)
    # Topes diarios de gasto de IA (AiConfig.kt): total y por persona, en USD.
    AI_DAILY_BUDGET_USD      = var.ai_daily_budget_usd
    AI_USER_DAILY_BUDGET_USD = var.ai_user_daily_budget_usd
    # Proveedor de IA (AiConfig.kt, ADR-0008): gemini (Vertex), claude (Claude en Vertex) o anthropic (API directa).
    AI_PROVIDER = var.ai_provider
    # Nombres completos de los secretos; la API lee la versión `latest` con la
    # API de Secret Manager (no se montan para que Cloud Run no falle mientras
    # el secreto no tenga versión).
    SECRET_HMAC_ANON_KEY     = google_secret_manager_secret.this["hmac-anon-key"].id
    SECRET_PLAY_SA           = google_secret_manager_secret.this["play-sa"].id
    SECRET_ANTHROPIC_API_KEY = google_secret_manager_secret.this["anthropic-api-key"].id
  }

  # Cuenta que la API suplanta para la API de Play (E7.1, solo prod); sin ella usa SECRET_PLAY_SA.
  play_run_env = var.play_service_account_enabled ? { PLAY_SERVICE_ACCOUNT = google_service_account.play[0].email } : {}

  run_env = merge(local.base_run_env, local.play_run_env)

  jobs = {
    "migrate"    = { args = ["migrate"], timeout = "600s" }
    "seed-demo"  = { args = ["seed-demo"], timeout = "900s" }
    "purge-demo" = { args = ["purge-demo"], timeout = "900s" }
    # Plus manual acotado (enmienda de ADR-0013). El argumento por defecto falla a propósito (falta --email o --uid):
    # se ejecuta con `--args=grant-plus,--email=<correo>,--days=7` (o `revoke-plus,...`), ver docs/operacion.md.
    "grant-plus" = { args = ["grant-plus"], timeout = "300s" }
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
    service_account                  = data.google_service_account.api.email
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
    # SERVICE_URL usa la URL determinista; si Cloud Run no la sirve (p. ej. un nombre
    # demasiado largo), las tareas fallarían al encolar: mejor que falle el apply.
    postcondition {
      condition     = contains(self.urls, local.service_url)
      error_message = "Cloud Run no sirve ${local.service_url} (SERVICE_URL); revisa la URL determinista del servicio api."
    }

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
  member   = "serviceAccount:${data.google_service_account.tasks.email}"
}

# Cuatro Cloud Run Jobs con la misma imagen y distinto argumento (ADR-0011, ADR-0027 y la enmienda de ADR-0013).
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
      service_account = data.google_service_account.api.email
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

  depends_on = [google_project_service.apis]
}
