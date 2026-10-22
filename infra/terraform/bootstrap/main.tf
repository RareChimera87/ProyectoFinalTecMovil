# Bootstrap de un entorno de ParcheU (ADR-0014). Se aplica una vez por proyecto
# con el ADC del usuario: bucket de estado, WIF para GitHub Actions y las cuentas
# de servicio con las que CI despliega (sa-deployer) y ejecuta Terraform
# (sa-terraform).

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

  # dev: CI hace `apply`, así que sa-terraform administra los servicios que usa
  # modules/platform (sin roles/owner ni roles/editor).
  terraform_roles_dev = [
    "roles/serviceusage.serviceUsageAdmin",
    "roles/resourcemanager.projectIamAdmin",
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
  ]

  # prod: CI solo hace `plan`; el `apply` lo ejecuta el usuario con su ADC.
  terraform_roles_prod = [
    "roles/viewer",
    "roles/iam.securityReviewer",
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

resource "google_service_account" "terraform" {
  project      = local.project_id
  account_id   = "sa-terraform"
  display_name = "ParcheU Terraform (GitHub Actions)"
  description  = local.is_prod ? "Plan de Terraform en CI (solo lectura)." : "Plan y apply de Terraform en CI."
  depends_on   = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "deployer" {
  for_each = toset(local.deployer_roles)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "terraform" {
  for_each = toset(local.is_prod ? local.terraform_roles_prod : local.terraform_roles_dev)
  project  = local.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.terraform.email}"
}

# sa-terraform escribe el estado y su lock (también el plan necesita el lock).
resource "google_storage_bucket_iam_member" "terraform_state" {
  bucket = google_storage_bucket.state.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.terraform.email}"
}

# Presupuesto de facturación (modules/platform): dev lo administra, prod solo lo lee.
resource "google_billing_account_iam_member" "terraform_budget" {
  billing_account_id = var.billing_account
  role               = local.is_prod ? "roles/billing.viewer" : "roles/billing.costsManager"
  member             = "serviceAccount:${google_service_account.terraform.email}"
}

resource "google_service_account_iam_member" "deployer_wif" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.deployer_principal
}

resource "google_service_account_iam_member" "terraform_wif" {
  service_account_id = google_service_account.terraform.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.repo_principal
}
