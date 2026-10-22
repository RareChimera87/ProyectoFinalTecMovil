# Infraestructura de ParcheU (Terraform)

Terraform 1.15 con los providers `google` y `google-beta` 7.x (fijados en cada
`.terraform.lock.hcl`). Decisiones: [ADR-0014](../../docs/adr/0014-dos-proyectos-terraform-wif.md),
[ADR-0019](../../docs/adr/0019-cloud-tasks-trabajo-asincrono.md),
[ADR-0020](../../docs/adr/0020-region-y-transferencia-internacional.md),
[ADR-0010](../../docs/adr/0010-control-de-costo-ia.md). Detalle de recursos: `docs/arquitectura.md` §12.

```
infra/terraform/
├─ bootstrap/          bucket de estado, WIF para GitHub, sa-deployer y sa-terraform (una vez por proyecto, ADC del usuario)
├─ modules/platform/   APIs, Artifact Registry, Cloud Run api + jobs, Cloud SQL, Secret Manager,
│                      Cloud Tasks, Scheduler, Pub/Sub, Firestore, Firebase, IAM, presupuesto, uptime
└─ envs/{dev,prod}/    instancia de platform por entorno (estado en gs://<proyecto>-tfstate/platform)
```

| Entorno | Proyecto | Estado | `apply` |
|---|---|---|---|
| dev | `parcheu-dev` | `gs://parcheu-dev-tfstate` | I1 o CI (`sa-terraform` vía WIF) |
| prod | `parcheu-prod` | `gs://parcheu-prod-tfstate` | **solo el usuario**, con su ADC; CI solo hace `plan` (sa-terraform de prod es de solo lectura) |

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
| `SECRET_HMAC_ANON_KEY`, `SECRET_PLAY_SA` | nombre del secreto; la API lee la versión `latest` con la API de Secret Manager (no se montan, para que Cloud Run no falle mientras el secreto no tenga valor) |

Trabajos de Cloud Scheduler (hora de Bogotá) → `POST /internal/jobs/<nombre>`: `communities` (lunes 03:00),
`memories` (diario 02:00), `twin-accuracy` (domingos 23:00), `retention` (diario 03:30).
Pub/Sub `play-rtdn`: en prod, suscripción push OIDC a `/internal/billing/rtdn`; en dev solo el tema.

**Nota para E0.4 (migraciones):** `sa-api` entra a Postgres como usuario IAM. En Postgres 16 el esquema
`public` no da permiso de creación a todos, así que antes del primer `migrate` hay que darle la propiedad
de la base al usuario IAM una vez (como `postgres`, desde Cloud SQL Studio o `gcloud sql connect`):
`ALTER DATABASE parcheu OWNER TO "sa-api@<proyecto>.iam"; ALTER SCHEMA public OWNER TO "sa-api@<proyecto>.iam";`

## Cuentas de servicio

| Cuenta | Dónde | Roles |
|---|---|---|
| `sa-api` | platform | `cloudsql.client`, `cloudsql.instanceUser`, `aiplatform.user`, `cloudtasks.enqueuer`, `datastore.user`, `firebaseauth.admin`, `firebasecloudmessaging.admin`, `logging.logWriter`, `monitoring.metricWriter`, `cloudtrace.agent`; `secretAccessor` solo sobre sus dos secretos; `serviceAccountUser` sobre `sa-tasks` |
| `sa-tasks` | platform | `run.invoker` sobre `api` |
| `sa-deployer` | bootstrap | `run.admin`, `artifactregistry.writer`, `firebasehosting.admin`, `firebaserules.admin`, `datastore.indexAdmin`, `serviceusage.serviceUsageConsumer`; `serviceAccountUser` sobre `sa-api` (platform). En prod solo la puede usar un job del environment `production` de GitHub |
| `sa-terraform` | bootstrap | dev: administradores de cada servicio usado (sin owner/editor) + `billing.costsManager`; prod: `viewer`, `iam.securityReviewer`, `billing.viewer` (solo `plan`) |

`scripts/check-iam.sh` falla si alguna cuenta de servicio recibe `roles/owner` o `roles/editor` o si a
`sa-api`/`sa-deployer` les falta un rol esperado.

## Puesta en marcha (una vez por entorno)

Requisitos: gcloud con ADC (`gcloud auth application-default login`), Terraform 1.15, jq.
La cuenta de facturación nunca se versiona: se pasa por `TF_VAR_billing_account`
(en local, `~/.parcheu/terraform.env`; en CI, el secreto `BILLING_ACCOUNT_ID`).

```bash
export TF_VAR_billing_account=XXXXXX-XXXXXX-XXXXXX
ENV=dev   # o prod (solo el usuario)

# 1. Proyecto, facturación, APIs mínimas y bucket de estado.
BILLING_ACCOUNT=$TF_VAR_billing_account scripts/crear-proyectos.sh $ENV

# 2. Bootstrap (importa el bucket y crea WIF, sa-deployer y sa-terraform).
cd infra/terraform/bootstrap
terraform init -reconfigure -backend-config=bucket=parcheu-$ENV-tfstate -backend-config=prefix=bootstrap
terraform apply -var environment=$ENV
terraform output   # valores para los secretos de GitHub (abajo)

# 3. Plataforma.
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
| `TF_SERVICE_ACCOUNT_DEV` / `_PROD` | `terraform_service_account` |
| `BILLING_ACCOUNT_ID` | cuenta de facturación (la usa el `plan`) |

## Verificaciones

```bash
terraform fmt -check -recursive infra/terraform
for d in bootstrap envs/dev envs/prod; do (cd infra/terraform/$d && terraform init -backend=false && terraform validate); done
scripts/test/check-plan.test.sh     # pruebas de check-plan.sh y check-iam.sh con fixtures
```

`.github/workflows/infra-ci.yml` repite esto en cada PR que toca `infra/**` y hace el `plan` de dev vía WIF.
