# Presupuesto de facturación al 50 % y 90 % (ADR-0010). Avisa por correo a los
# administradores de la cuenta de facturación.
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
      units         = tostring(var.budget_amount_usd)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }

  threshold_rules {
    threshold_percent = 0.9
  }

  depends_on = [google_project_service.apis]
}

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
