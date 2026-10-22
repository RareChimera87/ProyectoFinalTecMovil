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
  description = "Valor de TF_SERVICE_ACCOUNT_{DEV,PROD}."
  value       = google_service_account.terraform.email
}
