# Entorno prod (parcheu-prod). `apply` SOLO lo ejecuta el usuario con su ADC;
# CI solo hace `plan` (sa-terraform-plan de prod, solo metadatos, desde la rama dev).

terraform {
  required_version = ">= 1.15.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 7.0"
    }
  }

  backend "gcs" {
    bucket = "parcheu-prod-tfstate"
    prefix = "platform"
  }
}

locals {
  project_id = "parcheu-prod"
  region     = "us-central1"
}

provider "google" {
  project               = local.project_id
  region                = local.region
  billing_project       = local.project_id
  user_project_override = true
}

provider "google-beta" {
  project               = local.project_id
  region                = local.region
  billing_project       = local.project_id
  user_project_override = true
}

variable "simulations_max_dispatches_per_second" {
  description = "Techo de gasto de IA: simulaciones despachadas por segundo."
  type        = number
  default     = 2
}

variable "demo_users_enabled" {
  description = "Usuarios demo (ADR-0027): true solo durante la prueba cerrada; false antes de la 1.0 (y luego purge-demo)."
  type        = bool
  default     = false
}

variable "simulations_max_concurrent_dispatches" {
  description = "Techo de gasto de IA: simulaciones en paralelo."
  type        = number
  default     = 10
}

# Obligatoria: sin correo, las políticas de alerta de prod se crean sin canal y nadie se entera.
# Se pasa con -var o un .tfvars local (el `apply` de prod lo hace solo el usuario).
variable "alert_emails" {
  description = "Correos que reciben las alertas de monitoring.tf (E10.5). Al menos uno; sin valor por defecto."
  type        = list(string)

  validation {
    condition     = length(var.alert_emails) > 0 && alltrue([for e in var.alert_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", e))])
    error_message = "alert_emails necesita al menos un correo válido: sin canal, las alertas no avisan a nadie."
  }
}

module "platform" {
  source = "../../modules/platform"

  project_id   = local.project_id
  environment  = "prod"
  region       = local.region
  alert_emails = var.alert_emails

  api_min_instances   = 1
  api_max_instances   = 5
  deletion_protection = true

  sql_tier            = "db-g1-small"
  sql_backups_enabled = true
  sql_pitr_enabled    = true

  queue_limits = {
    "simulations" = {
      max_dispatches_per_second = var.simulations_max_dispatches_per_second
      max_concurrent_dispatches = var.simulations_max_concurrent_dispatches
    }
    "ai-light"    = { max_dispatches_per_second = 5, max_concurrent_dispatches = 10 }
    "maintenance" = { max_dispatches_per_second = 1, max_concurrent_dispatches = 2 }
  }

  rtdn_push_enabled = true

  # sa-play: verify/RTDN del backend (E7.1) y publicación desde CI (E10.2).
  play_service_account_enabled = true

  demo_users_enabled = var.demo_users_enabled
}

output "api_url" {
  value = module.platform.api_url
}

output "artifact_registry" {
  value = module.platform.artifact_registry
}

output "sql_connection_name" {
  value = module.platform.sql_connection_name
}

output "internal_oidc_audience" {
  value = module.platform.internal_oidc_audience
}

# fix/wif-deployer-dev: sa-api, sa-tasks, los roles de proyecto de sa-api y los enlaces
# entre cuentas pasaron a bootstrap/. Si este estado ya los tenía, se olvidan sin
# destruirlos (el bootstrap los importa; ver docs/pendientes-humanos.md). Sin ellos en el
# estado, estos bloques no hacen nada.
removed {
  from = module.platform.google_service_account.api
  lifecycle {
    destroy = false
  }
}

removed {
  from = module.platform.google_service_account.tasks
  lifecycle {
    destroy = false
  }
}

removed {
  from = module.platform.google_project_iam_member.api
  lifecycle {
    destroy = false
  }
}

removed {
  from = module.platform.google_service_account_iam_member.api_acts_as_tasks
  lifecycle {
    destroy = false
  }
}

removed {
  from = module.platform.google_service_account_iam_member.deployer_acts_as_api
  lifecycle {
    destroy = false
  }
}
