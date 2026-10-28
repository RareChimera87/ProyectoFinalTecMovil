# Operación y runbook

Cómo operar ParcheU: qué hacer cuando suena una alerta (E10.5, entornos en GCP) y el día a día del backend de dev
en el homeserver (E10.7). Contenido: [alertas](#alertas-de-gcp) · [backend de dev en el homeserver](#backend-de-dev-en-el-homeserver) ·
[moderación y administración](#moderación-y-administración) · [datos: respaldo, purga y borrado](#datos-respaldo-purga-y-borrado) ·
[cambios de configuración y secretos](#cambios-de-configuración-y-secretos) · [incidentes frecuentes](#incidentes-frecuentes-por-síntoma).
Diagramas en [c4.md](c4.md).

## Alertas de GCP

Qué hacer cuando suena una alerta de ParcheU (E10.5). Las alertas, métricas y el dashboard viven en
`infra/terraform/modules/platform/monitoring.tf` y llegan por correo a los tres del equipo
(`alert_emails`). Cada correo trae el enlace a este documento. Las alertas solo cubren entornos desplegados
en GCP; el backend de dev en el homelab (ADR-0033) no queda cubierto (allí: `docker compose logs api`). Diseño: `arquitectura.md` §11.

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
`parcheu_quota_rejected`, `parcheu_http_requests` y `parcheu_http_5xx` (peticiones de usuario, sin `/internal/`),
`parcheu_http_latency` (distribución, segundos) y `parcheu_ai_cost_micros` (distribución del costo de cada log
`ai_call`; el total diario es su suma). Las tareas internas (`/internal/tasks/simulate` corre la simulación
completa en minutos y devuelve 5xx para que Cloud Tasks reintente) quedan fuera de la latencia y de los 5xx.
Si alguien renombra esos mensajes en el backend, las alertas dejan de disparar; lo protege
`ObservabilityLogsTest` (incluye `simulation_failed` con `cause` ERROR e INELIGIBLE y `simulation_done`).

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

Un error del backend en dev debe aparecer en Error Reporting (requiere `parcheu-dev` aplicado).

**Alcance (ADR-0033):** mientras el backend de dev corra en el homelab, esta verificación no es ejecutable ni
las alertas cubren ese backend: sus logs van a `docker compose logs api` y nada llega a Cloud Logging ni a
Error Reporting. Allí se mira `docker compose logs api` en el homelab. Este procedimiento vale cuando se vuelva a
Terraform en dev (`GCP_DEV_ENABLED=true`) o, cambiando el proyecto por `parcheu-prod`, tras el primer apply de prod.

**Verificación principal: un error real del backend.** Provoca un `log.error` con traza a través del propio
servicio, por ejemplo una petición que llegue al handler de `Throwable` de `ErrorHandling.kt` (un 500 real,
no un 4xx de validación). Un minuto después debe aparecer en
`https://console.cloud.google.com/errors?project=parcheu-dev`, en el servicio `api`, con la traza y el `traceId`.
Esta prueba recorre el pipeline completo (backend, logback JSON, Cloud Logging, Error Reporting).

**Alternativa que solo prueba Error Reporting (no el backend).** Escribe directamente una entrada con el tipo
`ReportedErrorEvent` y una traza con formato Java reconocible; sin `@type` y sin traza válida Error Reporting
la ignora:

```bash
gcloud logging write run.googleapis.com%2Fstderr \
  '{"@type":"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent","message":"java.lang.IllegalStateException: E10.5 error forzado de prueba\n\tat me.santiagoreina.parcheu.Prueba.forzado(Prueba.kt:1)","serviceContext":{"service":"api","version":"e10-5-prueba"}}' \
  --payload-type=json --severity=ERROR --project parcheu-dev \
  --monitored-resource-type=cloud_run_revision \
  --monitored-resource-labels=service_name=api,revision_name=prueba,location=us-central1,configuration_name=api
```

Si esta alternativa aparece pero el error real no, el fallo está en el backend (formato del log), no en
Error Reporting.

Para Crashlytics: en un build `dev` release (no debug), llama a
`CrashReporting.reporter.recordNonFatal(RuntimeException("prueba"))` o fuerza un cierre y reabre la app; el evento
llega con las claves `route` y `flavor=dev` en unos minutos.

## Apagar el ruido

Para silenciar una alerta mientras se atiende, usa Monitoring > Alerting > Snoozes (no la borres: Terraform la
recrearía). Para cambiar a quién llega, edita `alert_emails` (en `terraform.tfvars` local o `-var`) y aplica.

## Backend de dev en el homeserver

Aplica mientras el backend de dev corra en el homeserver ([ADR-0033](adr/0033-backend-en-homeserver.md)). El detalle de
instalación, HTTPS y llaves está en [deploy/homelab/README.md](../deploy/homelab/README.md); aquí, lo que se hace al operar.
Las alertas de GCP no cubren este entorno: la vigilancia es manual.

**Piezas** (Docker Compose en `~/parcheu/` del servidor): `parcheu-db` (Postgres 16), `parcheu-migrate` (Flyway, una vez
por despliegue) y `parcheu-api` (Ktor en el puerto 8090). Las colas y los jobs corren dentro de la API
(`TASKS_MODE=inline`).

### Comprobar que está sano

```bash
curl https://api-parcheu.homeserverlab.site/v1/health          # o http://192.168.0.10:8090/v1/health desde la LAN
ssh user@homelab 'cd ~/parcheu && docker compose ps'           # los tres servicios; migrate termina y sale (0)
ssh user@homelab 'cd ~/parcheu && docker compose logs --tail 100 parcheu-api'
```

Qué buscar en los registros (JSON, un objeto por línea; `traceId`, `route` y `userId` uuid, nunca correos):
`ai_call` (costo de cada llamada), `simulation_failed` (campo `cause`), `report_created`, `moderation_failed`,
`AiBudgetExceeded`. Para seguir una petición: `docker compose logs parcheu-api | grep <traceId>`.

### Desplegar y revertir

```bash
deploy/homelab/desplegar.sh            # desde el portátil (con el árbol sucio la imagen se etiqueta `-dirty`): construye, sube, migra y verifica
deploy/homelab/desplegar.sh --sin-build
```

El script verifica la salud después de levantar la API y, **si falla, vuelve solo a la imagen anterior** (`PARCHEU_PREV_TAG`).
Revertir a mano: edita `PARCHEU_TAG` en `~/parcheu/.env` con el valor de `PARCHEU_PREV_TAG` y
`docker compose up -d parcheu-api`. Las migraciones de Flyway no se deshacen: si la versión nueva migró y se revierte la imagen,
el código viejo corre contra el esquema nuevo. Para un esquema dañado, restaura un respaldo (siguiente sección).

### Reiniciar o recuperarse de un corte

1. `docker compose up -d` levanta lo que falte; la base conserva el volumen `parcheu-pgdata`.
2. La cola en proceso **pierde lo que tenía en memoria**. Se recuperan solas: las simulaciones `QUEUED` o `RUNNING` de entre
   10 min y 24 h (`simulation-recovery`, cada 15 min) y las purgas de cuentas (`purge-sweep`, cada hora). Las tareas de
   gemelo y memorias se rehacen al siguiente uso o a las 02:00 de Bogotá (`memories`). **No se recuperan** las tareas
   `moderate` ni `moderate-help` (la ayuda ante `SELF_HARM`, E8.6): no tienen barrido. El reporte sí queda en la base; tras un corte,
   revisa en `admin.html` los reportes de prioridad 2 recientes. Es un hueco de código conocido (un barrido de moderación sin terminar).
3. Si ves el mismo id de simulación repetido en los registros una y otra vez, hay una simulación que mata el proceso: míralo
   antes de que gaste IA ([incidentes](#incidentes-frecuentes-por-síntoma)).
4. La IP `192.168.0.10` del servidor debe seguir siendo estática; si cambió, la API no arranca o queda inalcanzable.

### Qué equivale a cada alerta de GCP

| Síntoma (alerta en GCP) | Cómo se ve en el homeserver |
|---|---|
| 5xx altos | `docker compose logs parcheu-api` con `level":"ERROR"`; revisa si coincide con un despliegue y revierte (arriba) |
| p95 alto | Normal tras un reinicio (JVM en frío); si persiste, `docker stats` (la API tiene tope de 640 MB) y las llamadas `ai_call` lentas |
| `AiBudgetExceeded` | Igual que en GCP: [ai-budget-exceeded](#ai-budget-exceeded); el tope es `AI_DAILY_BUDGET_USD` del `.env` |
| `report_created` | Igual: [report-created](#report-created); nadie te avisa por correo, revisa la tabla `reports` o `admin.html` cada día de prueba |
| simulaciones FAILED | Igual: [simulaciones-failed](#simulaciones-failed); la cola es `InProcessTaskQueue` (reintenta hasta 5 veces con espera creciente) |
| API caída | `docker compose ps`; ver [reiniciar](#reiniciar-o-recuperarse-de-un-corte) |

## Moderación y administración

- **Dar o quitar el rol de administrador** (claim `admin` de Firebase Auth): `scripts/grant-admin.sh dev <correo>`; con
  `--revoke` lo quita y con `--dry-run` solo muestra qué haría. Necesita `gcloud` con permisos de administrador de Firebase
  Auth en `parcheu-dev`. La persona cierra sesión y vuelve a entrar para que el servidor vea el claim.
- **Atender reportes:** `admin.html` (Firebase Hosting, [ADR-0028](adr/0028-firebase-hosting-y-admin.md)) lista los reportes y
  permite resolverlos con una acción (descartar, ocultar el mensaje, suspender 24 h o banear). Los de prioridad 2 (`SELF_HARM`, `UNDERAGE`) se atienden
  el mismo día. Nunca copies textos de chats a canales de mensajería ni a issues.
- **Token `fake-admin`:** solo existe con `LOCAL_FAKE_ADMIN=true` y `PARCHEU_ENV=local`; no lo actives en el homeserver, donde
  viven datos de testers.

## Datos: respaldo, purga y borrado

- **Respaldo:** cron diario a las 03:17 (`respaldar.sh`, rotación de 7 días en `~/parcheu/backups`, `respaldo.log` rotado a 1 MiB).
  A mano: `ssh user@homelab '~/parcheu/respaldar.sh'`. Los respaldos viven en el mismo servidor: copia
  `~/parcheu/backups` a otro lugar (por ejemplo Seafile) si los datos importan.
- **Verificar que el respaldo sirve:** `gzip -t ~/parcheu/backups/<archivo>.sql.gz` y, de vez en cuando, restaurar en una base
  desechable. Un respaldo que nunca se restauró no es un respaldo.
- **Restaurar** (en este orden; `down -v` no sirve, porque `parcheu-migrate` correría Flyway antes y la restauración chocaría con el esquema ya creado):
  1. `docker compose stop parcheu-api`.
  2. Vacía la base: `docker compose exec parcheu-db psql -U parcheu -d postgres -c 'DROP DATABASE parcheu WITH (FORCE)' -c 'CREATE DATABASE parcheu OWNER parcheu'`.
  3. Restaura: `gunzip -c parcheu-AAAAMMDD-HHMMSS.sql.gz | docker compose exec -T parcheu-db psql -v ON_ERROR_STOP=1 -U parcheu parcheu`.
  4. Fija en `PARCHEU_TAG` (`~/parcheu/.env`) una imagen compatible con el esquema del respaldo.
  5. `docker compose up -d`.
- **Usuarios demo:** `docker compose run --rm parcheu-migrate seed-demo` los siembra (idempotente) y `purge-demo` los borra con
  todo lo que los nombra ([ADR-0027](adr/0027-usuarios-demo.md)); purgarlos antes de abrir la app a personas reales.
- **Retención y borrado:** la retención diaria (`retention`) y el barrido de purgas (`purge-sweep`) corren en el programador
  interno; las reglas están en [arquitectura.md §10.2](arquitectura.md#102-retención-de-datos-fuente-para-privacidad-data-safety-e83-y-e88)
  y [ADR-0025](adr/0025-borrado-purga-y-retencion.md). Una cuenta borrada pasa por `purge-user`; si el proceso muere a medias, `purge-sweep` la retoma.
- **Espacio en disco:** vigila `df -h` del servidor y `docker system df`. No uses `docker image prune` a ciegas: borraría
  la imagen anterior que necesita la reversión.

## Cambios de configuración y secretos

- Las variables viven en `~/parcheu/.env` (permisos 600, nunca en el repositorio). Cámbialas con edición atómica y
  `docker compose up -d parcheu-api` para recrear el contenedor. Solo llegan a la API las variables que lista `deploy/homelab/compose.yaml` (no hay `env_file`); una variable nueva hay que añadirla allí. Un valor inválido de las listadas hace fallar el arranque y el motivo sale en los registros. Tras cambiar una bandera, comprueba el efecto (por ejemplo `docker compose exec parcheu-api env | grep DEMO`).
- **Presupuesto de IA:** `AI_DAILY_BUDGET_USD` (por defecto 3). Súbelo solo tras revisar `ai_usage_daily` y entender el gasto
  ([ADR-0010](adr/0010-control-de-costo-ia.md)).
- **Proveedor y modelos de IA:** `AI_PROVIDER`, `AI_MODEL_CHAT`, `AI_MODEL_JUDGE`, `AI_MODEL_LITE`; cambiar de modelo es una variable
  y un reinicio ([ADR-0008](adr/0008-ia-vertex-proveedores-intercambiables.md)). Claude solo tras confirmar que los créditos lo cubren.
- **Llave de la cuenta de servicio** (`~/parcheu/secrets/sa.json`): rotarla es crear una llave nueva, copiarla con permisos 600,
  reiniciar la API y borrar la anterior en IAM. La cuenta `sa-homelab` es temporal: bórrala al terminar las pruebas.
- **Banderas útiles:** `DEMO_USERS_ENABLED`, `SCENARIO_MODE_ENABLED`, `MODERATION_ENABLED` (ver [desarrollo-local.md](desarrollo-local.md)).

## Incidentes frecuentes por síntoma

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| La app dice "sin conexión" pero el teléfono tiene red | La API no responde o `parcheu.apiUrl.dev` apunta a otra URL | `curl` a `/v1/health`; comprobar `local.properties` ([desarrollo-local.md](desarrollo-local.md)) |
| La app no inicia sesión (error 401 en todo) | `PARCHEU_ENV=local` (no valida tokens reales) o reloj del servidor desviado | Pasar a `dev` según el README del homelab; `timedatectl` en el servidor |
| Una simulación se queda en curso | Reinicio durante la tarea | Espera hasta 15 min al barrido; si no, mira el registro `simulate:` con el id |
| El mismo id de simulación se reencola sin parar | Una entrada que mata el proceso | Detén la API (`docker compose stop parcheu-api`), revisa el último error y marca la simulación como `FAILED` en la base antes de reiniciar |
| Chats o progreso en vivo no se actualizan | Credenciales de Firestore ausentes o proyecto equivocado | Registros de arranque; `FIREBASE_PROJECT_ID` debe ser `parcheu-dev` y `sa.json` válido |
| No llegan push | `PARCHEU_ENV=local` o falta `firebasecloudmessaging.admin` en la cuenta | Revisar rol y entorno |
| Plus no se activa tras comprar | No hay verificación de Play en el homeserver | Usar `grant-plus` ([README del homelab](../deploy/homelab/README.md)); las renovaciones por RTDN no se sincronizan |
| Todo responde 503 `AI_BUDGET` | Tope diario agotado | [ai-budget-exceeded](#ai-budget-exceeded); se libera a medianoche de Bogotá |
