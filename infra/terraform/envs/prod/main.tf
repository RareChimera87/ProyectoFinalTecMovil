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

# Se queda en "gemini" salvo que se pase -var ai_provider=anthropic (o claude) al aplicar; ver el módulo abajo.
variable "ai_provider" {
  description = "Proveedor de IA de prod (AI_PROVIDER): gemini (predeterminado), claude o anthropic."
  type        = string
  default     = "gemini"
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

  # Costo fijo mínimo viable (4 nov 2026: ~20 USD de créditos y presupuesto de 10 USD/mes). Precios aproximados de
  # us-central1; el detalle y cuándo subir cada valor están en docs/operacion.md ("Costo fijo y presupuesto").
  #
  # Sin instancia mínima: 0 USD en reposo; el costo es el arranque en frío (~5-10 s de la JVM) en la primera
  # petición tras un rato sin tráfico. Con usuarios reales súbelo a 1 (~7-8 USD/mes con cpu_idle).
  api_min_instances = 0
  # Techo de escalado (y de gasto) de la API: con min 0 y cpu_idle solo se paga mientras atiende (MVP: centavos).
  # Súbelo (hasta 5 con usuarios reales) si el p95 o la concurrencia lo piden.
  api_max_instances   = 2
  deletion_protection = true

  # Conexiones a Postgres por instancia. db-f1-micro admite max_connections = 25 (tabla de Cloud SQL por memoria,
  # 0,6 GB; aprox., parte la reserva Cloud SQL para su propio usuario): presupuesto del 80 % ≈ 20.
  #   - Régimen: 2 instancias × 4 + los cuatro jobs a la vez × 2 = 16 ≤ 20 (grant-plus casi nunca corre).
  #   - Despliegue: la revisión vieja y la nueva conviven un momento (hasta 2 + 2 instancias) más migrate:
  #     4 × 4 + 2 = 18 ≤ 20.
  # Con 10 (el valor por defecto) eran 2 × 10 + 2 = 22 en régimen y hasta 42 en un despliegue: por encima de 25.
  # Si subes api_max_instances o cambias sql_tier, rehaz la cuenta (scripts/check-plan.sh la comprueba).
  db_max_pool_size = 4

  # db-f1-micro (núcleo compartido, 0,6 GB): ~7,7 USD/mes encendida todo el mes; con el disco de abajo, ~8,6 USD/mes,
  # casi todo el costo fijo del entorno. Con usuarios reales: db-g1-small (~25 USD/mes) cambiando este valor y aplicando (reinicia la
  # instancia unos minutos).
  sql_tier = "db-f1-micro"
  # 10 GB de HDD (~0,9 USD/mes) en lugar de SSD (~1,7 USD/mes): la base del MVP es pequeña. Cambiar el tipo de disco
  # recrea la instancia, así que con usuarios reales migra con un backup a una instancia nueva en PD_SSD.
  sql_disk_type = "PD_HDD"
  # Backups diarios con 7 copias: centavos al mes con una base pequeña.
  sql_backups_enabled = true
  # PITR apagado: guarda los registros de transacciones (más almacenamiento). Actívalo (true) cuando haya datos de
  # personas reales que no se puedan perder entre dos backups diarios.
  sql_pitr_enabled = false

  queue_limits = {
    "simulations" = {
      max_dispatches_per_second = var.simulations_max_dispatches_per_second
      max_concurrent_dispatches = var.simulations_max_concurrent_dispatches
    }
    "ai-light"    = { max_dispatches_per_second = 5, max_concurrent_dispatches = 10 }
    "maintenance" = { max_dispatches_per_second = 1, max_concurrent_dispatches = 2 }
  }

  rtdn_push_enabled = true

  # MVP: solo la alerta de seguridad report_created (+0,10 USD/mes; se crea siempre). Las otras cinco (~0,50 USD/mes)
  # quedan apagadas: avisa el presupuesto de 10 USD del bootstrap y se revisa a mano el dashboard. Con usuarios
  # reales: true (las alertas llegan a alert_emails).
  alert_policies_enabled = false

  # Topes de IA del MVP: 1 USD/día en total y 0,25 USD/día por persona. Costo máximo de Vertex AI: 1 x 30 = 30 USD en
  # el peor mes (más que el presupuesto de 10 USD; en la práctica son centavos por simulación, ver docs/operacion.md).
  ai_daily_budget_usd      = "1"
  ai_user_daily_budget_usd = "0.25"

  # Proveedor de IA: gemini (Vertex, con los créditos de GCP) por defecto. Para pasar a la API directa de Anthropic
  # (clave en el secreto anthropic-api-key, cargada con ANTHROPIC_KEY_FILE=<ruta> scripts/cargar-secretos.sh prod):
  #   terraform apply -var ai_provider=anthropic
  # y para volver: terraform apply -var ai_provider=gemini. Pasos y reversión rápida en docs/operacion.md
  # ("Cambiar de proveedor de IA"). Con anthropic los datos salen de GCP hacia Anthropic (ADR-0020).
  ai_provider = var.ai_provider

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
