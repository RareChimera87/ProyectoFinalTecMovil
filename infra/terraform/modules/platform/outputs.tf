output "api_url" {
  value = google_cloud_run_v2_service.api.uri
}

output "api_service_account" {
  value = google_service_account.api.email
}

output "tasks_service_account" {
  value = google_service_account.tasks.email
}

output "artifact_registry" {
  description = "Prefijo de las imágenes: <region>-docker.pkg.dev/<proyecto>/parcheu"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}

output "sql_connection_name" {
  value = google_sql_database_instance.main.connection_name
}

output "internal_oidc_audience" {
  value = local.internal_audience
}

output "queues" {
  value = { for k, q in google_cloud_tasks_queue.this : k => q.id }
}
