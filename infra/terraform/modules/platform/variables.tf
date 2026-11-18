variable "project_id" {
  description = "ID del proyecto de GCP del entorno."
  type        = string
}

variable "environment" {
  description = "dev o prod."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment debe ser dev o prod."
  }
}

variable "region" {
  description = "Región de todos los recursos regionales (ADR-0020)."
  type        = string
  default     = "us-central1"
}

variable "firestore_location" {
  description = "Ubicación multirregional de Firestore (ADR-0020)."
  type        = string
  default     = "nam5"
}

# --- Cloud Run ---------------------------------------------------------------

variable "image" {
  description = "Imagen inicial de la API y de los jobs. CI la reemplaza en cada deploy (Terraform ignora los cambios de imagen)."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "job_placeholder_image" {
  description = "Imagen inicial de los Cloud Run Jobs hasta el primer deploy."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/job"
}

variable "api_min_instances" {
  description = "Instancias mínimas de la API (dev 0; prod 0 mientras dure el presupuesto de 10 USD/mes, 1 con usuarios reales)."
  type        = number
}

variable "api_max_instances" {
  description = "Instancias máximas de la API (dev 3, prod 2 en el MVP; 5 con usuarios reales)."
  type        = number
}

# Cada instancia de la API abre su propio pool Hikari (DB_MAX_POOL_SIZE en Database.kt/AppConfig.kt). Elígelo para que
# api_max_instances × db_max_pool_size + jobs quepa con holgura en max_connections de Cloud SQL (db-f1-micro: 25).
variable "db_max_pool_size" {
  description = "Conexiones máximas del pool Hikari por instancia de la API (DB_MAX_POOL_SIZE). Entero 1..50; por defecto 10, como el backend."
  type        = number
  default     = 10

  validation {
    condition     = var.db_max_pool_size == floor(var.db_max_pool_size) && var.db_max_pool_size >= 1 && var.db_max_pool_size <= 50
    error_message = "db_max_pool_size debe ser un entero entre 1 y 50 (el backend rechaza otro valor al arrancar)."
  }
}

# Topes diarios de gasto de IA en USD, como texto: el backend los lee de AI_DAILY_BUDGET_USD y
# AI_USER_DAILY_BUDGET_USD con toBigDecimalOrNull (> 0, punto decimal). Los defaults son los de modelos.properties.
variable "ai_daily_budget_usd" {
  description = "Presupuesto diario total de IA en USD (AI_DAILY_BUDGET_USD). Decimal con punto, mayor que 0."
  type        = string
  default     = "3"
}

variable "ai_user_daily_budget_usd" {
  description = "Techo diario de IA por persona en USD (AI_USER_DAILY_BUDGET_USD). Decimal con punto, mayor que 0."
  type        = string
  default     = "0.75"
}

variable "ai_provider" {
  description = "Proveedor de IA de la API (AI_PROVIDER): gemini (Vertex), claude (Claude en Vertex) o anthropic (API directa de Anthropic con la clave del secreto anthropic-api-key)."
  type        = string
  default     = "gemini"

  validation {
    condition     = contains(["gemini", "claude", "anthropic"], var.ai_provider)
    error_message = "ai_provider debe ser gemini, claude o anthropic (fake solo existe con ENV=local)."
  }
}

variable "api_cpu" {
  type    = string
  default = "1"
}

variable "api_memory" {
  type    = string
  default = "1Gi"
}

variable "api_concurrency" {
  type    = number
  default = 40
}

variable "api_timeout_seconds" {
  type    = number
  default = 300
}

variable "deletion_protection" {
  description = "Protección contra borrado de Cloud Run, Cloud SQL y Firestore."
  type        = bool
}

# --- Cloud SQL ---------------------------------------------------------------

variable "sql_tier" {
  description = "Máquina de Cloud SQL (db-f1-micro en dev y, mientras dure el presupuesto de 10 USD/mes, en prod; db-g1-small con usuarios reales)."
  type        = string
}

variable "sql_disk_type" {
  description = "Disco de Cloud SQL: PD_SSD (~0,17 USD/GB-mes) o PD_HDD (~0,09 USD/GB-mes). Cambiarlo recrea la instancia: decídelo antes del primer apply."
  type        = string
  default     = "PD_SSD"

  validation {
    condition     = contains(["PD_SSD", "PD_HDD"], var.sql_disk_type)
    error_message = "sql_disk_type debe ser PD_SSD o PD_HDD."
  }
}

variable "sql_backups_enabled" {
  description = "Backups diarios automáticos."
  type        = bool
}

variable "sql_pitr_enabled" {
  description = "Recuperación a un punto en el tiempo (requiere backups)."
  type        = bool
}

variable "sql_database_name" {
  type    = string
  default = "parcheu"
}

# --- Cloud Tasks (techo de gasto, ADR-0010 y ADR-0019) ----------------------

variable "queue_limits" {
  description = "Tasa máxima por cola. `simulations` es el techo físico de gasto de IA."
  type = map(object({
    max_dispatches_per_second = number
    max_concurrent_dispatches = number
  }))

  validation {
    condition     = alltrue([for q in ["simulations", "ai-light", "maintenance"] : contains(keys(var.queue_limits), q)])
    error_message = "queue_limits debe definir simulations, ai-light y maintenance."
  }
}

# --- Pub/Sub -----------------------------------------------------------------

variable "rtdn_push_enabled" {
  description = "Crea la suscripción push OIDC de play-rtdn hacia /internal/billing/rtdn."
  type        = bool
}

# --- Google Play (E7.1) ------------------------------------------------------

variable "play_service_account_enabled" {
  description = "Crea sa-play (API de Google Play Developer) y deja que sa-api la suplante. Solo en prod."
  type        = bool
  default     = false
}

# --- Usuarios demo (ADR-0027, E4.9) --------------------------

variable "demo_users_enabled" {
  description = "DEMO_USERS_ENABLED de la api y de los jobs: true en dev y durante la prueba cerrada; false en la 1.0."
  type        = bool
  default     = false
}

# --- Monitoreo -------------------------------------------------

variable "scheduler_time_zone" {
  type    = string
  default = "America/Bogota"
}

# --- Observabilidad (E10.5) ----------------------------------------------------

variable "alert_policies_enabled" {
  description = "Crea cinco de las seis políticas de alerta de monitoring.tf (cada condición cuesta ~0,10 USD/mes); la de report_created (reporte de seguridad de un usuario) se crea siempre. false en el MVP de prod: el presupuesto de facturación del bootstrap sigue avisando."
  type        = bool
  default     = true
}

variable "alert_emails" {
  description = "Correos que reciben las alertas de monitoring.tf (los 3 del equipo). Vacío = las políticas se crean sin canal de notificación."
  type        = list(string)
  default     = []
}
