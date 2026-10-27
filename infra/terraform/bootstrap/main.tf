# Bootstrap de un entorno de ParcheU (ADR-0014). Se aplica una vez por proyecto
# con el ADC del usuario: bucket de estado, WIF para GitHub Actions y las cuentas
# de servicio con las que CI despliega (sa-deployer) y ejecuta Terraform
# (sa-terraform-plan para `plan`; sa-terraform para `apply` solo en dev) y el
# presupuesto de facturación.

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

  repo_principal = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
  # En prod, sa-deployer solo lo pueden usar jobs del environment `production`
  # de GitHub (que exige la aprobación del usuario).
  deployer_principal = local.is_prod ? "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.environment/production" : local.repo_principal
  # Solo jobs que corren sobre la rama dev (protegida: solo entra por PR aprobado).
  dev_branch_principal = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.ref/refs/heads/dev"
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
  # servicios que usa modules/platform (sin roles/owner ni roles/editor). El rol
  # projectIamAdmin va aparte, con condición (google_project_iam_member.terraform_iam_admin).
  terraform_roles_dev = [
    "roles/serviceusage.serviceUsageAdmin",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.serviceAccountUser",
    "roles/run.admin",
    "roles/artifactregistry.admin",
    "roles/cloudsql.admin",
    "roles/secretmanager.admin",
    "roles/cloudtasks.admin",
    "roles/cloudscheduler.admin",
    "roles/pubsub.admin",
    "roles/datastore.owner",
    "roles/firebase.admin",
    "roles/monitoring.admin",
    # Métricas basadas en logs de monitoring.tf (E10.5).
    "roles/logging.configWriter",
  ]

  # Roles que modules/platform asigna a sa-api: sa-terraform solo puede otorgar
  # estos (condición de projectIamAdmin). Mantener alineado con
  # modules/platform/iam.tf (local.api_project_roles).
  terraform_grantable_roles = [
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
  }

  # Solo tokens emitidos para este repositorio pueden intercambiarse.
  attribute_condition = "assertion.repository == '${var.github_repository}'"

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

# projectIamAdmin sin condición permitiría otorgarse roles/owner. Con la condición
# solo puede otorgar los roles que usa modules/platform.
resource "google_project_iam_member" "terraform_iam_admin" {
  count   = local.is_prod ? 0 : 1
  project = local.project_id
  role    = "roles/resourcemanager.projectIamAdmin"
  member  = "serviceAccount:${google_service_account.terraform[0].email}"

  condition {
    title       = "solo-roles-de-la-plataforma"
    description = "sa-terraform solo otorga los roles de sa-api (nunca owner/editor)."
    expression  = "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly([${join(",", [for r in local.terraform_grantable_roles : "'${r}'"])}])"
  }
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
