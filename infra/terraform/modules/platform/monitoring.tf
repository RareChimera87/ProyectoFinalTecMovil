# El presupuesto de facturación (ADR-0010) se crea en bootstrap/ con el ADC del usuario:
# CI no tiene roles sobre la cuenta de facturación compartida.

# Uptime check de /v1/health (las alertas sobre él son de E10.5).
resource "google_monitoring_uptime_check_config" "api_health" {
  project      = var.project_id
  display_name = "api-health-${var.environment}"
  timeout      = "10s"
  period       = "300s"

  http_check {
    path         = "/v1/health"
    port         = 443
    use_ssl      = true
    validate_ssl = true
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = trimprefix(google_cloud_run_v2_service.api.uri, "https://")
    }
  }

  depends_on = [google_project_service.apis]
}
