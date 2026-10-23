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

variable "deployer_service_account_email" {
  description = "Email de sa-deployer (lo crea bootstrap)."
  type        = string
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
  description = "Instancias mínimas de la API (dev 0, prod 1)."
  type        = number
}

variable "api_max_instances" {
  description = "Instancias máximas de la API (dev 3, prod 5)."
  type        = number
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
  description = "Máquina de Cloud SQL (dev db-f1-micro, prod db-g1-small)."
  type        = string
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
