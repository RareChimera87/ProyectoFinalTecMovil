resource "google_sql_database_instance" "main" {
  project             = var.project_id
  name                = "parcheu-${var.environment}"
  region              = var.region
  database_version    = "POSTGRES_16"
  deletion_protection = var.deletion_protection

  settings {
    # db-f1-micro y db-g1-small solo existen en la edición ENTERPRISE.
    edition           = "ENTERPRISE"
    tier              = var.sql_tier
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = 10
    disk_autoresize   = true
    user_labels       = local.labels

    deletion_protection_enabled = var.deletion_protection

    ip_configuration {
      # IP pública sin redes autorizadas: solo se entra con el Cloud SQL
      # Connector (IAM + TLS), nunca por IP.
      ipv4_enabled = true
      ssl_mode     = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = var.sql_backups_enabled
      point_in_time_recovery_enabled = var.sql_pitr_enabled
      start_time                     = "08:00" # 03:00 en Bogotá
      transaction_log_retention_days = var.sql_pitr_enabled ? 7 : null

      dynamic "backup_retention_settings" {
        for_each = var.sql_backups_enabled ? [1] : []
        content {
          retained_backups = 7
        }
      }
    }

    database_flags {
      name  = "cloudsql.iam_authentication"
      value = "on"
    }

    maintenance_window {
      day          = 7 # domingo
      hour         = 9 # 04:00 en Bogotá
      update_track = "stable"
    }

    insights_config {
      query_insights_enabled = true
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_sql_database" "main" {
  project  = var.project_id
  instance = google_sql_database_instance.main.name
  name     = var.sql_database_name
}

# Usuario IAM de Postgres para sa-api (sin contraseña).
resource "google_sql_user" "api" {
  project  = var.project_id
  instance = google_sql_database_instance.main.name
  name     = trimsuffix(data.google_service_account.api.email, ".gserviceaccount.com")
  type     = "CLOUD_IAM_SERVICE_ACCOUNT"
}
