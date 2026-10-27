# El presupuesto de facturación (ADR-0010) se crea en bootstrap/ con el ADC del usuario:
# CI no tiene roles sobre la cuenta de facturación compartida.
#
# Observabilidad (E10.5, arquitectura.md §11): uptime de /v1/health, métricas basadas en logs,
# alertas por correo y un dashboard. Las alertas explican su respuesta en docs/operacion.md.

locals {
  alert_runbook = "Runbook: docs/operacion.md (sección de esta alerta)."
  run_filter    = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${google_cloud_run_v2_service.api.name}\""
  # Peticiones de usuario: se excluye /internal/ (tareas de Cloud Tasks y Scheduler). /internal/tasks/simulate
  # corre la simulación completa de forma síncrona (minutos) y devuelve 5xx a propósito para que Cloud Tasks
  # reintente; medirlas inflaría el p95 y la tasa de 5xx sin que ningún usuario lo note.
  user_requests_filter = "${local.run_filter} AND log_id(\"run.googleapis.com/requests\") AND NOT httpRequest.requestUrl:\"/internal/\""
}

# --- Uptime de /v1/health -------------------------------------------------------
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

# --- Canales de notificación (correo) --------------------------------------------
resource "google_monitoring_notification_channel" "email" {
  for_each     = toset(var.alert_emails)
  project      = var.project_id
  display_name = "alertas-${var.environment}-${each.value}"
  type         = "email"
  labels       = { email_address = each.value }

  depends_on = [google_project_service.apis]
}

locals {
  channels = [for c in google_monitoring_notification_channel.email : c.id]
}

# --- Métricas basadas en logs ------------------------------------------------------
# Los logs del backend son JSON (logback.xml): el mensaje queda en jsonPayload.message.
locals {
  log_events = {
    report_created = {
      description = "Reportes de seguridad creados (SafetyService)."
      filter      = "${local.run_filter} AND jsonPayload.message=\"report_created\""
    }
    ai_budget_exceeded = {
      description = "Llamadas rechazadas porque se agotó el presupuesto diario de IA."
      filter      = "${local.run_filter} AND jsonPayload.message=\"AiBudgetExceeded\""
    }
    simulation_failed = {
      description = "Simulaciones que terminaron en FAILED."
      filter      = "${local.run_filter} AND jsonPayload.message=\"simulation_failed\""
    }
    simulation_done = {
      description = "Simulaciones que terminaron en DONE."
      filter      = "${local.run_filter} AND jsonPayload.message=\"simulation_done\""
    }
    quota_rejected = {
      description = "Peticiones rechazadas por cuota (429)."
      filter      = "${local.run_filter} AND httpRequest.status=429"
    }
    http_requests = {
      description = "Peticiones de usuario (sin /internal/), base de la tasa de 5xx."
      filter      = local.user_requests_filter
    }
    http_5xx = {
      description = "Peticiones de usuario (sin /internal/) que respondieron 5xx."
      filter      = "${local.user_requests_filter} AND httpRequest.status>=500"
    }
  }
}

resource "google_logging_metric" "events" {
  for_each    = local.log_events
  project     = var.project_id
  name        = "parcheu_${each.key}"
  description = each.value.description
  filter      = each.value.filter

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }

  depends_on = [google_project_service.apis]
}

# Costo de IA: suma de costMicros de cada log "ai_call" (micro-USD). Una métrica de logs de tipo contador solo
# cuenta entradas y no admite value_extractor; para sumar un campo hay que declararla como DISTRIBUTION y
# sumar la distribución en MQL (sum_from). Los buckets exponenciales cubren de 1 a 2^63 micro-USD.
resource "google_logging_metric" "ai_cost_micros" {
  project     = var.project_id
  name        = "parcheu_ai_cost_micros"
  description = "Costo de IA en micro-USD de cada log ai_call (distribución; el total es su suma)."
  filter      = "${local.run_filter} AND jsonPayload.message=\"ai_call\""

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
    unit        = "1"
  }
  value_extractor = "EXTRACT(jsonPayload.costMicros)"
  bucket_options {
    exponential_buckets {
      num_finite_buckets = 63
      growth_factor      = 2
      scale              = 1
    }
  }

  depends_on = [google_project_service.apis]
}

# Latencia de las peticiones de usuario (segundos), de los logs de petición de Cloud Run.
resource "google_logging_metric" "http_latency" {
  project     = var.project_id
  name        = "parcheu_http_latency"
  description = "Latencia en segundos de las peticiones de usuario (sin /internal/)."
  filter      = local.user_requests_filter

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
    unit        = "s"
  }
  value_extractor = "EXTRACT(httpRequest.latency)"
  bucket_options {
    exponential_buckets {
      num_finite_buckets = 40
      growth_factor      = 1.5
      scale              = 0.005
    }
  }

  depends_on = [google_project_service.apis]
}

# --- Alertas -----------------------------------------------------------------------
# 5xx > 2 % de las peticiones de usuario en 5 min (sin /internal/, ver user_requests_filter).
resource "google_monitoring_alert_policy" "error_rate" {
  project      = var.project_id
  display_name = "api-5xx-mayor-2pct-${var.environment}"
  combiner     = "OR"

  conditions {
    display_name = "Tasa de 5xx > 2 % (5 min)"
    condition_monitoring_query_language {
      duration = "0s"
      query    = <<-EOT
        { fetch cloud_run_revision
          | metric 'logging.googleapis.com/user/${google_logging_metric.events["http_5xx"].name}'
          | align delta(5m)
          | every 5m
          | group_by [], [errores: sum(val())]
        ; fetch cloud_run_revision
          | metric 'logging.googleapis.com/user/${google_logging_metric.events["http_requests"].name}'
          | align delta(5m)
          | every 5m
          | group_by [], [total: sum(val())]
        }
        | join
        | value [tasa: div(errores, total)]
        | condition tasa > 0.02 '1'
      EOT
    }
  }

  notification_channels = local.channels
  documentation {
    content   = "Más del 2 % de las respuestas de la API son 5xx. ${local.alert_runbook}"
    mime_type = "text/markdown"
  }
  depends_on = [google_project_service.apis]
}

# Latencia p95 > 2 s en 5 min, solo peticiones de usuario (métrica de logs, sin /internal/).
resource "google_monitoring_alert_policy" "latency" {
  project      = var.project_id
  display_name = "api-p95-mayor-2s-${var.environment}"
  combiner     = "OR"

  conditions {
    display_name = "Latencia p95 > 2 s (5 min)"
    condition_threshold {
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.http_latency.name}\" AND resource.type=\"cloud_run_revision\""
      comparison      = "COMPARISON_GT"
      threshold_value = 2
      duration        = "300s"
      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_PERCENTILE_95"
        cross_series_reducer = "REDUCE_MAX"
      }
    }
  }

  notification_channels = local.channels
  documentation {
    content   = "El p95 de latencia de la API pasa de 2 s. ${local.alert_runbook}"
    mime_type = "text/markdown"
  }
  depends_on = [google_project_service.apis]
}

# Alertas por evento: se disparan cuando el conteo de la métrica supera el umbral.
locals {
  event_alerts = {
    ai_budget_exceeded = {
      name      = "ai-budget-exceeded"
      title     = "Presupuesto diario de IA agotado (AiBudgetExceeded)"
      threshold = 0
      window    = "300s"
    }
    report_created = {
      name      = "report-created"
      title     = "Nuevo reporte de seguridad (report_created)"
      threshold = 0
      window    = "300s"
    }
    simulation_failed = {
      name      = "simulaciones-failed"
      title     = "Más de 3 simulaciones FAILED en 10 min"
      threshold = 3
      window    = "600s"
    }
  }
}

resource "google_monitoring_alert_policy" "events" {
  for_each     = local.event_alerts
  project      = var.project_id
  display_name = "${each.value.name}-${var.environment}"
  combiner     = "OR"

  conditions {
    display_name = each.value.title
    condition_threshold {
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.events[each.key].name}\" AND resource.type=\"cloud_run_revision\""
      comparison      = "COMPARISON_GT"
      threshold_value = each.value.threshold
      duration        = "0s"
      aggregations {
        alignment_period     = each.value.window
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }
      trigger { count = 1 }
    }
  }

  notification_channels = local.channels
  documentation {
    content   = "${each.value.title}. ${local.alert_runbook}"
    mime_type = "text/markdown"
  }
  alert_strategy {
    notification_rate_limit { period = "3600s" }
  }
}

# Uptime de /v1/health: falla en 2 comprobaciones seguidas.
resource "google_monitoring_alert_policy" "uptime" {
  project      = var.project_id
  display_name = "api-health-caido-${var.environment}"
  combiner     = "OR"

  conditions {
    display_name = "/v1/health no responde"
    condition_threshold {
      filter          = "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"${google_monitoring_uptime_check_config.api_health.uptime_check_id}\" AND resource.type=\"uptime_url\""
      comparison      = "COMPARISON_GT"
      threshold_value = 1
      duration        = "300s"
      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }
      trigger { count = 1 }
    }
  }

  notification_channels = local.channels
  documentation {
    content   = "El uptime check de /v1/health falla desde varias regiones. ${local.alert_runbook}"
    mime_type = "text/markdown"
  }
  depends_on = [google_project_service.apis]
}

# --- Dashboard -----------------------------------------------------------------------
resource "google_monitoring_dashboard" "api" {
  project = var.project_id
  dashboard_json = jsonencode({
    displayName = "ParcheU ${var.environment}"
    mosaicLayout = {
      columns = 12
      tiles = [
        {
          width = 6, height = 4,
          widget = {
            title = "Peticiones por clase de respuesta"
            xyChart = { dataSets = [{
              plotType = "STACKED_BAR"
              timeSeriesQuery = { timeSeriesFilter = {
                filter = "${local.run_filter} AND metric.type=\"run.googleapis.com/request_count\""
                aggregation = {
                  alignmentPeriod    = "300s"
                  perSeriesAligner   = "ALIGN_RATE"
                  crossSeriesReducer = "REDUCE_SUM"
                  groupByFields      = ["metric.label.response_code_class"]
                }
              } }
            }] }
          }
        },
        {
          xPos = 6, width = 6, height = 4,
          widget = {
            title = "Latencia p50 / p95 / p99 (s, sin /internal/)"
            xyChart = { dataSets = [for p in ["ALIGN_PERCENTILE_50", "ALIGN_PERCENTILE_95", "ALIGN_PERCENTILE_99"] : {
              plotType = "LINE"
              timeSeriesQuery = { timeSeriesFilter = {
                filter = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.http_latency.name}\" AND resource.type=\"cloud_run_revision\""
                aggregation = {
                  alignmentPeriod    = "300s"
                  perSeriesAligner   = p
                  crossSeriesReducer = "REDUCE_MAX"
                }
              } }
            }] }
          }
        },
        {
          yPos = 4, width = 6, height = 4,
          widget = {
            title = "Simulaciones por estado"
            xyChart = { dataSets = [for k in ["simulation_done", "simulation_failed"] : {
              plotType = "STACKED_BAR"
              timeSeriesQuery = { timeSeriesFilter = {
                filter = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.events[k].name}\" AND resource.type=\"cloud_run_revision\""
                aggregation = {
                  alignmentPeriod    = "3600s"
                  perSeriesAligner   = "ALIGN_SUM"
                  crossSeriesReducer = "REDUCE_SUM"
                }
              } }
              legendTemplate = k
            }] }
          }
        },
        {
          xPos = 6, yPos = 4, width = 6, height = 4,
          widget = {
            title = "Costo de IA por día (micro-USD)"
            xyChart = { dataSets = [{
              plotType = "STACKED_BAR"
              timeSeriesQuery = { timeSeriesQueryLanguage = <<-EOT
                fetch cloud_run_revision
                | metric 'logging.googleapis.com/user/${google_logging_metric.ai_cost_micros.name}'
                | align delta(1d)
                | every 1d
                | value [costo: sum_from(val())]
                | group_by [], [costo: sum(costo)]
              EOT
              }
            }] }
          }
        },
        {
          yPos = 8, width = 6, height = 4,
          widget = {
            title = "Reportes creados y rechazos por cuota"
            xyChart = { dataSets = [for k in ["report_created", "quota_rejected"] : {
              plotType = "STACKED_BAR"
              timeSeriesQuery = { timeSeriesFilter = {
                filter = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.events[k].name}\" AND resource.type=\"cloud_run_revision\""
                aggregation = {
                  alignmentPeriod    = "3600s"
                  perSeriesAligner   = "ALIGN_SUM"
                  crossSeriesReducer = "REDUCE_SUM"
                }
              } }
              legendTemplate = k
            }] }
          }
        },
      ]
    }
  })

  depends_on = [google_project_service.apis]
}
