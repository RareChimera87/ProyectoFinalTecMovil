output "project_id" {
  value = local.project_id
}

output "state_bucket" {
  value = google_storage_bucket.state.name
}

output "wif_provider" {
  description = "Valor del secreto de GitHub WIF_PROVIDER (dev) o WIF_PROVIDER_PROD."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "deployer_service_account" {
  description = "Valor de WIF_SERVICE_ACCOUNT_{DEV,PROD}."
  value       = google_service_account.deployer.email
}

output "terraform_service_account" {
  description = "Valor de TF_SERVICE_ACCOUNT_DEV (apply desde la rama dev). Vacío en prod."
  value       = local.is_prod ? "" : google_service_account.terraform[0].email
}

output "terraform_plan_service_account" {
  description = "Valor de TF_PLAN_SERVICE_ACCOUNT_{DEV,PROD} (plan en CI)."
  value       = google_service_account.terraform_plan.email
}

output "api_service_account" {
  description = "sa-api (identidad de Cloud Run api y de los jobs). La lee modules/platform."
  value       = google_service_account.api.email
}

output "tasks_service_account" {
  description = "sa-tasks (OIDC de Tasks, Scheduler y Pub/Sub). La lee modules/platform."
  value       = google_service_account.tasks.email
}
