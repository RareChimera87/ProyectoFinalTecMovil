# Operación y runbook

Qué hacer cuando suena una alerta de ParcheU (E10.5). Las alertas, métricas y el dashboard viven en
`infra/terraform/modules/platform/monitoring.tf` y llegan por correo a los tres del equipo
(`alert_emails`). Cada correo trae el enlace a este documento. Diseño: `arquitectura.md` §11.

Nombres en Cloud Monitoring (sufijo `-dev` o `-prod`):

| Alerta | Condición | Sección |
|---|---|---|
| `api-5xx-mayor-2pct-*` | más del 2 % de respuestas 5xx en 5 min | [5xx](#api-5xx-mayor-2pct) |
| `api-p95-mayor-2s-*` | p95 de latencia > 2 s durante 5 min | [Latencia](#api-p95-mayor-2s) |
| `ai-budget-exceeded-*` | un log `AiBudgetExceeded` | [Presupuesto de IA](#ai-budget-exceeded) |
| `report-created-*` | un log `report_created` | [Reporte creado](#report-created) |
| `simulaciones-failed-*` | más de 3 `simulation_failed` en 10 min | [Simulaciones fallidas](#simulaciones-failed) |
| `api-health-caido-*` | el uptime check de `/v1/health` falla | [API caída](#api-health-caido) |

## Dónde mirar

- **Dashboard** `ParcheU dev|prod` (Monitoring > Dashboards): peticiones por clase de respuesta, latencia
  p50/p95/p99, simulaciones por estado, costo de IA por día y reportes/rechazos por cuota.
- **Error Reporting** (consola de GCP > Error Reporting): agrupa los `log.error` del backend con su traza.
- **Logs** (Logging > Logs Explorer), filtro base:
  `resource.type="cloud_run_revision" AND resource.labels.service_name="api"`.
  Los logs son JSON: el mensaje está en `jsonPayload.message`, y `traceId`, `route` y `userId` (uuid
  interno, nunca correo) son campos. Para seguir una petición: `jsonPayload.traceId="<id>"`.
- **Crashlytics** (Firebase > Crashlytics): cierres de la app Android con las claves `route` (pantalla
  visible, sin argumentos) y `flavor` (`dev` o `prod`).

Métricas basadas en logs (`logging.googleapis.com/user/...`): `parcheu_report_created`,
`parcheu_ai_budget_exceeded`, `parcheu_simulation_failed`, `parcheu_simulation_done`,
`parcheu_quota_rejected` y `parcheu_ai_cost_micros` (micro-USD sumados de los logs `ai_call`).
Si alguien renombra esos mensajes en el backend, las alertas dejan de disparar; lo protege
`ObservabilityLogsTest`.

## api-5xx-mayor-2pct

1. Dashboard: ¿el pico coincide con un despliegue? Cloud Run > `api` > Revisiones.
2. Error Reporting: ¿qué error nuevo domina? Abre una muestra y copia el `traceId` para ver la petición completa.
3. Causas frecuentes: Cloud SQL caído o sin conexiones (`sql.tf`, `db-f1-micro` en dev se satura con
   la concurrencia 40), migración fallida del último despliegue, Vertex AI con errores, secreto faltante.
4. Si empezó con el último despliegue: vuelve a enrutar el tráfico a la revisión anterior
   (`gcloud run services update-traffic api --to-revisions=<anterior>=100 --region us-central1`) y abre un issue.
5. Cierra cuando el 5xx baje del 2 % durante 15 min.

## api-p95-mayor-2s

1. Dashboard > latencia: ¿es toda la API o una ruta? En Logs Explorer filtra por `jsonPayload.route`.
2. Arranque en frío (dev tiene `min_instances = 0`): un p95 alto aislado en dev es esperable. En prod
   (`min 1`) no lo es.
3. Rutas con IA (`/internal/tasks/*` no cuentan para el usuario, pero `POST /v1/simulations` y los endpoints del
   gemelo sí llaman a Vertex): revisa si Vertex responde lento (`ai_call` en logs) o si hay degradación de
   modelo por presupuesto (≥ 70 %, ver abajo).
4. Cloud SQL: CPU y conexiones en la consola. Si es el cuello, sube `sql_tier` (variable del entorno) y aplica.
5. Si persiste, sube `api_max_instances` o `api_cpu` en `envs/<entorno>/main.tf`.

## ai-budget-exceeded

El presupuesto diario de IA se agotó (ADR-0010): la API responde 503 `AI_BUDGET` con `retryAt` a
medianoche de Bogotá y las simulaciones nuevas quedan FAILED con su cupo devuelto.

1. Dashboard > "Costo de IA por día": ¿gasto legítimo o runaway? Revisa `ai_usage_daily` (por propósito y
   modelo) y los logs `ai_call` (campos `purpose`, `model`, `costMicros`; sin textos).
2. Si es un bucle o abuso: baja el techo de la cola (`simulations_max_dispatches_per_second` y
   `simulations_max_concurrent_dispatches`, variables de `envs/dev`) y aplica; revisa cuotas por usuario.
3. Si es crecimiento legítimo: sube `AI_DAILY_BUDGET_USD` del servicio (Cloud Run > `api` > Variables) o su
   valor en Terraform, y despliega. Recuerda que el presupuesto de facturación de bootstrap (50 % y 90 %)
   es independiente y no corta nada.
4. A partir del 70 % ya se degradó a modelo lite: es la señal previa; no genera alerta.
5. Las simulaciones FAILED por presupuesto no se reintentan solas; las personas pueden volver a pedirlas.

## report-created

Alguien reportó a otra persona o un chat (E8.1). Es un aviso informativo, no una falla.

1. Abre el reporte en Postgres (`reports`, el log trae `reportId`, `kind`, `reason`, `priority`):
   `select * from reports where id = '<reportId>'`. La instantánea del chat está en la fila.
2. `priority = 2` (`SELF_HARM` o `UNDERAGE`): atiende primero y el mismo día. Autolesión: sigue el protocolo de apoyo que acuerde el equipo
   (aún no está escrito en la arquitectura); menor de edad: suspende la cuenta.
3. Resuelve con el script de administración (claim `admin`) y registra la resolución en el mismo reporte.
4. Nunca copies textos de mensajes a canales de chat del equipo ni a issues.

## simulaciones-failed

Más de 3 simulaciones terminaron FAILED en 10 min (log `simulation_failed`, campo `cause`: `INELIGIBLE` o `ERROR`).

1. Logs: filtra `jsonPayload.message="simulation_failed"` y luego el `simulationId` en `simulate:` para ver la razón.
2. Si coincide con `AiBudgetExceeded`: ver esa sección.
3. Errores de Vertex (cuota o disponibilidad del modelo): revisa la cuota de Vertex en la consola y el
   estado del modelo; el motor reintenta cada llamada 3 veces antes de fallar.
4. Cola `simulations`: Cloud Tasks > `simulations` — si hay tareas atascadas, pausa la cola, corrige y reanuda.
5. Cada FAILED devuelve el cupo del usuario; no hace falta compensar.

## api-health-caido

El uptime check de `/v1/health` (cada 5 min, varias regiones) falló.

1. Abre `https://<url de la API>/v1/health` desde tu navegador. Si responde, puede ser un falso positivo de una región.
2. Cloud Run > `api`: ¿hay revisión lista y tráfico al 100 %? ¿La instancia arranca? Mira los logs de arranque.
3. Causas: despliegue roto (vuelve a la revisión anterior, ver 5xx), secreto o variable ausente, certificado,
   cuota de Cloud Run, facturación suspendida del proyecto.
4. Si el servicio está sano pero el check falla, revisa que `google_monitoring_uptime_check_config.api_health`
   apunte al host actual (`terraform plan` debe salir sin cambios).

## Verificar el pipeline de errores en dev

Un error del backend en dev debe aparecer en Error Reporting. Verificación (requiere `parcheu-dev` aplicado):

```bash
# 1. Publica un error con traza en el servicio api (formato de Error Reporting).
gcloud logging write run.googleapis.com%2Fstderr \
  '{"message":"E10.5 error forzado de prueba\n    at forzado(operacion.md)","serviceContext":{"service":"api","version":"e10-5-prueba"},"severity":"ERROR"}' \
  --payload-type=json --severity=ERROR --project parcheu-dev \
  --monitored-resource-type=cloud_run_revision \
  --monitored-resource-labels=service_name=api,revision_name=prueba,location=us-central1,configuration_name=api
# 2. En un minuto abre https://console.cloud.google.com/errors?project=parcheu-dev: debe aparecer
#    "E10.5 error forzado de prueba" en el servicio api.
```

También sirve cualquier `log.error` real del backend (por ejemplo, una petición que dispare el handler de
`Throwable` en `ErrorHandling.kt`). Para Crashlytics: en un build `dev` release (no debug), llama a
`CrashReporting.reporter.recordNonFatal(RuntimeException("prueba"))` o fuerza un cierre y reabre la app; el evento
llega con las claves `route` y `flavor=dev` en unos minutos.

## Apagar el ruido

Para silenciar una alerta mientras se atiende, usa Monitoring > Alerting > Snoozes (no la borres: Terraform la
recrearía). Para cambiar a quién llega, edita `alert_emails` (en `terraform.tfvars` local o `-var`) y aplica.
