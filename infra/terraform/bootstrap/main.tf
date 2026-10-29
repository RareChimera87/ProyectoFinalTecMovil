# Bootstrap de un entorno de ParcheU (ADR-0014). Se aplica una vez por proyecto
# con el ADC del usuario: bucket de estado, WIF para GitHub Actions, las cuentas
# de servicio con las que CI despliega (sa-deployer) y ejecuta Terraform
# (sa-terraform-plan para `plan`; sa-terraform para `apply` solo en dev), las
# identidades de ejecución sa-api y sa-tasks con sus roles (enmienda del 28 oct de
# ADR-0014: así sa-terraform no administra IAM) y el presupuesto de facturación.

locals {
  # IDs reales de los proyectos. Si alguno cambia (sufijo por ID ocupado),
  # actualízalo aquí, en envs/<env>/main.tf y en docs/arquitectura.md §12.
  projects = {
    dev  = "parcheu-dev"
    prod = "parcheu-prod"
  }
  project_id   = local.projects[var.environment]
  region       = "us-central1"
  state_bucket = "${local.project_id}-tfstate"
  is_prod      = var.environment == "prod"

  pool_principal = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}"
  repo_principal = "${local.pool_principal}/attribute.repository/${var.github_repository}"
  # Solo jobs que corren sobre la rama dev (protegida: solo entra por PR aprobado).
  # Un `pull_request` trae ref refs/pull/<n>/merge y una rama cualquiera refs/heads/<rama>.
  dev_branch_principal = "${local.pool_principal}/attribute.ref/refs/heads/dev"
  # sa-deployer:
  #   - dev: solo la rama dev (deploy-dev, smoke-e2e y firebase-deploy corren en push a dev
  #     o workflow_dispatch sobre dev). Antes era todo el repo: un PR podía reescribir las
  #     reglas de Firestore o firmar tokens de Firebase con cualquier claim (C-A3).
  #   - prod: environment `production` (aprobación del usuario) Y ref de tag v* (C-M7), con el
  #     atributo derivado env_ref; un principalSet no admite prefijos.
  deployer_principal = local.is_prod ? "${local.pool_principal}/attribute.env_ref/production@tag" : local.dev_branch_principal
  # sa-terraform-plan: en dev cualquier PR del repo puede hacer `plan` (no hay datos
  # reales); en prod solo la rama dev, porque cualquier PR podría usarla (ADR-0012).
  plan_principal = local.is_prod ? local.dev_branch_principal : local.repo_principal

  budget_usd = local.is_prod ? 60 : 30

  bootstrap_apis = [
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "storage.googleapis.com",
    "cloudbilling.googleapis.com",
    "billingbudgets.googleapis.com",
  ]

  # Despliegue de la app (deploy-dev/deploy-prod y firebase-deploy de E0.8).
  deployer_roles = [
    "roles/run.admin",
    "roles/artifactregistry.writer",
    "roles/firebasehosting.admin",
    "roles/firebaserules.admin",
    "roles/datastore.indexAdmin",
    "roles/serviceusage.serviceUsageConsumer",
  ]

  # dev: CI hace `apply` (solo desde la rama dev), así que sa-terraform administra los
  # servicios que usa modules/platform (sin roles/owner ni roles/editor). No administra
  # IAM de proyecto ni cuentas de servicio (C-M1): sa-api y sa-tasks, sus roles y sus
  # enlaces los crea este bootstrap; sa-terraform solo tiene actAs sobre esas dos
  # (google_service_account_iam_member.terraform_acts_as) y el rol personalizado
  # parcheuTerraformApply para secretos, Firestore y Firebase sin acceso a datos.
  terraform_roles_dev = [
    "roles/serviceusage.serviceUsageAdmin", # habilita las APIs de modules/platform (main.tf)
    "roles/run.admin",                      # servicio api, jobs y su IAM de invocación (cloud_run.tf)
    "roles/artifactregistry.admin",         # repositorio de imágenes y su política de limpieza
    "roles/cloudsql.admin",                 # instancia, base y usuario IAM de sa-api (sql.tf)
    "roles/cloudtasks.admin",               # colas (tasks.tf)
    "roles/cloudscheduler.admin",           # trabajos programados (scheduler.tf)
    "roles/pubsub.admin",                   # tema play-rtdn, su IAM y la suscripción push (pubsub.tf)
    "roles/monitoring.admin",               # uptime, canales, alertas y tablero (monitoring.tf)
    "roles/logging.configWriter",           # métricas basadas en logs (monitoring.tf, E10.5)
  ]

  # Roles de proyecto de sa-api (antes en modules/platform/iam.tf). Viven aquí para que
  # ninguna identidad de CI necesite projectIamAdmin.
  api_project_roles = [
    "roles/aiplatform.user",
    "roles/cloudsql.client",
    "roles/cloudsql.instanceUser",
    "roles/cloudtasks.enqueuer",
    "roles/cloudtrace.agent",
    "roles/datastore.user",
    "roles/firebaseauth.admin",
    "roles/firebasecloudmessaging.admin",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
  ]

  # sa-terraform-plan: solo metadatos. Sin roles/viewer ni securityReviewer ni
  # firebase.viewer, que incluyen datastore.entities.* y logging.logEntries.list
  # (verificado con `gcloud iam roles describe`). Sin roles de facturación: el
  # presupuesto se crea aquí, en el bootstrap, con el ADC del usuario.
  terraform_plan_roles = [
    "roles/run.viewer",
    "roles/secretmanager.viewer",
    "roles/cloudtasks.viewer",
    "roles/cloudscheduler.viewer",
    "roles/pubsub.viewer",
    "roles/monitoring.viewer",
    "roles/iam.serviceAccountViewer",
    "roles/serviceusage.serviceUsageViewer",
    "roles/serviceusage.serviceUsageConsumer",
  ]
}

resource "google_project_service" "bootstrap" {
  for_each           = toset(local.bootstrap_apis)
  project            = local.project_id
  service            = each.value
  disable_on_destroy = false
}

# ---------------------------------------------------------------------------
# Estado de Terraform
# ---------------------------------------------------------------------------

import {
  to = google_storage_bucket.state
  id = "${local.project_id}/${local.state_bucket}"
}

resource "google_storage_bucket" "state" {
  name                        = local.state_bucket
  project                     = local.project_id
  location                    = "US-CENTRAL1"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 20
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

# ---------------------------------------------------------------------------
# Workload Identity Federation (GitHub Actions, sin llaves JSON)
# ---------------------------------------------------------------------------

resource "google_iam_workload_identity_pool" "github" {
  project                   = local.project_id
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
  description               = "Identidades de GitHub Actions del repo ${var.github_repository}"
  depends_on                = [google_project_service.bootstrap]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = local.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-oidc"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository"    = "assertion.repository"
    "attribute.repository_id" = "assertion.repository_id"
    "attribute.ref"           = "assertion.ref"
    "attribute.environment"   = "assertion.environment"
    "attribute.actor"         = "assertion.actor"
    "attribute.workflow"      = "assertion.workflow"
    "attribute.event_name"    = "assertion.event_name"
    "attribute.owner"         = "assertion.repository_owner"
    # Se mapea para auditar; la condición del pool lo exige.
    "attribute.repository_owner_id" = "assertion.repository_owner_id"
    # "<environment>@<ref>", con "tag" en lugar de la ref para refs/tags/v* y "-" sin
    # environment. Permite exigir environment y ref a la vez en un principalSet (que no
    # admite prefijos): sa-deployer de prod usa production@tag; sa-play, play@... (play.tf).
    "attribute.env_ref" = "('environment' in assertion ? assertion.environment : '-') + '@' + (assertion.ref.startsWith('refs/tags/v') ? 'tag' : assertion.ref)"
  }

  # Solo tokens emitidos para este repositorio pueden intercambiarse. Por ID y no por
  # nombre (C-B10): un nombre liberado se puede volver a registrar, un ID no.
  attribute_condition = "assertion.repository_id == '${var.github_repository_id}' && assertion.repository_owner_id == '${var.github_repository_owner_id}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# ---------------------------------------------------------------------------
# Cuentas de servicio de CI
# ---------------------------------------------------------------------------

resource "google_service_account" "deployer" {
  project      = local.project_id
  account_id   = "sa-deployer"
  display_name = "ParcheU deployer (GitHub Actions)"
  description  = "Despliega la API, los jobs y Firebase (reglas, índices, Hosting) desde CI."
  depends_on   = [google_project_service.bootstrap]
}

# Aplica Terraform en dev. No existe en prod (allí aplica el usuario con su ADC).
resource "google_service_account" "terraform" {
  count        = local.is_prod ? 0 : 1
  project      = local.project_id
  account_id   = "sa-terraform"
  display_name = "ParcheU Terraform apply (GitHub Actions)"
  description  = "Apply de Terraform en CI; solo desde la rama dev."
  depends_on   = [google_project_service.bootstrap]
}

# Solo `plan`, de solo lectura de metadatos y sin acceso a datos.
resource "google_service_account" "terraform_plan" {
  project      = local.project_id
  account_id   = "sa-terraform-plan"
  display_name = "ParcheU Terraform plan (GitHub Actions)"
  description  = "Plan de Terraform en CI (solo metadatos, sin datos de Firestore ni logs)."
  depends_on   = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "deployer" {
  for_each = toset(local.deployer_roles)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.deployer.email}"
}

# E10.6: el smoke e2e de deploy-dev crea tokens personalizados de Firebase con el claim `is_test`. Con WIF no hay llave
# privada, así que el Admin SDK los firma con IAM signBlob de la propia cuenta: sa-deployer necesita poder
# crear tokens de sí misma (solo sobre esta cuenta, no sobre el proyecto). En prod no hay smoke automático.
# Desde C-A3 solo la rama dev puede suplantar a sa-deployer, así que un PR ya no llega a este permiso.
resource "google_service_account_iam_member" "deployer_signs_own_tokens" {
  count              = local.is_prod ? 0 : 1
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "terraform" {
  for_each = local.is_prod ? toset([]) : toset(local.terraform_roles_dev)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.terraform[0].email}"
}

# Lo que sa-terraform necesita de Secret Manager, Firestore y Firebase sin los roles
# admin/owner, que incluyen leer valores de secretos (versions.access) y documentos de
# Firestore (datastore.entities.*). Terraform solo crea contenedores de secretos sin valor
# (los valores los carga scripts/cargar-secretos.sh con el ADC de una persona), así que no
# necesita ningún permiso de versiones. setIamPolicy de secretos queda por el
# secretAccessor de sa-api (secrets.tf).
resource "google_project_iam_custom_role" "terraform_apply" {
  count       = local.is_prod ? 0 : 1
  project     = local.project_id
  role_id     = "parcheuTerraformApply"
  title       = "ParcheU Terraform apply (sin datos)"
  description = "Contenedores de secretos, base de Firestore y proyecto de Firebase, sin leer valores ni documentos."
  permissions = [
    "secretmanager.secrets.create",
    "secretmanager.secrets.delete",
    "secretmanager.secrets.get",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.setIamPolicy",
    "datastore.databases.create",
    "datastore.databases.delete",
    "datastore.databases.get",
    "datastore.databases.getMetadata",
    "datastore.databases.list",
    "datastore.databases.update",
    "firebase.projects.get",
    "firebase.projects.update",
    "resourcemanager.projects.get",
  ]
  depends_on = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "terraform_apply_custom" {
  count   = local.is_prod ? 0 : 1
  project = local.project_id
  role    = google_project_iam_custom_role.terraform_apply[0].name
  member  = "serviceAccount:${google_service_account.terraform[0].email}"
}

# ---------------------------------------------------------------------------
# Identidades de ejecución (antes en modules/platform/iam.tf)
# ---------------------------------------------------------------------------

resource "google_service_account" "api" {
  project      = local.project_id
  account_id   = "sa-api"
  display_name = "ParcheU API"
  description  = "Identidad de Cloud Run api y de los Cloud Run Jobs (migrate, seed-demo, purge-demo)."
  depends_on   = [google_project_service.bootstrap]
}

resource "google_service_account" "tasks" {
  project      = local.project_id
  account_id   = "sa-tasks"
  display_name = "ParcheU tasks"
  description  = "Identidad OIDC de Cloud Tasks, Cloud Scheduler y Pub/Sub hacia /internal/*."
  depends_on   = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "api" {
  for_each = toset(local.api_project_roles)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.api.email}"
}

# La API crea tareas con token OIDC de sa-tasks: necesita actAs sobre ella.
resource "google_service_account_iam_member" "api_acts_as_tasks" {
  service_account_id = google_service_account.tasks.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.api.email}"
}

# CI despliega la API y los jobs que corren como sa-api.
resource "google_service_account_iam_member" "deployer_acts_as_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

# sa-terraform (dev) crea recursos que corren como estas cuentas: Cloud Run api y jobs
# (sa-api), Scheduler y la suscripción push de Pub/Sub (sa-tasks). actAs solo sobre ellas,
# no sobre el proyecto: no puede lanzar nada como sa-deployer ni como sí misma.
resource "google_service_account_iam_member" "terraform_acts_as" {
  for_each           = local.is_prod ? {} : { api = google_service_account.api.name, tasks = google_service_account.tasks.name }
  service_account_id = each.value
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.terraform[0].email}"
}

# Lectura de metadatos que ningún rol predefinido ofrece sin datos asociados.
resource "google_project_iam_custom_role" "terraform_plan" {
  project     = local.project_id
  role_id     = "parcheuTerraformPlan"
  title       = "ParcheU Terraform plan (metadatos)"
  description = "Lee metadatos de Firestore, Artifact Registry, Cloud SQL, Firebase e IAM del proyecto. Sin datastore.entities.*, sin logs, sin imágenes."
  permissions = [
    "datastore.databases.get",
    "datastore.databases.getMetadata",
    "datastore.databases.list",
    "artifactregistry.repositories.get",
    "artifactregistry.repositories.list",
    "artifactregistry.repositories.getIamPolicy",
    "cloudsql.instances.get",
    "cloudsql.instances.list",
    "cloudsql.databases.get",
    "cloudsql.databases.list",
    "cloudsql.users.get",
    "cloudsql.users.list",
    "firebase.projects.get",
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
    "pubsub.topics.getIamPolicy",
    # Métricas basadas en logs (E10.5): solo configuración, no entradas de log.
    "logging.logMetrics.get",
    "logging.logMetrics.list",
  ]
  depends_on = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "terraform_plan" {
  for_each = toset(local.terraform_plan_roles)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.terraform_plan.email}"
}

resource "google_project_iam_member" "terraform_plan_custom" {
  project = local.project_id
  role    = google_project_iam_custom_role.terraform_plan.name
  member  = "serviceAccount:${google_service_account.terraform_plan.email}"
}

# sa-terraform escribe el estado y su lock; sa-terraform-plan solo lo lee (el plan
# de CI corre con -lock=false).
resource "google_storage_bucket_iam_member" "terraform_state" {
  count  = local.is_prod ? 0 : 1
  bucket = google_storage_bucket.state.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.terraform[0].email}"
}

resource "google_storage_bucket_iam_member" "terraform_plan_state" {
  bucket = google_storage_bucket.state.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.terraform_plan.email}"
}

# Presupuesto de facturación (ADR-0010). Lo crea el usuario con su ADC: CI no tiene
# ningún rol sobre la cuenta de facturación compartida con otros proyectos.
data "google_project" "this" {
  project_id = local.project_id
}

resource "google_billing_budget" "project" {
  billing_account = var.billing_account
  display_name    = "parcheu-${var.environment}"

  budget_filter {
    projects               = ["projects/${data.google_project.this.number}"]
    credit_types_treatment = "INCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(local.budget_usd)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }

  threshold_rules {
    threshold_percent = 0.9
  }

  depends_on = [google_project_service.bootstrap]
}

resource "google_service_account_iam_member" "deployer_wif" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.deployer_principal
}

resource "google_service_account_iam_member" "terraform_wif" {
  count              = local.is_prod ? 0 : 1
  service_account_id = google_service_account.terraform[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.dev_branch_principal
}

resource "google_service_account_iam_member" "terraform_plan_wif" {
  service_account_id = google_service_account.terraform_plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.plan_principal
}
