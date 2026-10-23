# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E0.8 · Configuración de Firebase

Estado al 22 oct: en `parcheu-dev` ya está hecho (con el ADC del usuario): Firebase agregado al proyecto,
APIs `firestore`, `firebaserules`, `firebasehosting` e `identitytoolkit` habilitadas, base Firestore `(default)` en
`nam5`, app Android `me.santiagoreina.parcheu.dev` (`1:388770932007:android:d6f5452d14d98bf1d43502`) con SHA-1 y
SHA-256 de debug, reglas, índices y Hosting desplegados (`https://parcheu-dev.web.app` responde 200),
environment `production` de GitHub con el usuario como revisor y secreto `GOOGLE_SERVICES_JSON_DEV`.

1. **Importar al estado de Terraform lo que se creó por CLI en dev** (E0.5 declara estos recursos; sin importarlos el
   `apply` fallará con "already exists"):

   ```bash
   cd infra/terraform/envs/dev && terraform init
   terraform import module.platform.google_firebase_project.this projects/parcheu-dev
   terraform import module.platform.google_firestore_database.default projects/parcheu-dev/databases/'(default)'
   ```

2. **Activar el acceso con Google en dev** (la consola es la única vía sin crear un cliente OAuth a mano): consola
   Firebase → `parcheu-dev` → Authentication → Comenzar → Método de acceso → Google → Habilitar, con
   `sanctireinadiaz@gmail.com` como correo de asistencia. Al guardarlo se crean los clientes OAuth (Android y web).
   Luego regenera y recarga el archivo (hoy `oauth_client` viene vacío):

   ```bash
   firebase apps:sdkconfig ANDROID 1:388770932007:android:d6f5452d14d98bf1d43502 --project parcheu-dev > ~/.parcheu/google-services.dev.json
   base64 -w0 ~/.parcheu/google-services.dev.json | gh secret set GOOGLE_SERVICES_JSON_DEV -R RareChimera87/ProyectoFinalTecMovil
   ```

3. **Verificación manual del criterio de inicio de sesión** (cuando E2.2 exista): instala la variante `devDebug`
   firmada con el keystore de debug cuyo SHA-1 está registrado, en un emulador con Google Play, e inicia sesión con
   Google; debe aparecer el usuario en Authentication → Usuarios. Anótalo en el PR de E2.2.

4. **Secretos de WIF** (dependen del `apply` del bootstrap de E0.5, ver su sección): `WIF_PROVIDER`,
   `WIF_SERVICE_ACCOUNT_DEV`, `WIF_PROVIDER_PROD`, `WIF_SERVICE_ACCOUNT_PROD`. Hasta entonces `firebase-deploy`
   no puede autenticarse en CI. Verifica que `sa-deployer` ya tiene `firebasehosting.admin`, `firebaserules.admin` y
   `datastore.indexAdmin` (los declara E0.5) y que puede usar el proyecto (`serviceusage.serviceUsageConsumer`).

5. **Prod, cuando exista `parcheu-prod`** (necesita el cupo de facturación de E0.5, punto 4, y su `apply`):

   ```bash
   firebase projects:addfirebase parcheu-prod            # solo si google_firebase_project no lo creó
   firebase apps:create ANDROID "ParcheU" --package-name me.santiagoreina.parcheu --project parcheu-prod
   firebase apps:android:sha:create <APP_ID_PROD> <SHA-1 de la llave de subida> --project parcheu-prod
   firebase apps:android:sha:create <APP_ID_PROD> <SHA-256 de la llave de subida> --project parcheu-prod
   firebase deploy --only firestore:rules,firestore:indexes,hosting --project parcheu-prod   # o el tag v*
   base64 -w0 google-services.json | gh secret set GOOGLE_SERVICES_JSON_PROD -R RareChimera87/ProyectoFinalTecMovil
   ```

   Después de la primera subida a Play registra también el SHA-1/SHA-256 de la **firma de la app de Play**
   (Play Console → Integridad de la app) o el login con Google fallará en producción. Si el ID real de prod lleva
   sufijo, cámbialo en `firebase/.firebaserc` y en `.github/workflows/firebase-deploy.yml`.

6. **Probar la aprobación de producción** (criterio de la ficha): en una rama descartable crea y sube el tag
   `v0.0.0-rc` (`git tag v0.0.0-rc && git push origin v0.0.0-rc`); el job `deploy-prod` debe quedar en espera de tu
   aprobación del environment `production`. Recházalo y borra el tag. No se probó aquí porque solo el usuario aprueba.
## E0.5 · Infraestructura con Terraform

Estado al 22 oct: el proyecto `parcheu-dev` existe, tiene facturación y el bucket versionado
`gs://parcheu-dev-tfstate`. Los `plan` de bootstrap y de la plataforma de dev pasan (63 recursos) y
`check-plan.sh`/`check-iam.sh` están en verde. **No se aplicó nada con Terraform**: el entorno del agente no
tuvo permiso para ejecutar `terraform apply` (ni siquiera en dev) ni para crear `parcheu-prod`. La cuenta de facturación está en `~/.parcheu/terraform.env`.

1. **Aplicar el bootstrap de dev** (crea WIF, `sa-deployer`, `sa-terraform`, `sa-terraform-plan` y el presupuesto de facturación; importa el bucket):
## E0.1 · Monorepo y git flow

1. **Protección de `dev` con checks requeridos.** Hoy `dev` y `main` exigen PR y bloquean force push, pero no tienen checks requeridos. Tras mezclar este PR y cuando los workflows `pr-title`, `gitleaks` y `major-gate` hayan corrido una vez, ejecuta como admin del repo:

   ```bash
   CHECKS="pr-title,gitleaks,major-gate" scripts/proteger-ramas.sh dev
   ```

   Cuando E0.6 agregue `revision-agente` y las CI por ruta, vuelve a ejecutarlo incluyéndolos en `CHECKS`.
2. **Protección de `main` con tu aprobación.** `scripts/proteger-ramas.sh main` exige 1 aprobación pero deja `enforce_admins: false`. Decisión: al ser tú la única cuenta (autora y admin), GitHub no deja aprobar el PR propio y con `enforce_admins: true` nadie podría mezclar. Los agentes abren el PR `release/*` → `main` y tú lo mezclas con `gh pr merge --admin`, que cuenta como tu aprobación explícita. Cuando entre un segundo colaborador (I2/I3) pasa `enforce_admins` a `true`. En `dev` se usa `strict: false` para que los PR paralelos con auto-merge no queden detrás esperando un `update-branch`.
3. **Agregar a I2 e I3 como colaboradores** del repo y reemplazar los comentarios de `.github/CODEOWNERS` con sus usuarios de GitHub.

## E0.3 · Esqueleto del backend

No hay pasos exclusivos de una persona para esta feature. Dos cosas a tener en cuenta:

1. **Dependencias del PR.** E0.3 se construyó sobre E0.1 (PR #2) y E0.2 (PR #4), que al momento de implementarla seguían abiertos; la rama las integra con dos merges. Mezcla antes esos PR: el diff de E0.3 se reduce a `backend/` y los docs de la ficha.
2. **CI del backend.** El workflow `backend-ci` (con Testcontainers) lo crea E0.6; hasta entonces `MigrateContainerTest` no ha corrido en ningún entorno (en esta máquina Docker no funciona) y debe revisarse cuando exista esa CI.

## E10.1 · Configuración de Play Console

Guía completa y registro de estado en [play-console.md](play-console.md).

| # | Paso | Dueño | Fecha límite |
|---|---|---|---|
| 1 | Confirmar tipo de cuenta de desarrollador y fecha de creación; registrarlos en `play-console.md` | I3 | 24 oct |
| 2 | Iniciar verificación de identidad (y de dispositivo si se pide) | I3 | 22 oct |
| 3 | Crear la app `me.santiagoreina.parcheu` (es-419, Social, gratis con compras) y confirmar Play App Signing | I3 | 24 oct |
| 4 | Crear el perfil de pagos y completar su verificación | I3 | 25 oct |
| 5 | Respaldar `~/.parcheu/upload-keystore.jks` y cargar los 4 secretos `UPLOAD_*` en GitHub (comandos en `play-console.md` §6) | I3 / usuario | 25 oct |
| 6 | Crear `sa-play` en GCP prod, invitarla a Play Console con permisos mínimos y cargar `PLAY_SERVICE_ACCOUNT_JSON` (§7) | I3 | 27 oct |
| 7 | Crear el Google Group y confirmar ≥ 15 correos de testers | I3 | 26-28 oct |
| 8 | Agregar ≥ 3 license testers | I3 | 28 oct |

Nota: la llave de subida ya está generada localmente; falta cargarla a GitHub porque el entorno automatizado no tiene permiso para escribir secretos.
## E0.7 · Esqueleto Android

Lo automatizable ya quedó hecho: flavors, `BuildConfig`, `scripts/fetch-google-services.sh` probado contra `parcheu-dev` y el secreto `GOOGLE_SERVICES_JSON_DEV` ya existe en GitHub.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **URL de la API de prod.** Cuando exista `parcheu-prod` y su servicio `api` (E0.5 apply de prod + primer `deploy-prod`), poner la URL real en el flavor `prod` de `android/app/build.gradle.kts` (hoy es un marcador: `https://api-parcheu-prod.us-central1.run.app/`). Se obtiene con `gcloud run services describe api --project parcheu-prod --region us-central1 --format='value(status.url)'` o con `terraform output api_url` en `infra/terraform/envs/prod`. Mientras tanto se puede compilar con `-Pparcheu.apiUrl.prod=<url>/`. La 0.1 no usa backend, así que no bloquea la primera subida. | I1 | Antes de la 0.2 |
| 2 | **Hosting de prod para los enlaces legales.** Los enlaces 'Términos' y 'Política de privacidad' de A4 en el flavor `prod` apuntan a `https://parcheu-prod.web.app/terminos` y `/privacidad`. Si el sitio de Hosting de prod queda con otro ID, cambiar `parcheu.hostingUrl.prod` (o el valor por defecto en `app/build.gradle.kts`). Las páginas las publica E8.4. | I3 | Antes de la 0.1 (28-29 oct) |
| 3 | **google-services.json de prod.** Cuando E0.8 registre la app `me.santiagoreina.parcheu` en `parcheu-prod`: `scripts/fetch-google-services.sh prod` y cargar el secreto con `base64 -w0 android/app/src/prod/google-services.json \| gh secret set GOOGLE_SERVICES_JSON_PROD`. | I3 | Con E0.8 en prod |
| 4 | **Prueba en dispositivo (I2).** Instalar `./gradlew :app:installDevDebug`, abrir el catálogo de rutas del stub de Splash y recorrer las 36 pantallas, la NavBar en las 5 raíces y los enlaces legales de A4. Repetir con `:app:installProdRelease` (R8 activo, firmado con la llave de debug) para confirmar que las rutas tipadas y el contrato sobreviven a la minificación. Dejar el resultado en el PR. | I2 | Revisión del PR de E0.7 |
| 5 | **Aprobación del revisor par** del PR (feature major): la pide `major-gate`; según `docs/equipo.md` el par de E0.7 es I1. | I1 | Revisión del PR de E0.7 |

## E1.1 · Tokens de diseño

Lo automatizable ya quedó hecho: tokens, fuentes Sora e Inter (OFL) en `android/designsystem/src/main/res/font`, pruebas de afinidad y contraste AA, y capturas Roborazzi de la hoja de tokens en ambos temas (`android/designsystem/src/test/screenshots/token_sheet_{dark,light}.png`).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Revisión visual lado a lado.** Comparar `token_sheet_dark.png` y `token_sheet_light.png` con la hoja de sistema S0 y la Sección I de Claude Design (tokens exactos, tipografía, radios). Diferencias aceptadas por ADR-0030: `ort` #9E480B y `mintt` #0B6B59 en claro, y avatares de claro con alpha > 0,80 pintados en violeta pleno con texto blanco. | I2 | Ventana de revisión de la ola 4 (29 oct) |
| 2 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E1.1 es I3. | I3 | Antes del merge |
| 3 | **Mezclar primero E0.7 (PR #8).** Esta rama parte de `feature/android-skeleton` porque el módulo `:designsystem` nace ahí; el PR de E1.1 muestra también los commits de E0.7 hasta que #8 se mezcle en `dev`. | I2 | Antes del merge de E1.1 |

## E0.6 · Pipelines de CI/CD

**Corrección del 22 oct (fix/ci-sin-puertas-humanas).** El CI exigía la aprobación de un segundo usuario
de GitHub (`major-gate`) y una revisión por API con `ANTHROPIC_API_KEY` (`revision-agente`); con una sola
cuenta eso dejó 40 PR sin mezclar. Ahora ambos checks solo exigen la etiqueta `revisado-agente`, que pone
el integrador del workflow tras la revisión adversarial del agente revisor. `auto-merge` queda apagado
(`AUTO_MERGE_ENABLED` no definida): los merges a `dev` los hace el workflow. Los checks de backend,
contrato, Firestore, migraciones e infra se omiten (en verde, con aviso) mientras su directorio no exista
en la rama, e `infra-plan-dev` se omite hasta que exista el secreto `WIF_PROVIDER` (E0.5).
**Nada pendiente del usuario** por este cambio; el incidente original de abajo se conserva como historia.

**Incidente del 22 oct (leer primero).** El PR #11 (E0.6, major) se mezcló a `dev` a los segundos de
abrirse, sin revisión y con checks en rojo: su propio workflow `auto-merge` corrió en el PR y, como la
protección de `dev` no exigía ningún check, `gh pr merge --auto` mezcló al instante. Medidas tomadas: el
workflow `auto-merge` quedó **deshabilitado** en el repo (`gh workflow disable auto-merge.yml`) y el PR de
`fix/auto-merge-sin-protecciones` agrega una salvaguarda (solo activa el auto-merge si `dev` exige
`revision-agente` y `major-gate`). Decide tú:
- **Mantener E0.6 en dev** y hacer su revisión post-merge (I3, PLAN.md §5 punto 2) sobre el commit `6d8b966`; o
- **Revertirlo:** `git revert 6d8b966` en una rama `fix/revertir-e0-6`, PR a dev y reabrir E0.6.

Reactiva `auto-merge` solo después del paso 5 (checks obligatorios) y de mezclar la salvaguarda:
`gh workflow enable auto-merge.yml -R RareChimera87/ProyectoFinalTecMovil`.

Estado al 22 oct: los workflows están en `.github/workflows/` (rama `feature/ci-cd-pipelines`), con
`actionlint` + `shellcheck` en verde y las pruebas de `review-counter` pasando. **Nada de GCP quedó
aplicado:** el agente preparó y validó el `plan` del bootstrap de dev (WIF, `sa-deployer`, `sa-terraform`,
`sa-terraform-plan`, presupuesto; 50 recursos + importar el bucket), pero el entorno no le permitió ejecutar
`terraform apply`. Tampoco hay todavía secretos de WIF en GitHub. Orden recomendado:

0. **Correcciones de la revisión post-merge (ronda 1).** El PR #11 se mezcló solo el 22 oct porque dev no
   exigía checks. Antes de reactivar el workflow `auto-merge` (hoy `disabled_manually`): mezclar el PR #13
   (el auto-merge no actúa si dev no exige `revision-agente` y `major-gate`) y el PR de la ronda 1
   (`fix/ci-cd-ronda-1`: `--base-ref` en migration-order, `pipefail` en infra-ci, concurrencia de
   review-counter, `revision-agente` con `pull_request_target`, PR que tocan la CI no se mezclan solos). Se
   conserva el commit 6d8b966 (revisión post-merge, sin revertir). Los PR que tocan `.github/workflows/**`,
   `scripts/revision-agente/**` o `scripts/review-counter.py` los marca `major` el workflow `auto-merge` (y desactiva
   cualquier auto-merge previo). **No actives *Require review from Code Owners* en dev mientras haya una sola cuenta
   colaboradora:** CODEOWNERS asigna `*` a RareChimera87, autor de todos los PR, que no puede aprobarse a sí mismo, y con
   `enforce_admins:true` ni el admin podría mezclar. Con una sola cuenta, los PR `major` y los de CI se mezclan con
   `gh pr merge --admin --squash` (requiere `enforce_admins:false` en dev; `major-gate` no pasa sin una segunda cuenta).
   Solo cuando exista un segundo colaborador que apruebe (y CODEOWNERS deje de asignar `*`) tiene sentido exigir Code
   Owners. Los PR #13 y #14 deben mezclarse antes de exigir los checks. Nota de E0.5: `sa-deployer`
   de dev acepta cualquier workflow del repo (`attribute.repository`), incluidos los `pull_request`; conviene
   restringirlo a `ref:refs/heads/dev` en una feature de infra.

1. **Mezclar antes las dependencias** (E0.1, E0.2, E0.3, E0.5, E0.7, E0.8): este PR asume sus archivos
   (`scripts/check-plan.sh`, `backend/`, `android/` con flavors, `firebase/`). Al resolver conflictos:
   - `.github/workflows/infra-ci.yml` (choca con E0.5): quedarse con la versión de E0.6, que es la de E0.5
     más el comentario del plan, el `apply` de dev y el check agregado `infra-ci`.
   - `docs/pendientes-humanos.md`: conservar todas las secciones.

2. **Bootstrap de dev y secretos de WIF** (lo mismo que E0.5, pasos 1 y 2):

   ```bash
   source ~/.parcheu/terraform.env
   cd infra/terraform/bootstrap
   terraform init -reconfigure -backend-config=bucket=parcheu-dev-tfstate -backend-config=prefix=bootstrap
   terraform apply -var environment=dev
   ```

2. **Cargar los secretos de GitHub de dev** con las salidas del bootstrap (desde `infra/terraform/bootstrap`):

   ```bash
   R=RareChimera87/ProyectoFinalTecMovil
   terraform output -raw wif_provider              | gh secret set WIF_PROVIDER -R $R
   terraform output -raw deployer_service_account  | gh secret set WIF_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_plan_service_account | gh secret set TF_PLAN_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_service_account | gh secret set TF_SERVICE_ACCOUNT_DEV -R $R   # apply (E0.6)
   ```

   Luego vuelve a correr el job `infra-ci` del PR de E0.5 (`gh run rerun --failed <id>`): el `plan` de dev vía
   WIF debe quedar verde.

3. **Aplicar la plataforma de dev** (o dejar que lo haga CI cuando E0.6 agregue el `apply` al mezclar).
   Tarda unos 15 min por Cloud SQL. Costo esperado: unos USD 10/mes (Cloud SQL `db-f1-micro`; Cloud Run escala a 0):

   ```bash
   cd infra/terraform/envs/dev
   terraform init && terraform plan -out=plan.tfplan
   terraform show -json plan.tfplan > plan.json
   ../../../../scripts/check-plan.sh plan.json && ../../../../scripts/check-iam.sh plan.json
   terraform apply plan.tfplan
   ../../../../scripts/cargar-secretos.sh dev      # valor de hmac-anon-key
   ```

   Si `google_firebase_project` falla por términos sin aceptar: abre https://console.firebase.google.com,
   "Agregar proyecto" → elige `parcheu-dev` (5 min) y luego
   `terraform import module.platform.google_firebase_project.this projects/parcheu-dev` y repite el `apply`.

4. **Cupo de facturación lleno (bloquea prod).** La cuenta de facturación ya llegó a su cupo de proyectos
   vinculados y Google respondió `Cloud billing quota exceeded`. Antes de crear prod, revisa
   `gcloud billing projects list --billing-account=$TF_VAR_billing_account`, desvincula uno que no uses
   (`gcloud billing projects unlink <id>`) o pide más cupo en
   https://support.google.com/code/contact/billing_quota_increase.

5. **Crear prod y su bootstrap** (solo tú):

   ```bash
   source ~/.parcheu/terraform.env
   BILLING_ACCOUNT=$TF_VAR_billing_account scripts/crear-proyectos.sh prod
   ```

   Si el ID `parcheu-prod` está ocupado el script usa un sufijo (lo deja en `~/.parcheu/proyectos.env`):
   en ese caso cambia el ID en `infra/terraform/bootstrap/main.tf`, `infra/terraform/envs/prod/main.tf` y
   `docs/arquitectura.md` §12 en un PR. Después:

   ```bash
   cd infra/terraform/bootstrap
   terraform init -reconfigure -backend-config=bucket=parcheu-prod-tfstate -backend-config=prefix=bootstrap
   terraform apply -var environment=prod
   R=RareChimera87/ProyectoFinalTecMovil
   terraform output -raw wif_provider              | gh secret set WIF_PROVIDER_PROD -R $R
   terraform output -raw deployer_service_account  | gh secret set WIF_SERVICE_ACCOUNT_PROD -R $R
   terraform output -raw terraform_plan_service_account | gh secret set TF_PLAN_SERVICE_ACCOUNT_PROD -R $R
   ```

6. **Plan y apply de prod (27 oct, solo tú).** El plan de prod se validó con un número de proyecto ficticio
   (64 recursos, `check-plan.sh` y `check-iam.sh` en verde: API con mínimo 1 y máximo 5, backups y PITR,
   push de RTDN); el real necesita que el proyecto exista:

   ```bash
   cd infra/terraform/envs/prod
   terraform init && terraform plan -out=plan.tfplan
   terraform show -json plan.tfplan > plan.json
   ../../../../scripts/check-plan.sh plan.json && ../../../../scripts/check-iam.sh plan.json
   terraform apply plan.tfplan
   ../../../../scripts/cargar-secretos.sh prod
   ```

7. **Antes del primer `migrate` (E0.4), en cada entorno:** dar al usuario IAM de `sa-api` la propiedad de la
   base, como `postgres` desde Cloud SQL Studio (consola → Cloud SQL → `parcheu-<env>` → Cloud SQL Studio):
   `ALTER DATABASE parcheu OWNER TO "sa-api@parcheu-<env>.iam"; ALTER SCHEMA public OWNER TO "sa-api@parcheu-<env>.iam";`
   R=RareChimera87/ProyectoFinalTecMovil
   terraform output -raw wif_provider                   | gh secret set WIF_PROVIDER -R $R
   terraform output -raw deployer_service_account       | gh secret set WIF_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_plan_service_account | gh secret set TF_PLAN_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_service_account      | gh secret set TF_SERVICE_ACCOUNT_DEV -R $R
   ```

   Con eso `infra-ci` hace `plan` en los PR y `apply` de dev al mezclar a dev (la plataforma de dev se
   puede aplicar así, o a mano como dice E0.5 paso 3). Para prod: `WIF_PROVIDER_PROD`,
   `WIF_SERVICE_ACCOUNT_PROD` y `TF_PLAN_SERVICE_ACCOUNT_PROD` (E0.5 paso 5). Ningún secreto es una llave JSON.

3. **`ANTHROPIC_API_KEY`** para `revision-agente` (sin él, el check falla y nada se mezcla). Crea una
   llave en https://platform.claude.com (Settings → API keys), idealmente en un workspace con límite de gasto
   mensual, y cárgala sin dejarla en el historial:

   ```bash
   gh secret set ANTHROPIC_API_KEY -R RareChimera87/ProyectoFinalTecMovil   # pega la llave cuando la pida
   ```

   Costo orientativo: cada push a un PR dispara una revisión con `claude-opus-5-5` (USD 4 / 20 por millón de
   tokens de entrada / salida); un PR mediano cuesta del orden de USD 0,1-0,5 por revisión. Para cambiar
   modelo o esfuerzo: `gh variable set REVISION_MODELO --body claude-sonnet-5-5` y
   `gh variable set REVISION_ESFUERZO --body medium`.

4. **`AUTO_MERGE_TOKEN`** (PAT fine-grained). Sin él el auto-merge funciona, pero el merge hecho con el
   `GITHUB_TOKEN` no dispara `deploy-dev`, el `apply` de `infra-ci` ni `review-counter` (regla de GitHub).
   En https://github.com/settings/personal-access-tokens/new: solo el repo `ProyectoFinalTecMovil`,
   permisos *Contents: Read and write* y *Pull requests: Read and write*, vencimiento 31 dic 2026. Luego
   `gh secret set AUTO_MERGE_TOKEN -R RareChimera87/ProyectoFinalTecMovil`.

5. **Checks obligatorios de dev** (después de mezclar este PR y de que cada check haya corrido una vez;
   si se exigen antes, los PR abiertos sin estos workflows quedan bloqueados):

   ```bash
   CHECKS="pr-title,gitleaks,major-gate,revision-agente,android-ci,backend-ci,contract-ci,infra-ci,rules-ci,migration-order" \
     scripts/proteger-ramas.sh dev
   ```

   Todos los checks de CI reportan en cada PR (si el PR no toca su parte, pasan sin trabajo), así que
   exigirlos no bloquea PR ajenos. Actualiza el valor por defecto de `CHECKS` en `scripts/proteger-ramas.sh`.

6. **Variables opcionales del repo** (`gh variable set ...`):
   - `DUENOS_GITHUB`: usuarios de GitHub de los dueños para `review-counter`, p. ej.
     `{"I1":"RareChimera87","I2":"<usuario-i2>","I3":"<usuario-i3>"}`. Sin I2/I3 el issue se asigna a I1.
   - `DESPLEGAR_API_PROD=true` desde la 0.2 (la 0.1 no usa backend; mientras no esté, el tag no despliega la API).
   - `GCP_PROJECT_PROD` si `parcheu-prod` terminó con sufijo.

7. **Primer `deploy-dev`** (cuando E0.3, E0.4 y la plataforma de dev estén): antes del primer `migrate`, el
   paso 7 de E0.5 (propiedad de la base para `sa-api`). Luego `gh workflow run deploy-dev --ref dev` o un
   merge que toque `backend/`; el humo verifica que `/v1/health` responde con el SHA del commit.

8. **Prueba de aceptación de E0.6** (cuando lo anterior esté): un PR de prueba no major que toque
   `android/` (todo en verde → se mezcla solo), otro que rompa un test de Android (queda bloqueado por
   `android-ci`), uno con etiqueta `major` (espera la aprobación de I3) y, tras 3 merges pequeños, un único
   issue de `review-counter`.

9. **Después de mezclar E0.8:** quitar el disparador `pull_request` de `firebase-deploy.yml` (su job
   `rules-ci` queda duplicado con el workflow `rules-ci` de E0.6).

## E1.3 · Componentes base

1. **Logo oficial de Google para `GoogleSignInButton`:** descargar el recurso "G" de las pautas de marca de
   Google Sign-In (developers.google.com/identity/branding-guidelines), agregarlo como vector drawable en
   `:designsystem` y pasarlo en el parámetro `logo`. Mientras tanto el botón dibuja una "G" provisional.
2. **Revisión lado a lado (ADR-0022):** I2 compara las capturas de `android/designsystem/src/test/screenshots/components/`
   con los PNG de los mockups y registra las diferencias aceptadas en el PR.

## E2.2 · Onboarding y login con Google

1. **Activar el proveedor Google en Firebase Auth de `parcheu-dev`** (es el paso 2 de E0.8; sin él el login no
   funciona). Consola Firebase → `parcheu-dev` → Authentication → Comenzar → Método de acceso → Google →
   Habilitar (correo de asistencia `sanctireinadiaz@gmail.com`). Hoy la API de Identity Toolkit responde
   `CONFIGURATION_NOT_FOUND` (Authentication nunca se inició) y `google-services.json` trae `oauth_client` vacío.
   Después regenera el archivo y el secreto de CI:
   ```bash
   firebase apps:sdkconfig ANDROID 1:388770932007:android:d6f5452d14d98bf1d43502 --project parcheu-dev > ~/.parcheu/google-services.dev.json
   cp ~/.parcheu/google-services.dev.json android/app/src/dev/google-services.json
   base64 -w0 ~/.parcheu/google-services.dev.json | gh secret set GOOGLE_SERVICES_JSON_DEV -R RareChimera87/ProyectoFinalTecMovil
   ```
   Comprueba que el JSON nuevo tenga un `oauth_client` con `client_type: 3` (web): la app lo lee como
   `default_web_client_id` en tiempo de ejecución. Mientras falte, A4 muestra "El inicio de sesión con Google
   todavía no está disponible en esta versión." en lugar de fallar.
2. **API de sesión desplegada en dev:** el login llama `POST /v1/session` y `POST /v1/me/terms` (E2.1, PR #19).
   Hasta que E2.1 esté mezclado y desplegado en `https://api-388770932007.us-central1.run.app/`, el login de
   Google funciona pero A4 muestra el error de red/servidor con "Reintentar".
3. **Verificación manual en el PR (criterio de E2.2):** con 1 y 2 listos, instala el flavor dev
   (`cd android && ./gradlew :app:installDevDebug`) en un teléfono o emulador **con Google Play**, firmado con
   el keystore de debug cuyo SHA-1 está registrado en Firebase. Prueba: A1 → A2 → Saltar → A4 → Continuar con
   Google → elegir cuenta → B0 (`BasicProfile`, usuario nuevo tras aceptar términos). Confirma en Firebase →
   Authentication → Usuarios que aparece la cuenta y, en la base de dev, las filas TERMS y PRIVACY en
   `consents`. Cierra la app y ábrela: A1 debe ir directo al destino según la sesión. Cancela el selector de
   cuentas una vez (A4 sin error) y prueba en modo avión (mensaje y "Reintentar"). Anota el resultado en el PR.
4. **Revisión lado a lado (ADR-0022):** I2 compara `android/app/src/test/screenshots/onboarding/` con los PNG de
   la Sección A y registra las diferencias aceptadas en el PR. Diferencias conocidas: wordmark, ícono y mascota
   están dibujados localmente en `feature/onboarding/OnboardingArt.kt` (E1.2 y E1.5 aún no están en dev) y el
   grafo de A4 es una ilustración fija (el `PuGraph` real es E3.3); el destello de A2 es el carácter "✦" en
   lugar del símbolo `auto_awesome`. Cuando se mezclen E1.2/E1.5, reemplazar esas piezas y volver a grabar las
   capturas (`./gradlew :app:recordRoborazziDevDebug`).
5. **Texto legal:** pide al docente revisar el aviso de A4 ("Al continuar aceptas los Términos y la Política de
   privacidad (Ley 1581 de 2012)…") junto con la revisión legal de E2.1.
