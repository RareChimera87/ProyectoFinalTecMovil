# Infraestructura de ParcheU (Terraform)

Terraform 1.15 con los providers `google` y `google-beta` 7.x (fijados en cada
`.terraform.lock.hcl`). Decisiones: [ADR-0014](../../docs/adr/0014-dos-proyectos-terraform-wif.md),
[ADR-0019](../../docs/adr/0019-cloud-tasks-trabajo-asincrono.md),
[ADR-0020](../../docs/adr/0020-region-y-transferencia-internacional.md),
[ADR-0010](../../docs/adr/0010-control-de-costo-ia.md). Detalle de recursos: `docs/arquitectura.md` §12.

```
infra/terraform/
├─ bootstrap/          bucket de estado, WIF para GitHub, sa-deployer, sa-terraform(-plan), sa-api y sa-tasks con sus roles,
│                      y presupuesto (una vez por proyecto, ADC del usuario)
├─ modules/platform/   APIs, Artifact Registry, Cloud Run api + jobs, Cloud SQL, Secret Manager,
│                      Cloud Tasks, Scheduler, Pub/Sub, Firestore, Firebase, IAM de recursos, sa-play, uptime
└─ envs/{dev,prod}/    instancia de platform por entorno (estado en gs://<proyecto>-tfstate/platform)
```

| Entorno | Proyecto | Estado | `apply` |
|---|---|---|---|
| dev | `parcheu-dev` | `gs://parcheu-dev-tfstate` | I1 o CI (`sa-terraform` vía WIF, solo desde la rama `dev`) |
| prod | `parcheu-prod` | `gs://parcheu-prod-tfstate` | **solo el usuario**, con su ADC; CI solo hace `plan` (`sa-terraform-plan`, solo metadatos, solo desde la rama `dev`) |

## Mapa de colas de Cloud Tasks

Cada cola tiene `max_dispatches_per_second` y `max_concurrent_dispatches` (techo de gasto de IA). Las
tareas llaman a `POST /internal/tasks/<tarea>` con token OIDC de `sa-tasks`; el nombre de la tarea es el id
de la entidad (deduplicación).

| Cola | Tareas | dev / prod (despachos/s, concurrentes) | Variable |
|---|---|---|---|
| `simulations` | `simulate`, `judge-scenario` | 2/s, 10 | `simulations_max_dispatches_per_second`, `simulations_max_concurrent_dispatches` en `envs/<env>` |
| `ai-light` | `build-persona`, `extract-memories`, `moderate`, `name-community` | 5/s, 10 | `queue_limits["ai-light"]` |
| `maintenance` | `purge-user` (sin IA) | 1/s, 2 | `queue_limits["maintenance"]` |

## Contrato con el backend (E0.3)

Cloud Run `api` (args `serve`) y los jobs `migrate`, `seed-demo` y `purge-demo` usan la **misma imagen**
con distinto argumento y la identidad `sa-api`. Terraform crea todo con una imagen de ejemplo de Google e
ignora los cambios de imagen: el deploy de CI (E0.6) la reemplaza. Variables de entorno que reciben:

| Variable | Valor |
|---|---|
| `ENV` | `dev` o `prod` |
| `GCP_PROJECT`, `GCP_REGION` | proyecto y `us-central1` |
| `CLOUD_SQL_INSTANCE` | `<proyecto>:us-central1:parcheu-<env>` (Cloud SQL Java Connector) |
| `DB_NAME`, `DB_USER`, `DB_IAM_AUTH` | `parcheu`, `sa-api@<proyecto>.iam` (usuario IAM, sin contraseña), `true` |
| `TASKS_SERVICE_ACCOUNT` | email de `sa-tasks` (va en el token OIDC de cada tarea) |
| `TASKS_QUEUE_SIM`, `TASKS_QUEUE_AI_LIGHT`, `TASKS_QUEUE_MAINT` | nombre completo de cada cola |
| `INTERNAL_OIDC_AUDIENCE` | `https://api.parcheu-<env>.internal`: audiencia fija (custom audience de Cloud Run) de los tokens de Tasks, Scheduler y Pub/Sub; `/internal/*` la verifica junto con el email de `sa-tasks` |
| `DEMO_USERS_ENABLED` | `var.demo_users_enabled`: `true` en dev; en prod `false` salvo durante la prueba cerrada (`-var demo_users_enabled=true`, ADR-0027, E4.9) |
| `SECRET_HMAC_ANON_KEY`, `SECRET_PLAY_SA` | nombre del secreto; la API lee la versión `latest` con la API de Secret Manager (no se montan, para que Cloud Run no falle mientras el secreto no tenga valor) |
| `PLAY_SERVICE_ACCOUNT` | solo prod (`play_service_account_enabled`): correo de `sa-play`, que la API suplanta para la API de Google Play Developer (E7.1); sin ella usa la llave de `SECRET_PLAY_SA` |

Trabajos de Cloud Scheduler (hora de Bogotá) → `POST /internal/jobs/<nombre>`: `communities` (lunes 03:00),
`memories` (diario 02:00), `twin-accuracy` (domingos 23:00), `retention` (diario 03:30), `purge-sweep` (cada hora, minuto 15; E8.3).
Pub/Sub `play-rtdn`: en prod, suscripción push OIDC a `/internal/billing/rtdn`; en dev solo el tema.

**Nota para E0.4 (migraciones):** `sa-api` entra a Postgres como usuario IAM. En Postgres 16 el esquema
`public` no da permiso de creación a todos, así que antes del primer `migrate` hay que darle la propiedad
de la base al usuario IAM una vez (como `postgres`, desde Cloud SQL Studio o `gcloud sql connect`):
`ALTER DATABASE parcheu OWNER TO "sa-api@<proyecto>.iam"; ALTER SCHEMA public OWNER TO "sa-api@<proyecto>.iam";`

## Cuentas de servicio

| Cuenta | Dónde | Roles |
|---|---|---|
| `sa-api` | bootstrap (desde fix/wif-deployer-dev; la plataforma la lee con `data`) | `cloudsql.client`, `cloudsql.instanceUser`, `aiplatform.user`, `cloudtasks.enqueuer`, `datastore.user`, `firebaseauth.admin`, `firebasecloudmessaging.admin`, `logging.logWriter`, `monitoring.metricWriter`, `cloudtrace.agent` (bootstrap); `secretAccessor` solo sobre sus dos secretos (platform); `serviceAccountUser` sobre `sa-tasks` (bootstrap) |
| `sa-tasks` | bootstrap | `run.invoker` sobre `api` (platform) |
| `sa-play` | platform (solo prod, `play_service_account_enabled`) | Ninguno de GCP: sus permisos se dan en Play Console (verificar compras para la API, E7.1; publicar para CI, E10.2). `sa-api` tiene `serviceAccountTokenCreator` sobre ella para suplantarla sin llaves. GitHub la suplanta por WIF (`play.tf`, `attribute.env_ref`): environment `play` desde `dev`, `main` o un tag `v*`; `play-alpha` desde `main` o un tag `v*` |
| `sa-deployer` | bootstrap | `run.admin`, `artifactregistry.writer`, `firebasehosting.admin`, `firebaserules.admin`, `datastore.indexAdmin`, `serviceusage.serviceUsageConsumer`; `serviceAccountUser` sobre `sa-api`; en dev, `serviceAccountTokenCreator` sobre sí misma (smoke e2e). **Quién la usa:** en dev solo jobs de la rama `dev` (`attribute.ref/refs/heads/dev`; un PR no puede); en prod solo un job del environment `production` que corra sobre un tag `v*` (`attribute.env_ref/production@tag`) |
| `sa-terraform` | bootstrap (solo dev) | `apply` en CI, **solo desde la rama `dev`**. Cada rol con su motivo en `bootstrap/main.tf`: `serviceUsageAdmin`, `run.admin`, `artifactregistry.admin`, `cloudsql.admin`, `cloudtasks.admin`, `cloudscheduler.admin`, `pubsub.admin`, `monitoring.admin` y el rol personalizado `parcheuTerraformApply` (contenedores de secretos, base de Firestore, proyecto de Firebase y métricas basadas en logs, **sin** `secretmanager.versions.*`, `datastore.entities.*` ni `logging.sinks/buckets/links.*`); `serviceAccountUser` solo sobre `sa-api` y `sa-tasks`. **Sin** `serviceAccountAdmin`, `serviceAccountUser` de proyecto, `projectIamAdmin`, `secretmanager.admin`, `datastore.owner` `firebase.admin` ni `logging.configWriter` (con sus sinks se exportarían todos los logs). Sin roles sobre la facturación. No existe en prod (allí aplica el usuario) |
| `sa-terraform-plan` | bootstrap | `plan` en CI, solo metadatos: `run.viewer`, `secretmanager.viewer`, `cloudtasks.viewer`, `cloudscheduler.viewer`, `pubsub.viewer`, `monitoring.viewer`, `iam.serviceAccountViewer`, `serviceusage.*` y el rol personalizado `parcheuTerraformPlan` (solo `*.get/list` de bases, repos, instancias y proyecto). **Sin** `roles/viewer`, `securityReviewer` ni `firebase.viewer`: incluyen `datastore.entities.*` y `logging.logEntries.list`. Lee el estado (`objectViewer`; el plan de CI corre con `-lock=false`). En dev cualquier PR del repo puede usarla (no hay datos reales); en prod solo jobs de la rama `dev` (ADR-0012) |

**WIF.** El pool solo acepta tokens cuyo `repository_id` y `repository_owner_id` coinciden con
`var.github_repository_id` / `var.github_repository_owner_id` (IDs numéricos, que no se reutilizan como un
nombre). Además de los atributos directos (`repository`, `ref`, `environment`...), el proveedor mapea el
atributo derivado `env_ref` = `<environment>@<ref>` (`tag` en lugar de la ref si empieza por `refs/tags/v`;
`-` si el job no tiene environment), porque un `principalSet` no admite prefijos ni combinar dos atributos.

**Residuales aceptados** (revisión de #105). Todos exigen que el cambio entre a `dev` por un PR aprobado,
porque `sa-terraform` solo acepta la rama `dev`:
- `run.admin` + actAs sobre `sa-api` (en `sa-terraform` y en `sa-deployer` de dev): pueden ejecutar código con
  los permisos de `sa-api`. Es inherente a desplegar Cloud Run como esa cuenta.
- `secretmanager.secrets.setIamPolicy` (`parcheuTerraformApply`): `sa-terraform` podría darse `secretAccessor`.
  Las condiciones de IAM no restringen el miembro de un `setIamPolicy` de secretos, y `sa-api` ya tiene ese acceso.
- `cloudsql.admin` incluye `instances.export` y `users.update`: lectura completa de la base de dev (no hay un
  rol predefinido que administre la instancia sin eso).

`scripts/check-iam.sh plan.json` lee el JSON de `terraform show -json plan` (no consulta GCP) y sale con 1
ante cualquier **ERROR**: `roles/owner` o `roles/editor` para cualquier principal; `allUsers` o
`allAuthenticatedUsers` con cualquier rol, salvo `roles/run.invoker` para `allUsers` (lista blanca
`PUBLIC_ALLOWED_ROLES`); `serviceAccountKeyAdmin` o `securityAdmin`; `serviceAccountTokenCreator` y
`serviceAccountUser` a nivel de proyecto fuera de las cuentas esperadas (`EXPECTED_SA_NAMES`:
`sa-deployer`); `serviceAccountAdmin` fuera de `SA_ADMIN_EXPECTED_NAMES` (hoy ninguna);
`projectIamAdmin` sin condición; `sa-terraform` con `projectIamAdmin` (con o sin condición),
`serviceAccountAdmin` o `serviceAccountUser` de proyecto (`TERRAFORM_FORBIDDEN_PROJECT_ROLES`, #105); y los chequeos de bootstrap (`sa-api`/`sa-deployer` sin sus roles,
`sa-terraform-plan` con roles de datos o de facturación). Las listas blancas están al inicio del script.
Los **AVISO** de `user:` con roles distintos de `viewer`, `logging.viewer` y `monitoring.viewer` no cambian
el código de salida (0). Solo revisa lo que está en el plan, no la política real del proyecto.
Desde que `sa-api` nace en el bootstrap, sus roles mínimos se verifican sobre el plan del **bootstrap**
(paso 2 de abajo).

El **presupuesto de facturación** (50 % y 90 %) se crea en `bootstrap/` con el ADC del usuario, no en la
plataforma: la cuenta de facturación se comparte con otros proyectos y ninguna identidad de CI tiene roles
sobre ella (`billing.viewer` expone `getPaymentInfo` y `costsManager` permite borrar los presupuestos de
los demás proyectos). Los roles personalizados no existen a nivel de cuenta de facturación sin organización.

## Puesta en marcha (una vez por entorno)

Requisitos: gcloud con ADC (`gcloud auth application-default login`), Terraform 1.15, jq.
La cuenta de facturación nunca se versiona: se pasa por `TF_VAR_billing_account`
(en local, `~/.parcheu/terraform.env`). Solo la usan `crear-proyectos.sh` y el bootstrap; CI no la necesita.

```bash
export TF_VAR_billing_account=XXXXXX-XXXXXX-XXXXXX
ENV=dev   # o prod (solo el usuario)

# 1. Proyecto, facturación, APIs mínimas y bucket de estado.
BILLING_ACCOUNT=$TF_VAR_billing_account scripts/crear-proyectos.sh $ENV

# 2. Bootstrap (importa el bucket y crea WIF, sa-deployer, sa-terraform, sa-terraform-plan y el presupuesto).
cd infra/terraform/bootstrap
terraform init -reconfigure -backend-config=bucket=parcheu-$ENV-tfstate -backend-config=prefix=bootstrap
terraform plan -var environment=$ENV -out=plan.tfplan && terraform show -json plan.tfplan > plan.json
../../../scripts/check-iam.sh plan.json   # sa-api, sa-deployer y sa-terraform(-plan)
terraform apply plan.tfplan
terraform output   # valores para los secretos de GitHub (abajo)

# 3. Plataforma (prod exige -var 'alert_emails=["..."]'; dev trae el correo de contacto por defecto).
cd ../envs/$ENV
terraform init
terraform plan -out=plan.tfplan
terraform show -json plan.tfplan > plan.json
../../../../scripts/check-plan.sh plan.json && ../../../../scripts/check-iam.sh plan.json
terraform apply plan.tfplan

# 4. Valores de los secretos (nunca pasan por Terraform).
../../../../scripts/cargar-secretos.sh $ENV
```

Si `google_firebase_project` falla porque la cuenta no aceptó los términos de Firebase: agrega Firebase al
proyecto desde https://console.firebase.google.com (una vez) y luego
`terraform import module.platform.google_firebase_project.this projects/parcheu-$ENV`.

### Secretos de GitHub que salen del bootstrap

| Secreto | Valor |
|---|---|
| `WIF_PROVIDER` | `terraform output wif_provider` de dev |
| `WIF_PROVIDER_PROD` | `terraform output wif_provider` de prod (cada proyecto tiene su pool) |
| `WIF_SERVICE_ACCOUNT_DEV` / `_PROD` | `deployer_service_account` |
| `TF_PLAN_SERVICE_ACCOUNT_DEV` / `_PROD` | `terraform_plan_service_account` (el `plan` de CI) |
| `TF_SERVICE_ACCOUNT_DEV` | `terraform_service_account` (el `apply` de dev, E0.6; no hay en prod) |

## Verificaciones

```bash
terraform fmt -check -recursive infra/terraform
for d in bootstrap modules/platform envs/dev envs/prod; do (cd infra/terraform/$d && terraform init -backend=false && terraform validate); done
scripts/test/check-plan.test.sh        # check-plan.sh y check-iam.sh con fixtures y mutaciones
scripts/test/cargar-secretos.test.sh   # cargar-secretos.sh con un gcloud falso
```

`.github/workflows/infra-ci.yml` repite esto en cada PR que toca `infra/**` y hace el `plan` de dev vía WIF.
