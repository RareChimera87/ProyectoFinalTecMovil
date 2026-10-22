# Entorno prod (parcheu-prod). `apply` SOLO lo ejecuta el usuario con su ADC;
# CI solo hace `plan` (sa-terraform de prod es de solo lectura).

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

variable "billing_account" {
  description = "Cuenta de facturación (TF_VAR_billing_account; secreto BILLING_ACCOUNT_ID en CI)."
  type        = string
}

variable "simulations_max_dispatches_per_second" {
  description = "Techo de gasto de IA: simulaciones despachadas por segundo."
  type        = number
  default     = 2
}

variable "simulations_max_concurrent_dispatches" {
  description = "Techo de gasto de IA: simulaciones en paralelo."
  type        = number
  default     = 10
}

module "platform" {
  source = "../../modules/platform"

  project_id                     = local.project_id
  environment                    = "prod"
  region                         = local.region
  billing_account                = var.billing_account
  deployer_service_account_email = "sa-deployer@${local.project_id}.iam.gserviceaccount.com"

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
  budget_amount_usd = 60
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
