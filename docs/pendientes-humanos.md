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


## E0.4 · Esquema de base de datos v1

No hay pasos de consola, pagos ni identidad. Quedan cuatro cosas a cargo de una persona o de otra feature:

1. **Orden de mezcla.** E0.4 se construyó sobre E0.3 (PR #7), que a su vez integra E0.1 (#2) y E0.2 (#4); la rama los integra con un merge. Mezcla antes esos PR y el diff de E0.4 se reduce a `backend/src/main/resources/db/migration/`, `backend/.../platform/db/`, `scripts/` y docs.
2. **Testcontainers en CI.** `backend-ci` ya existe (E0.6). Tras el merge con `dev`, confirma en su ejecución sobre este PR que los 6 tests de `SchemaContainerTest` aparecen como ejecutados y no omitidos. Localmente pasaron todos (53 de 53) contra un Postgres 16 real vía `PARCHEU_TEST_DB_URL`.
3. **Check `migration-order` (E0.6).** El workflow ya invoca `scripts/check-migration-order.sh origin/dev` (ref base posicional, soportada). Agrega `migration-order` a los checks requeridos de `dev` con `scripts/proteger-ramas.sh`.
4. **Revisar las semillas (I1).** Los textos de las 12 preguntas, los pesos por rasgo, las universidades y los 15 escenarios con sus rúbricas son una primera versión razonable; si el equipo quiere cambiarlos, hazlo **antes** de que V2 se aplique en dev (después exige una migración nueva).
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

## E1.2 Iconos y marca

- [ ] Revisar a ojo el icono de lanzador y el splash en un dispositivo o emulador (Android 13+ para ver la capa monocroma con iconos temáticos). La geometría de la U (5 nodos) se dibujó a partir de la descripción del inventario; si el Claude Design tiene coordenadas distintas, ajustar `ic_launcher_foreground.xml` y `ic_launcher_monochrome.xml`.
- [ ] Confirmar la lista de íconos: se eligió un conjunto representativo (NavBar, app bars, estados, privacidad) sin recorrer cada pantalla del archivo de diseño. Si falta alguno, agregarlo en `designsystem/src/main/res/drawable/` y en `IconCatalogTest`.
## E1.1 · Tokens de diseño

Lo automatizable ya quedó hecho: tokens, fuentes Sora e Inter (OFL) en `android/designsystem/src/main/res/font`, pruebas de afinidad y contraste AA, y capturas Roborazzi de la hoja de tokens en ambos temas (`android/designsystem/src/test/screenshots/token_sheet_{dark,light}.png`).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Revisión visual lado a lado.** Comparar `token_sheet_dark.png` y `token_sheet_light.png` con la hoja de sistema S0 y la Sección I de Claude Design (tokens exactos, tipografía, radios). Diferencias aceptadas por ADR-0030: `ort` #9E480B y `mintt` #0B6B59 en claro, y avatares de claro con alpha > 0,80 pintados en violeta pleno con texto blanco. | I2 | Ventana de revisión de la ola 4 (29 oct) |
| 2 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E1.1 es I3. | I3 | Antes del merge |
| 3 | **Mezclar primero E0.7 (PR #8).** Esta rama parte de `feature/android-skeleton` porque el módulo `:designsystem` nace ahí; el PR de E1.1 muestra también los commits de E0.7 hasta que #8 se mezcle en `dev`. | I2 | Antes del merge de E1.1 |

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


## E0.4 · Esquema de base de datos v1

No hay pasos de consola, pagos ni identidad. Quedan cuatro cosas a cargo de una persona o de otra feature:

1. **Orden de mezcla.** E0.4 se construyó sobre E0.3 (PR #7), que a su vez integra E0.1 (#2) y E0.2 (#4); la rama los integra con un merge. Mezcla antes esos PR y el diff de E0.4 se reduce a `backend/src/main/resources/db/migration/`, `backend/.../platform/db/`, `scripts/` y docs.
2. **Testcontainers en CI.** `backend-ci` ya existe (E0.6). Tras el merge con `dev`, confirma en su ejecución sobre este PR que los 6 tests de `SchemaContainerTest` aparecen como ejecutados y no omitidos. Localmente pasaron todos (53 de 53) contra un Postgres 16 real vía `PARCHEU_TEST_DB_URL`.
3. **Check `migration-order` (E0.6).** El workflow ya invoca `scripts/check-migration-order.sh origin/dev` (ref base posicional, soportada). Agrega `migration-order` a los checks requeridos de `dev` con `scripts/proteger-ramas.sh`.
4. **Revisar las semillas (I1).** Los textos de las 12 preguntas, los pesos por rasgo, las universidades y los 15 escenarios con sus rúbricas son una primera versión razonable; si el equipo quiere cambiarlos, hazlo **antes** de que V2 se aplique en dev (después exige una migración nueva).

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

## E10.2 · Pipeline de release Android

Lo automatizable ya quedó hecho: workflow `.github/workflows/android-release.yml`, firma de release
desde variables de entorno (`UPLOAD_*`), `versionCode` derivado de la ejecución
(`scripts/version-code.sh`, probado en `scripts/test/version-code.test.sh`), Gradle Play Publisher
4.1.1 verificado con AGP 9.4.1, notas en es-419 (`android/app/src/prod/play/release-notes/es-419/default.txt`)
y un AAB `prod` construido en local con la llave de subida (huella `7F:51:…:03:D3`, versionCode 110).
Los environments de GitHub `play` (ramas `dev` y `main` y tags `v*`) y `play-alpha` (tags `v*` y `main`,
con aprobación del usuario) ya están creados; los secretos de firma y de Play van **en ellos**, no en el
repo. La cuenta de servicio de Play es `sa-play` en `parcheu-prod` (ADR-0014, enmienda; la comparte E7.1).
Se creó antes una `play-publisher@parcheu-dev`: **no se usa**; bórrala si existe
(`gcloud iam service-accounts delete play-publisher@parcheu-dev.iam.gserviceaccount.com --project parcheu-dev`).
El agente **no** cargó secretos en GitHub ni creó llaves: el entorno no le permitió materializar credenciales.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Secretos de la llave de subida** (si E10.1/E7.0 no los cargaron; deben quedar en el environment, no en el repo: repite cada comando con `--env play` y con `--env play-alpha`, o `gh secret list --env play` debe mostrar los cuatro): `cd ~/.parcheu && set -a && . ./upload-key.env && set +a && R=RareChimera87/ProyectoFinalTecMovil`, luego (secretos de repo con esos nombres, si existen, bórralos con `gh secret delete`) `base64 -w0 upload-keystore.jks \| gh secret set UPLOAD_KEYSTORE -R $R --env play`, `printf %s "$UPLOAD_KEYSTORE_PASSWORD" \| gh secret set UPLOAD_KEYSTORE_PASSWORD -R $R --env play`, `printf %s "$UPLOAD_KEY_ALIAS" \| gh secret set UPLOAD_KEY_ALIAS -R $R --env play` y `printf %s "$UPLOAD_KEY_PASSWORD" \| gh secret set UPLOAD_KEY_PASSWORD -R $R --env play`. | I3 (o el usuario) | Antes de la primera corrida |
| 2 | **Credenciales de Play, sin llaves (preferido).** (`R=RareChimera87/ProyectoFinalTecMovil`) Con `sa-play` creada en `parcheu-prod` (E7.1) y el bootstrap de WIF aplicado: permite la suplantación (`gcloud iam service-accounts add-iam-policy-binding sa-play@parcheu-prod.iam.gserviceaccount.com --role roles/iam.workloadIdentityUser --member "principalSet://iam.googleapis.com/<pool>/attribute.environment/play"`, y otro con `play-alpha`), y carga `gh secret set WIF_PROVIDER_PROD -R $R --env play` (y `--env play-alpha`) y la variable `gh variable set PLAY_SERVICE_ACCOUNT -R $R --env play --body sa-play@parcheu-prod.iam.gserviceaccount.com` (y `--env play-alpha`). **Excepción temporal si el WIF aún no existe** (enmienda de ADR-0014): `gcloud iam service-accounts keys create ~/.parcheu/sa-play.json --iam-account sa-play@parcheu-prod.iam.gserviceaccount.com && gh secret set PLAY_SERVICE_ACCOUNT_JSON -R $R --env play < ~/.parcheu/sa-play.json` (repite con `--env play-alpha`); borra la llave y el secreto al activar WIF. | El usuario | Antes de la primera corrida |
| 3 | **Invitar la cuenta en Play Console**: *Usuarios y permisos → Invitar usuarios →* `sa-play@parcheu-prod.iam.gserviceaccount.com`; en *Permisos de la app* agrega ParcheU con **Ver información de la app**, **Lanzar a pistas de prueba** y **Administrar pistas de prueba y editar listas de testers**. Sin acceso a producción. El permiso puede tardar hasta 24 h en propagarse. | I3 | Después de la primera subida manual de E7.0 (28 oct) |
| 4 | **Primera corrida (criterio de aceptación; se valida desde `dev`).** El workflow solo publica desde `dev`, `main` o un tag (`release-params.sh` rechaza cualquier otra rama, incluida la de la feature), así que no se prueba antes de mezclar: el PR sale de borrador, se mezcla a `dev` con las pruebas en verde y la **primera corrida real se hace desde `dev`**. Con la 0.0.1 ya en la pista interna: `gh workflow run android-release --ref dev -f pista=internal`. Debe terminar en verde y aparecer en *Pruebas internas* una versión `0.0.1-int.<n>` con versionCode `100 + n×10`. Si Play responde *"Only releases with status draft may be created on draft app"*, repite con `-f estado=draft` y lanza la versión desde la consola. Corre una segunda vez y confirma que el versionCode subió. **Deja el enlace de ambas corridas en el PR de E10.2 como evidencia de cierre.** | I3 | 28-31 oct, tras mezclar a `dev` |
| 5 | **Disparo tras `deploy-prod`:** `workflow_run` solo se activa con la copia del workflow en `main`; queda activo cuando `release/0.1.0` llegue a `main`. Un tag `vX.Y.Z` sube a la pista cerrada (`alpha`). Si la pista cerrada de Play tiene otro identificador (p. ej. una pista personalizada), cambia `alpha` en `scripts/release-params.sh` y en `build.gradle.kts` si hace falta; la corrida pide la aprobación del environment `play-alpha`. **Antes del primer tag, agrega `main` a las políticas de ramas de `play-alpha`** (`gh api -X POST repos/$R/environments/play-alpha/deployment-branch-policies -f name=main -f type=branch`): en `workflow_run` la ref es `main`, no el tag, y sin esa política el job falla con *Branch main is not allowed to deploy to play-alpha*. Seguirá exigiendo tu aprobación y `release-params.sh` valida tag y commit. | I3 | Con el primer tag |
| 6 | **Opcional:** variable `UPLOAD_CERT_SHA256` del repo si se cambia la llave de subida (por defecto se exige la huella de `docs/play-console.md`). | I3 | Solo si se rota la llave |
| 7 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E10.2 es I1. | I1 | Ventana de la ola 5 (31 oct) |
## E10.3 · Prueba cerrada temprana (0.1)

Lo automatizable ya quedó hecho: guía completa en [`docs/play-console.md` §12](play-console.md) (proceso de release,
comandos de Firebase probados contra `parcheu-dev`, respuestas de Contenido de la app, borrador de Data safety,
cuestionario IARC, texto para testers, notas de versión y cálculo de los 14 días). Casi todo lo demás es consola de
Play y aprobaciones del usuario. Al 22 oct `parcheu-prod` no existe, así que nada de prod se pudo hacer por CLI.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar las dependencias en `dev`:** E0.7 (#8), E0.8 (#6), E8.4 (#16), E7.0 (#17) y, si se puede, E10.2. Al mezclar las ramas que crean `docs/play-console.md` (E10.1, E7.0, E8.4 y esta), conservar todas las secciones. | usuario / revisores | Antes del 28 oct |
| 2 | **Crear `parcheu-prod` y aplicar Terraform de prod**; registrar la app Android de prod en Firebase (pendientes de E0.5 y E0.8). | usuario | 27 oct |
| 3 | **Rama `release/0.1.0`** desde `dev` y `fix/version-0.1.0` con `versionName = "0.1.0"` y `versionCode` > 1 (§12.3 pasos 1-2). Si E10.2 está mezclado, `versionName` sale del tag y esta rama no toca `defaultConfig`. | I3 | 28 oct |
| 4 | **Contenido de la app y ficha, ANTES del tag:** privacidad, sin anuncios, acceso, IARC, 18+, Data safety con §12.5-§12.7; ficha mínima e instrucciones de §12.8. Notas de versión en `android/app/src/prod/play/release-notes/es-419/default.txt` dentro de `release/0.1.0`. Activar la **publicación administrada** (o subida en `draft`). Verificar en la ayuda de Play si 'Historial de compras' aplica a la 0.1. | I3 | 28 oct |
| 5 | **Aprobar el PR `release/0.1.0` → `main`**, crear el tag `v0.1.0` y aprobar `deploy-prod` (Hosting). El tag encadena `android-release`, que sube el AAB a la pista cerrada (o plan B manual de §12.3 paso 8). Verificar 200 en `/privacidad`, `/terminos` y `/borrar-cuenta` de prod (§12.3 paso 7). | usuario / I3 | 28-29 oct |
| 6 | **Enviar a revisión a mano** la versión subida y registrar la fecha en §12.11. | I3 | 29 oct |
| 7 | **Huellas de Play App Signing en Firebase prod** (SHA-1 y SHA-256 del certificado de firma de la app) con los comandos de §12.4; recargar `GOOGLE_SERVICES_JSON_PROD`; captura en el PR. | I3 | 28-29 oct (tras la subida de E7.0) |
| 8 | **Tras la aprobación, pulsar 'Publicar cambios'** (la publicación administrada retiene la versión), registrar la fecha en §12.11 y desactivar la publicación administrada para no retener la 0.2 y la 0.3. **Solo entonces** enviar el enlace de inscripción, contar inscritos (≥ 12) y calcular la fecha de los 14 días (día 1 = primer día con la 0.1 publicada y ≥ 12 inscritos, §12.9). Conteo diario hasta el ≈ 14 nov. | I3 | 30-31 oct en adelante |
| 9 | **Prueba de instalación** desde la pista cerrada (requiere la 0.1 ya publicada, paso 8): abre la app y A4 lleva a `/terminos` y `/privacidad` de prod (§12.10); capturas en el PR. | I3 | Tras la aprobación |
| 10 | **Aprobación del revisor par** del PR (feature major): según `docs/equipo.md` el par de E10.3 es I1. | I1 | Revisión del PR |
## E4.1 · Servicio de cuotas

Lo automatizable ya quedó hecho: `QuotaService` (límites gratis y uso justo de Plus), `usage_events` idempotente con devolución, `GET /v1/quota`, la interfaz `EntitlementService` (stub 'gratis' en prod hasta E7.1) y `POST /internal/dev/grant-plus` solo fuera de prod. Las pruebas con Postgres (`QuotaDbTest`, incluida la de concurrencia) se omiten en local con `-PsinContenedores` y corren en CI.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar primero E0.3 (PR #7) y E0.4 (PR #12).** Esta rama integra `feature/db-schema-v1` porque necesita las tablas `usage_events`, `entitlements` y `users`; el PR de E4.1 muestra también esos commits hasta que se mezclen en `dev`. | I1 | Antes del merge de E4.1 |
| 2 | **Conflicto esperado con E2.1 (PR #19)** en `AppContainer.kt` y `TestSupport.kt`: ambos agregan un parámetro al contenedor y a `allDomains(...)`. Al mezclar el segundo, conservar los dos (`identity` y `quota`). Los cambios en `Database.kt` (`failFast`) y en `PlatformTest` son idénticos a los de E2.1 y no deberían chocar. Cuando E2.1 esté en `dev`, `QuotaUserLookup` puede implementarse con su repositorio. | I1 | Al mezclar el segundo de los dos |
| 3 | **Revisar los valores de uso justo de Plus** (por defecto 20 simulaciones, 10 matches al azar y 20 informes completos por día) y, si se cambian, fijarlos como variables del servicio `api` en Terraform: `QUOTA_PLUS_DAILY_SIMULATIONS`, `QUOTA_PLUS_DAILY_RANDOM_MATCHES`, `QUOTA_PLUS_DAILY_FULL_REPORTS`. Sin variables se usan los valores por defecto. | I1 | Antes de la 0.2 |
| 4 | **Probar `grant-plus` en dev** (opcional). La ruta está bajo `/internal`, así que exige el token OIDC de `sa-tasks`. Quien la use necesita `roles/iam.serviceAccountTokenCreator` sobre `sa-tasks@parcheu-dev.iam.gserviceaccount.com`. Luego: `TOKEN=$(gcloud auth print-identity-token --impersonate-service-account=sa-tasks@parcheu-dev.iam.gserviceaccount.com --audiences=https://api.parcheu-dev.internal --include-email)` y `curl -X POST "$API_URL/internal/dev/grant-plus" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"userId":"<users.id>","days":30}'` (204). En prod la ruta no existe (404). | I1 | Cuando haya `deploy-dev` |
| 5 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E4.1 es I3. | I3 | Ventana de revisión de la ola 5 (31 oct) |
## E0.9 · Pasarela de IA

Lo automatizable ya quedó hecho: API `aiplatform.googleapis.com` habilitada en `parcheu-dev`, spike de modelos con llamadas reales (resultado en ADR-0008), IDs y precios en `backend/src/main/resources/ai/modelos.properties`, y el humo `@Tag("vertex")` pasó contra Vertex dev con `gemini-3.8-flash` y `gemini-3.5-flash-lite` el 22 oct 2026.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **¿Los créditos cubren a Claude?** En la consola: Facturación → cuenta `01D4D0-42F08D-DF5FE6` → **Créditos**; abrir el crédito vigente y revisar su **alcance** (servicios/SKU incluidos). Si dice "todos los servicios excepto Marketplace/partner models" o lista solo SKU de Google, Claude **no** está cubierto. Anotar la respuesta en ADR-0008 ("Resultado del spike", línea "¿Los créditos promocionales cubren a Claude?"). | I1 | Antes del 30 oct (fecha límite del spike) |
| 2 | **Solo si los créditos cubren a Claude y se quiere usar:** en Model Garden (`parcheu-dev`) abrir Claude Sonnet 5.5 y Claude Haiku 5.5, pulsar **Habilitar** y aceptar los términos de Anthropic; luego en IAM y administración → **Cuotas** pedir aumento de `us_multi_region_online_prediction_requests_per_base_model` para `anthropic-claude-sonnet` y `anthropic-claude-haiku` (hoy 0 → 429). Verificar con `AI_PROVIDER=claude ./gradlew test -Pvertex -PsinContenedores --tests '*VertexSmokeTest*'` desde `backend/`. Repetir en `parcheu-prod` si se activa allá. | I1 | Opcional |
| 3 | **Habilitar Vertex en prod:** la API `aiplatform.googleapis.com` ya está en la lista de E0.5; se habilita con el `terraform apply` de prod (lo hace el usuario). La cuenta `sa-api` recibe `roles/aiplatform.user` por Terraform (E0.5): mientras el apply de dev no exista, el servicio de dev no podrá llamar a Vertex. | Usuario | Con el apply de E0.5 |
| 4 | **Presupuesto diario de prod:** el valor por defecto es 3 USD/día (`budget.daily.usd`). Si se quiere otro, agregar `AI_DAILY_BUDGET_USD` al `run_env` de `infra/terraform/modules/platform/cloud_run.tf` (E0.5) o ajustar el archivo de propiedades. No hace falta ninguna otra variable `AI_*`: `GCP_PROJECT` ya la pone Terraform y los modelos salen del archivo. | I1 | Antes del primer despliegue con IA |
| 5 | **Revisar precios el 1 ene 2027:** el precio introductorio de `gemini-3.8-flash` termina el 31 dic 2026; la tabla ya trae la fila de 2027 (1,65/8,25), pero conviene confirmar en la página de precios que no cambió. | I1 | Diciembre 2026 |
| 6 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E0.9 es I2. **Mezclar antes E0.4 (PR #12)**: esta rama lo integra porque `ai_usage` nace ahí. | I2 | Antes del merge |

## E2.3 · API del gemelo

El código y las pruebas (incluidas las de Postgres real, ejecutadas localmente contra un Postgres 16 desechable) están completos. Lo único que falta es de infraestructura y de revisión:

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Agregar `SERVICE_URL` al `run_env` de `infra/terraform/modules/platform/cloud_run.tf` (E0.5)** con la URL del servicio Cloud Run `api` (`google_cloud_run_v2_service.api.uri`). Cloud Tasks necesita la URL real a la que hacer el POST de `/internal/tasks/build-persona`; la audiencia OIDC (`INTERNAL_OIDC_AUDIENCE`) es una custom audience y no sirve como destino. Sin `SERVICE_URL` (o sin `TASKS_QUEUE_AI_LIGHT`) el servicio arranca, guarda las respuestas y registra `Cloud Tasks no está configurado` al encolar: el gemelo queda con `personaReady = false` hasta que se configuren y se reenvíen las respuestas. | I1 | Antes del primer despliegue con E2.3 |
| 2 | **Verificar en dev tras el `apply` de E0.5:** `POST /v1/twin/answers` con 12 respuestas de un usuario real, esperar unos segundos y comprobar que `GET /v1/twin` devuelve `personaReady = true` (la tarea llega a `ai-light` y `sa-tasks` invoca la API). Requiere que Vertex esté habilitado (pendiente 3 de E0.9). | I1 | Después del `apply` de dev |
| 3 | **Mezclar antes las dependencias.** Esta rama integra E0.9 (que a su vez trae E0.4 y E0.3) porque `dev` aún no las tiene. Mezclar en este orden: E0.3 (PR #7), E0.4 (#12), E0.9 (#21) y luego E2.3; al mezclar, el diff del PR queda solo con los archivos de E2.3. | I1 | Antes del merge |
## E2.1 · API de sesión y usuarios

No hay pasos bloqueantes: la feature no crea recursos en GCP, Firebase ni consolas.

1. **Texto legal vigente (`LEGAL_TEXT_VERSION`).** Por defecto es `2026-10-29` (constante en `AppConfig`). Cuando E8.4 publique
   las páginas `/privacidad` y `/terminos`, confirma que su versión sea exactamente esa o cambia el valor: o bien se actualiza
   `DEFAULT_LEGAL_TEXT_VERSION`, o bien se define la variable `LEGAL_TEXT_VERSION` en el servicio de Cloud Run (Terraform de E0.5).
   Cada cambio de versión devuelve a todos los usuarios a `NEEDS_TERMS` (es lo que exige la Ley 1581).
2. **Variables de la base en el servicio.** Desde E2.1 el modo `serve` exige `DB_NAME` y `CLOUD_SQL_INSTANCE`. Antes del primer
   `deploy-dev` verifica que el servicio de Cloud Run las recibe (hoy solo las usa el job `migrate`).
3. **Revisión legal.** Pide al docente o a un abogado revisar el texto de aceptación de A4 y el plazo de conservación de
   `consents` (5 años, arquitectura.md §10.2) antes de la prueba cerrada.

## E5.3 · Push con FCM

El código, los tests y las rutas están hechos; lo siguiente solo lo puedes hacer tú o depende de piezas que aún no se mezclan.

1. **Permiso de FCM para la cuenta del API.** Cuando exista `sa-api` en `parcheu-dev` (E0.5), debe tener el rol `roles/firebasecloudmessaging.admin` (envío con `messages.create`). Si E0.5 no lo concede en Terraform, agrégalo allí. En `parcheu-dev` las APIs `fcm.googleapis.com` y `firebase.googleapis.com` ya están habilitadas.
2. **Verificación manual del push real en el flavor dev** (criterio de la ficha, no se pudo hacer sin dispositivo ni backend desplegado):
   1. Descargar `google-services.json` de dev con `scripts/fetch-google-services.sh` y compilar `:app:installDevDebug` en un dispositivo o emulador con Play Services (API 33+ para ver el permiso).
   2. Iniciar sesión (E2.2), llegar a Inicio y aceptar el permiso de notificaciones; el token se registra con `POST /v1/me/fcm-token`.
   3. Con el backend de dev desplegado, enviar un aviso de prueba (por ejemplo desde un test de integración o un Cloud Run Job) con `Notifier.notify(userId, Notification(kind = "ACCOUNT", title = "Prueba", body = "Hola"))`, o con curl a la API de FCM v1 mandando `data: {type, channel, title, body, route}`.
   4. Comprobar que llega con la app cerrada, que se ve en el canal correcto (Ajustes > Notificaciones) y que al tocarla abre la pantalla de la tabla de arquitectura §8.4.
   5. Pegar el resultado (captura) en el PR de E5.3.
3. **Integración con otras features que aún no se mezclan:** E2.2 debe llamar `PushTokenRegistrar.sync()` justo después de iniciar sesión (ya se llama al llegar a Inicio, pero conviene también tras el login) y E8.2 debe llamar `PushTokenRegistrar.unregister()` en el cierre de sesión.
4. **`TokenProvider.currentUserId()` (E2.2).** Es abstracto: el `FirebaseTokenProvider` de E2.2 debe implementarlo devolviendo `FirebaseAuth.currentUser?.uid`. Sin eso no compila; no se deja un valor por defecto para que el push no se apague en silencio (`PushTokenRegistrar` no llama a `POST /v1/me/fcm-token` sin UID). Sin esto la verificación manual del punto 2 no se puede hacer.
## E3.1 · API del grafo

La feature no crea recursos en GCP; solo necesita que exista el valor del secreto de E0.5.

1. **Valor de `hmac-anon-key` en dev y prod.** `GET /v1/graph` enmascara a las personas sin conexión mutua con
   `anonId = HMAC(clave, observador || persona)`. El servicio lee la clave del secreto que indica `SECRET_HMAC_ANON_KEY`
   (variable que ya define `modules/platform/cloud_run.tf` de E0.5). Tras el `terraform apply` de cada entorno ejecuta
   `scripts/cargar-secretos.sh` (dev: I1; prod: el usuario) para crear la primera versión. Sin valor, el grafo responde 500.
   Verifica con: `gcloud secrets versions list hmac-anon-key --project=<proyecto>` (debe haber una versión `enabled`).
2. **No rotar la clave en prod sin avisar.** Rotarla cambia todos los `anonId`: los ids anónimos que el cliente tenga en
   caché (DataStore del último grafo) dejan de resolver en `GET /v1/graph/nodes/{id}` hasta refrescar el grafo.
3. **Permiso de lectura.** La cuenta de servicio del API necesita `roles/secretmanager.secretAccessor` sobre `hmac-anon-key`
   (E0.5 lo declara como `api_accessor`); compruébalo tras el primer despliegue en dev llamando a `GET /v1/graph`.


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

## E7.0 · Billing mínimo y primera subida

Lo automatizable ya quedó hecho: `billing-ktx` 8.3.0, `BillingClientProvider` con tests, verificación del permiso `com.android.vending.BILLING` en `prodRelease` (también en `android-ci`) y el **AAB 0.0.1 `prod` firmado con la llave de subida** en `~/.parcheu/parcheu-0.0.1-prod-release.aab` (fuera del repo). Detalle paso a paso en [play-console.md §11](play-console.md).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | Requisitos de E10.1: app creada, perfil de pagos verificado e identidad aprobada (sin eso Play no deja crear suscripciones) | I3 | Antes del 28 oct |
| 2 | Subir **a mano** `~/.parcheu/parcheu-0.0.1-prod-release.aab` a *Pruebas internas* con Play App Signing (llave generada por Google) y lanzar la versión; registrar la fecha en `play-console.md` §11.3 | I3 | **28 oct** |
| 3 | Copiar las huellas SHA-1/SHA-256 del certificado de **firma de la app** (Integridad de la app) a `play-console.md` §11.3 para Firebase prod (E10.3) | I3 | 28 oct |
| 4 | Crear la suscripción `parcheu_plus` con planes base `mensual` (P1M, COP 5.000) y `anual` (P1Y, COP 45.000) y **activarlos** | I3 | 28 oct |
| 5 | Agregar ≥ 3 license testers (los 3 integrantes) en *Pruebas de licencia* | I3 | 28 oct |
| 6 | `terraform apply` de prod (crea `play-rtdn` y el permiso de publicación de Play); luego configurar RTDN con `projects/<ID de parcheu-prod>/topics/play-rtdn` y enviar el mensaje de prueba | usuario (apply) / I3 (consola) | 27-28 oct |
| 7 | Adjuntar al PR de E7.0 la captura de los planes base activos y de RTDN, sin correos ni datos personales | I3 | 28-29 oct |
| 8 | Aprobación del revisor par (feature major, `major-gate`): según `docs/equipo.md` el par de E7.0 es I1 | I1 | Ventana de revisión del 29 oct |
| 9 | **Mezclar antes E10.1 (PR #1)** o junto con este: esta rama mezcla `feature/play-console-setup` porque completa su `docs/play-console.md`; el PR de E7.0 muestra también el commit de E10.1 | I3 | Antes del merge de E7.0 |
## E8.4 · Páginas web legales

Lo automatizable ya quedó hecho: `/privacidad`, `/terminos`, `/borrar-cuenta`, respaldo de `/i/{codigo}` y `assetlinks.json` vacío en `firebase/public/`, y `scripts/test/legal-pages.sh` (emulador de Hosting) con su workflow `legal-pages-ci`.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Revisar los textos.** Los redactó el agente; no son asesoría legal. Confirmar responsable, datos recolectados, plazos (consultas 10 días hábiles, reclamos 15) y la lista de la tabla de retención (los plazos marcados 'verificar' en arquitectura.md §10.2, p. ej. 5 años de consentimientos). | I3 con el docente | Antes del 29 oct |
| 2 | **Correo de contacto.** Las páginas usan `sanctireinadiaz@gmail.com` como canal de derechos y de borrado. Es público una vez desplegado. Si se prefiere un correo dedicado, reemplazar en `firebase/public/*.html` (`grep -rl sanctireinadiaz firebase/public`). | I3 | Antes de mezclar |
| 3 | **Orden de mezcla con E0.8.** `firebase/firebase.json` y `firebase-deploy` llegan con `feature/firebase-setup`; mientras E0.8 no esté en `dev`, los textos no se despliegan. Para `/i/{codigo}` agregar en `firebase.json` el rewrite `{"source": "/i/**", "destination": "/i/index.html"}`, y para `assetlinks.json` quitar `**/.*` de `hosting.ignore` (o permitir `.well-known`); ambos cambios son de E0.8/E6.1 y no los hace este PR. `legal-pages.sh` ya prueba contra `firebase/firebase.json` real y marca `OMITIDO` esas dos rutas mientras falten las reglas; al agregarlas se prueban solas. **Mezclar #6 (E0.8) antes que este PR**: `rules-ci` y `rules-verificar` fallan aquí porque `dev` aún no tiene `firebase/package-lock.json`; tras mezclar #6, rebasar `feature/privacy-web` sobre `dev` y volver a correr la CI. | I3 | Con E0.8 / E6.1 |
| 4 | **Comprobar dev tras mezclar:** `curl -sI https://parcheu-dev.web.app/privacidad` debe dar 200 (si el sitio de Hosting de dev tiene otro ID, ajustar la URL). | I3 | Tras mezclar a `dev` |
| 5 | **Prod:** con el tag `v0.1.0` y la aprobación del environment `production`, `firebase-deploy` publica en `https://parcheu-prod.web.app`. Verificar 200 y pegar `/privacidad` y `/borrar-cuenta` en Play Console (Contenido de la app y Seguridad de los datos). | I3 | 28-29 oct |
| 6 | **`LEGAL_TEXT_VERSION`.** E2.1 debe definirla como `2026-10-29` (la versión de los textos publicados); si se cambian los textos, subir ambas. | I1 (E2.1) | Con E2.1 |
| 7 | **Etiqueta del botón de borrado.** `/borrar-cuenta` indica `Perfil > Ajustes > Privacidad > Borrar mi cuenta`, pero el mockup H3 dice 'Borrar mi gemelo y mis datos'. Fijar una sola etiqueta en la ficha de E8.3/E9.x y, si cambia, actualizar `firebase/public/borrar-cuenta.html` (Play exige que la ruta descrita exista en la app). | I3 | Con E8.3 / E9.x |
## E1.3 · Componentes base

1. **Logo oficial de Google para `GoogleSignInButton`:** descargar el recurso "G" de las pautas de marca de
   Google Sign-In (developers.google.com/identity/branding-guidelines), agregarlo como vector drawable en
   `:designsystem` y pasarlo en el parámetro `logo`. Mientras tanto el botón dibuja una "G" provisional.
2. **Revisión lado a lado (ADR-0022):** I2 compara las capturas de `android/designsystem/src/test/screenshots/components/`
   con los PNG de los mockups y registra las diferencias aceptadas en el PR.

## E1.4 · Componentes compuestos

Lo automatizable ya quedó hecho: los 25 compuestos en `android/designsystem/.../components/compound/`, pruebas de semántica, de LockedOverlay (sdk 30 y 35) y de avatares, y capturas Roborazzi en ambos temas (`android/designsystem/src/test/screenshots/compound/`).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar primero E1.3 (PR #20).** Esta rama parte de `feature/components-base` porque los compuestos reutilizan los botones, tarjetas y `touchTarget()` de E1.3; el PR muestra también su commit hasta que #20 se mezcle en `dev`. | I2 | Antes del merge de E1.4 |
| 2 | **Revisión visual lado a lado.** Comparar las capturas de `screenshots/compound/*.png` con los mockups B-H de Claude Design (radios, paddings, colores de burbujas, anillo de afinidad, QR). Diferencias aceptadas: el QR de las capturas es sintético (el codificador llega con E6.1) y los iconos son círculos de prueba hasta que E1.2 se mezcle. | I2 | Ventana de revisión de la ola 6 |
| 3 | **Prueba del blur en un dispositivo real.** Robolectric no renderiza `RenderEffect`, así que el desenfoque de `LockedOverlay` solo se verifica por rama (etiqueta `locked_overlay_blur`) y no por píxeles. Abrir una pantalla que lo use (D7 o F7, cuando existan) en un dispositivo/emulador con API >= 31 y en otro con API < 31 y confirmar blur y scrim opaco respectivamente. | I2 | Cuando exista la primera pantalla con LockedOverlay |
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
   **Prerrequisito de legales (E8.4):** antes del paso 3, mezcla E8.4 (PR #16) y despliega Hosting en dev
   (`firebase deploy --only hosting --project parcheu-dev`). Comprueba que `curl -s -o /dev/null -w '%{http_code}'
   https://parcheu-dev.web.app/terminos` y `/privacidad` den 200: si no, las filas TERMS y PRIVACY quedarían
   registradas (Ley 1581, ADR-0017) sobre un texto que la persona no pudo leer.
3. **Verificación manual en el PR (criterio de E2.2):** con 1 y 2 listos y los enlaces legales en 200, instala el flavor dev
   (`cd android && ./gradlew :app:installDevDebug`) en un teléfono o emulador **con Google Play**, firmado con
   el keystore de debug cuyo SHA-1 está registrado en Firebase. Prueba: A1 → A2 → Saltar → A4 → Continuar con
   Google → elegir cuenta → B0 (`BasicProfile`, usuario nuevo tras aceptar términos). Confirma en Firebase →
   Authentication → Usuarios que aparece la cuenta y, en la base de dev, las filas TERMS y PRIVACY en
   `consents`. Cierra la app y ábrela: A1 debe ir directo al destino según la sesión. Cancela el selector de
   cuentas una vez (A4 sin error) y prueba en modo avión (mensaje y "Reintentar"). Anota el resultado en el PR.
4. **Revisión lado a lado (ADR-0022):** I2 compara `android/app/src/test/screenshots/onboarding/` con los PNG de
   la Sección A y registra las diferencias aceptadas en el PR. Diferencias conocidas: wordmark, ícono y mascota
   están dibujados localmente en `feature/onboarding/OnboardingArt.kt` (E1.2 y E1.5 aún no están en dev) y el
   grafo de A4 usa el `PuGraph` de E3.3 con datos fijos de ejemplo; el destello de A2 es el carácter "✦" en
   lugar del símbolo `auto_awesome`. Cuando se mezclen E1.2/E1.5, reemplazar esas piezas y volver a grabar las
   capturas (`./gradlew :app:recordRoborazziDevDebug`).
5. **Texto legal:** pide al docente revisar el aviso de A4 ("Al continuar aceptas los Términos y la Política de
   privacidad (Ley 1581 de 2012)…") junto con la revisión legal de E2.1.

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

## E7.0 · Billing mínimo y primera subida

Lo automatizable ya quedó hecho: `billing-ktx` 8.3.0, `BillingClientProvider` con tests, verificación del permiso `com.android.vending.BILLING` en `prodRelease` (también en `android-ci`) y el **AAB 0.0.1 `prod` firmado con la llave de subida** en `~/.parcheu/parcheu-0.0.1-prod-release.aab` (fuera del repo). Detalle paso a paso en [play-console.md §11](play-console.md).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | Requisitos de E10.1: app creada, perfil de pagos verificado e identidad aprobada (sin eso Play no deja crear suscripciones) | I3 | Antes del 28 oct |
| 2 | Subir **a mano** `~/.parcheu/parcheu-0.0.1-prod-release.aab` a *Pruebas internas* con Play App Signing (llave generada por Google) y lanzar la versión; registrar la fecha en `play-console.md` §11.3 | I3 | **28 oct** |
| 3 | Copiar las huellas SHA-1/SHA-256 del certificado de **firma de la app** (Integridad de la app) a `play-console.md` §11.3 para Firebase prod (E10.3) | I3 | 28 oct |
| 4 | Crear la suscripción `parcheu_plus` con planes base `mensual` (P1M, COP 5.000) y `anual` (P1Y, COP 45.000) y **activarlos** | I3 | 28 oct |
| 5 | Agregar ≥ 3 license testers (los 3 integrantes) en *Pruebas de licencia* | I3 | 28 oct |
| 6 | `terraform apply` de prod (crea `play-rtdn` y el permiso de publicación de Play); luego configurar RTDN con `projects/<ID de parcheu-prod>/topics/play-rtdn` y enviar el mensaje de prueba | usuario (apply) / I3 (consola) | 27-28 oct |
| 7 | Adjuntar al PR de E7.0 la captura de los planes base activos y de RTDN, sin correos ni datos personales | I3 | 28-29 oct |
| 8 | Aprobación del revisor par (feature major, `major-gate`): según `docs/equipo.md` el par de E7.0 es I1 | I1 | Ventana de revisión del 29 oct |
| 9 | **Mezclar antes E10.1 (PR #1)** o junto con este: esta rama mezcla `feature/play-console-setup` porque completa su `docs/play-console.md`; el PR de E7.0 muestra también el commit de E10.1 | I3 | Antes del merge de E7.0 |

## E7.2 · Billing en el cliente

Lo automatizable ya quedó hecho: `BillingRepository` (planes desde `ProductDetails`, compra con `obfuscatedAccountId = sha256(userId)`, `verify` → entitlement, compras pendientes, restaurar y deep link a la gestión en Play), `FakeBillingRepository` para E7.3 y tests JVM de todos los criterios automatizables. Lo que sigue necesita una persona, un dispositivo con Play Store y la consola.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar antes E7.0 (PR #17)** o junto con este: la rama `feature/billing-client` mezcla `feature/billing-bootstrap` (y por ella E10.1) porque parte de su `BillingClientProvider`; el PR de E7.2 muestra también esos commits | I3 | Antes del merge de E7.2 |
| 2 | Con la versión **0.2** de la pista cerrada, instalada desde Play con una cuenta license tester: abrir una pantalla que llame a `loadPlans()` (o un build de depuración con un botón temporal, sin commitearlo) y confirmar que conecta y que llegan `mensual` ($5.000) y `anual` ($45.000) con '$3.750/mes' calculado. Requiere que `parcheu_plus` y sus dos planes estén **activos** (E7.0, paso 4) | I3 | Al publicar la 0.2 |
| 3 | Con la versión **0.3 (13 nov)**, ya con E7.1 (verify en el servidor) y E7.3 (pantallas): comprar `anual` y `mensual` con license tester (tarjeta de prueba "siempre aprueba"), cancelar la hoja de Play (no debe mostrar error), probar "aprueba con retraso" (debe verse 'verificación pendiente' y luego Plus), restaurar desde G4 y abrir "Cancelar en Google Play". Probar también la conciliación: con "aprueba con retraso", cerrar la app antes de que Play confirme, esperar y reabrirla (sin tocar Restaurar) y comprobar en el servidor que la compra quedó verificada y reconocida; repetir con el teléfono en modo avión durante la compra Registrar el resultado en el PR de E7.3 sin correos ni datos personales | I3 (con I1 e I2) | 13-14 nov |
| 4 | Aprobación del revisor par (feature major, `major-gate`): según `docs/equipo.md` el par de E7.2 es I1 | I1 | Ventana de revisión del 8 nov |
## E0.9 · Pasarela de IA

Lo automatizable ya quedó hecho: API `aiplatform.googleapis.com` habilitada en `parcheu-dev`, spike de modelos con llamadas reales (resultado en ADR-0008), IDs y precios en `backend/src/main/resources/ai/modelos.properties`, y el humo `@Tag("vertex")` pasó contra Vertex dev con `gemini-3.8-flash` y `gemini-3.5-flash-lite` el 22 oct 2026.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **¿Los créditos cubren a Claude?** En la consola: Facturación → cuenta `01D4D0-42F08D-DF5FE6` → **Créditos**; abrir el crédito vigente y revisar su **alcance** (servicios/SKU incluidos). Si dice "todos los servicios excepto Marketplace/partner models" o lista solo SKU de Google, Claude **no** está cubierto. Anotar la respuesta en ADR-0008 ("Resultado del spike", línea "¿Los créditos promocionales cubren a Claude?"). | I1 | Antes del 30 oct (fecha límite del spike) |
| 2 | **Solo si los créditos cubren a Claude y se quiere usar:** en Model Garden (`parcheu-dev`) abrir Claude Sonnet 5.5 y Claude Haiku 5.5, pulsar **Habilitar** y aceptar los términos de Anthropic; luego en IAM y administración → **Cuotas** pedir aumento de `us_multi_region_online_prediction_requests_per_base_model` para `anthropic-claude-sonnet` y `anthropic-claude-haiku` (hoy 0 → 429). Verificar con `AI_PROVIDER=claude ./gradlew test -Pvertex -PsinContenedores --tests '*VertexSmokeTest*'` desde `backend/`. Repetir en `parcheu-prod` si se activa allá. | I1 | Opcional |
| 3 | **Habilitar Vertex en prod:** la API `aiplatform.googleapis.com` ya está en la lista de E0.5; se habilita con el `terraform apply` de prod (lo hace el usuario). La cuenta `sa-api` recibe `roles/aiplatform.user` por Terraform (E0.5): mientras el apply de dev no exista, el servicio de dev no podrá llamar a Vertex. | Usuario | Con el apply de E0.5 |
| 4 | **Presupuesto diario de prod:** el valor por defecto es 3 USD/día (`budget.daily.usd`). Si se quiere otro, agregar `AI_DAILY_BUDGET_USD` al `run_env` de `infra/terraform/modules/platform/cloud_run.tf` (E0.5) o ajustar el archivo de propiedades. No hace falta ninguna otra variable `AI_*`: `GCP_PROJECT` ya la pone Terraform y los modelos salen del archivo. | I1 | Antes del primer despliegue con IA |
| 5 | **Revisar precios el 1 ene 2027:** el precio introductorio de `gemini-3.8-flash` termina el 31 dic 2026; la tabla ya trae la fila de 2027 (1,65/8,25), pero conviene confirmar en la página de precios que no cambió. | I1 | Diciembre 2026 |
| 6 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E0.9 es I2. **Mezclar antes E0.4 (PR #12)**: esta rama lo integra porque `ai_usage` nace ahí. | I2 | Antes del merge |

## E2.3 · API del gemelo

El código y las pruebas (incluidas las de Postgres real, ejecutadas localmente contra un Postgres 16 desechable) están completos. Lo único que falta es de infraestructura y de revisión:

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Agregar `SERVICE_URL` al `run_env` de `infra/terraform/modules/platform/cloud_run.tf` (E0.5)** con la URL del servicio Cloud Run `api` (`google_cloud_run_v2_service.api.uri`). Cloud Tasks necesita la URL real a la que hacer el POST de `/internal/tasks/build-persona`; la audiencia OIDC (`INTERNAL_OIDC_AUDIENCE`) es una custom audience y no sirve como destino. Sin `SERVICE_URL` (o sin `TASKS_QUEUE_AI_LIGHT`) el servicio arranca, guarda las respuestas y registra `Cloud Tasks no está configurado` al encolar: el gemelo queda con `personaReady = false` hasta que se configuren y se reenvíen las respuestas. | I1 | Antes del primer despliegue con E2.3 |
| 2 | **Verificar en dev tras el `apply` de E0.5:** `POST /v1/twin/answers` con 12 respuestas de un usuario real, esperar unos segundos y comprobar que `GET /v1/twin` devuelve `personaReady = true` (la tarea llega a `ai-light` y `sa-tasks` invoca la API). Requiere que Vertex esté habilitado (pendiente 3 de E0.9). | I1 | Después del `apply` de dev |
| 3 | **Mezclar antes las dependencias.** Esta rama integra E0.9 (que a su vez trae E0.4 y E0.3) porque `dev` aún no las tiene. Mezclar en este orden: E0.3 (PR #7), E0.4 (#12), E0.9 (#21) y luego E2.3; al mezclar, el diff del PR queda solo con los archivos de E2.3. | I1 | Antes del merge |

## E4.1 · Servicio de cuotas

Lo automatizable ya quedó hecho: `QuotaService` (límites gratis y uso justo de Plus), `usage_events` idempotente con devolución, `GET /v1/quota`, la interfaz `EntitlementService` (stub 'gratis' en prod hasta E7.1) y `POST /internal/dev/grant-plus` solo fuera de prod. Las pruebas con Postgres (`QuotaDbTest`, incluida la de concurrencia) se omiten en local con `-PsinContenedores` y corren en CI.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar primero E0.3 (PR #7) y E0.4 (PR #12).** Esta rama integra `feature/db-schema-v1` porque necesita las tablas `usage_events`, `entitlements` y `users`; el PR de E4.1 muestra también esos commits hasta que se mezclen en `dev`. | I1 | Antes del merge de E4.1 |
| 2 | **Conflicto esperado con E2.1 (PR #19)** en `AppContainer.kt` y `TestSupport.kt`: ambos agregan un parámetro al contenedor y a `allDomains(...)`. Al mezclar el segundo, conservar los dos (`identity` y `quota`). Los cambios en `Database.kt` (`failFast`) y en `PlatformTest` son idénticos a los de E2.1 y no deberían chocar. Cuando E2.1 esté en `dev`, `QuotaUserLookup` puede implementarse con su repositorio. | I1 | Al mezclar el segundo de los dos |
| 3 | **Revisar los valores de uso justo de Plus** (por defecto 20 simulaciones, 10 matches al azar y 20 informes completos por día) y, si se cambian, fijarlos como variables del servicio `api` en Terraform: `QUOTA_PLUS_DAILY_SIMULATIONS`, `QUOTA_PLUS_DAILY_RANDOM_MATCHES`, `QUOTA_PLUS_DAILY_FULL_REPORTS`. Sin variables se usan los valores por defecto. | I1 | Antes de la 0.2 |
| 4 | **Probar `grant-plus` en dev** (opcional). La ruta está bajo `/internal`, así que exige el token OIDC de `sa-tasks`. Quien la use necesita `roles/iam.serviceAccountTokenCreator` sobre `sa-tasks@parcheu-dev.iam.gserviceaccount.com`. Luego: `TOKEN=$(gcloud auth print-identity-token --impersonate-service-account=sa-tasks@parcheu-dev.iam.gserviceaccount.com --audiences=https://api.parcheu-dev.internal --include-email)` y `curl -X POST "$API_URL/internal/dev/grant-plus" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"userId":"<users.id>","days":30}'` (204). En prod la ruta no existe (404). | I1 | Cuando haya `deploy-dev` |
| 5 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E4.1 es I3. | I3 | Ventana de revisión de la ola 5 (31 oct) |

## E4.3 · Motor de simulación

El código y las pruebas están completos: 31 pruebas nuevas con FakeLlm y la API, más 5 con Postgres 16 real que se ejecutaron en local contra un Postgres desechable (`PARCHEU_TEST_DB_URL`) y corren en CI con Testcontainers. Además, el 22 oct 2026 se ejecutaron contra GCP dev los humos manuales `SimulationVertexSmokeTest`: una simulación real con `gemini-3.8-flash` terminó en DONE (puntaje 82, 27 llamadas, unos 2 centavos de USD, turnos de 480 caracteres como máximo) y el espejo escribió y borró `simulations/{id}` en el Firestore de `parcheu-dev`. Lo que falta depende de infraestructura, de otras features o de revisión:

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar antes las dependencias.** `dev` aún no tiene E0.3, E0.4, E0.9, E2.1, E2.3, E4.1 ni E3.1, así que esta rama las integra. Orden sugerido: E0.3 (PR #7), E0.4 (#12), E0.9 (#21), E2.1 (#19), E2.3 (#27), E4.1 (#22), E3.1 (#31) y luego E4.3. Al mezclarlas, el diff del PR queda solo con `backend/.../simulation/**`, el cableado en `AppContainer.kt` y los ajustes de pruebas. Los conflictos de `AppContainer.kt`/`TestSupport.kt` entre E2.3, E4.1 y E3.1 ya están resueltos en esta rama (sirve de referencia). | I1 | Antes del merge |
| 2 | **`terraform apply` de dev (E0.5).** Hoy la API de Cloud Tasks ni siquiera está habilitada en `parcheu-dev` (`gcloud tasks queues list` responde `SERVICE_DISABLED`). Sin el apply no existen la cola `simulations`, `TASKS_QUEUE_SIM`, `SERVICE_URL` (pendiente 1 de E2.3) ni el permiso de Vertex para `sa-api`: `POST /v1/simulations` respondería 500 al encolar y el cupo se devuelve solo. Con el apply listo, `TASKS_QUEUE_SIM` sale del Terraform de E0.5 (`cloud_run.tf`) y no hace falta nada más del lado del código. | Usuario | Antes del primer despliegue con E4.3 |
| 3 | **Primera simulación real en dev (hito del 6 nov).** Con 2 y el despliegue listos: dos cuentas con gemelo listo y el modo Amistad activo (E2.5), `POST /v1/simulations {"otherUserId":"<users.id de la otra>","mode":"AMISTAD"}`. Comprobar que responde 202, que `GET /v1/simulations/{id}` pasa a DONE en uno o dos minutos, que el documento `simulations/{id}` aparece en Firestore y que la arista SIMULATED sale en `GET /v1/graph`. Mientras E2.5 no esté, activar el modo a mano: `insert into user_modes (user_id, mode, enabled, enabled_at) values ('<id>', 'AMISTAD', true, now()) on conflict (user_id, mode) do update set enabled = true;`. | I1 | 6 nov |
| 4 | ~~**Avisos al terminar.**~~ Hecho al integrar `dev` (con E5.3): `NotifierSimulationNotifier` entrega `SIMULATION_READY` (`parcheu://results/{id}`) y `SIMULATION_FAILED` (`parcheu://simulation/{id}`) al `Notifier` del contenedor. | I1 | Hecho |
| 5 | **`refunded` para F5 (E4.5).** El criterio de E4.5 pide mostrar "No se descontó de tu cupo" solo si el resumen trae `refunded = true`, pero `SimulationSummary` del contrato no tiene ese campo. El servidor ya devuelve el cupo; falta agregar `refunded: Boolean = false` al contrato (cambio compatible) y llenarlo desde `usage_events`. Decidir si lo hace E4.5 o un PR corto del contrato. | I1 + I2 | Antes de E4.5 |
| 6 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E4.3 es I2. | I2 | Ventana de revisión de la ola 7 (6 nov) |

## E3.4 · Pantalla Tu parche

No hay pasos humanos en consolas externas. Quedan decisiones de contrato para I1 y la dueña de E3.1/E6.3:

- **`NodeDetail.chatId` sin llenar en el servidor.** El contrato ya trae `chatId?` y D2 solo muestra "Chatear" cuando viene con valor. I1 debe devolverlo en `GET /v1/graph/nodes/{id}` para nodos `CONNECTED` (E4.6/E5.1); mientras tanto el botón permanece oculto.
- **`GraphFof.parentId`.** D1 no pinta la capa de amigos de amigos (es de D6, ParcheU+); el hueco `viaId` de `FofCandidate` sigue abierto para E3.1/E6.3.
- **Comunidades.** `GraphDto` no trae la lista de comunidades: el chip "Comunidad" abre D8 (ParcheU+) o el paywall (gratis) y D8 vuelve a D1 con `Parche(community)`, que pasa `?community=` a la API.
- **Iconos.** D1-D5 usan iconos vectoriales propios (`ParcheIcons.kt`) hasta que exista la biblioteca de Material Symbols del sistema de diseño (E1.4).
- **Cierre de sesión (E8.2).** Debe llamar `GraphCache.clear()` para borrar el último grafo guardado.
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

## E5.1 · API de chat

Lo automatizable ya quedó hecho: `ChatService`, las cuatro rutas de `/v1/chats`, el espejo en Firestore con el Admin SDK y las pruebas. En local pasaron las 123 pruebas del backend con Postgres 16 (binarios de `@embedded-postgres`, `PARCHEU_TEST_DB_URL`) y el emulador de Firestore, sin omitir ninguna. `sa-api` ya tiene `roles/datastore.user` en el Terraform de E0.5 (PR #5), así que no hace falta otro permiso.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar antes las dependencias.** Esta rama integra E3.1 (#31, que trae E0.2, E0.3, E0.4 y E2.1) y E0.8 (#6) con commits "Integra ... mientras su PR no se mezcla", y copia sin cambios `platform/notifier/Notifier.kt` de E5.3 (#30) para no traer su parte Android. | I1 | Antes del merge de E5.1 |
| 2 | **Conectar FcmNotifier al mezclar E5.3.** Hoy `AppContainer.fromConfig` pasa `NoopNotifier` al chat (los avisos de mensajes nuevos no salen todavía). Al resolver el conflicto de `AppContainer.kt` con #30, el `notifier` de `FcmNotifier` debe ser el mismo que recibe `ChatModule`. | I1 / I3 | Al mezclar #30 o esta rama, la que vaya segunda |
| 3 | **Revisar `backend-ci` en el PR.** Ahora corre los tests dentro de `firebase emulators:exec` (Node 24 + `npm ci` en `firebase/`). Confirmar que `ChatDbTest` y `ChatFirestoreEmulatorTest` aparecen como ejecutados (no omitidos) en el reporte. | I1 | Revisión del PR |
| 4 | **Aprobación del revisor par** (feature major, check `major-gate`): según `docs/equipo.md` el par de E5.1 es I3, que además es dueño de la capa Firebase que toca el espejo. | I3 | Ventana de revisión de la ola 7 (6 nov) |
| 5 | **Prueba en dev con la app (E5.2).** Cuando E5.2 y E4.6 estén en `dev`: enviar un mensaje desde la app y comprobar que el documento aparece en `chats/{id}/messages` de `parcheu-dev` y que las reglas desplegadas dejan leerlo solo a los miembros. | I2 | Ola 8 |

## E5.2 · UI de chat y lista de chats

- [ ] Prueba manual en dispositivo (flavor dev) cuando E5.1 (API de chat) y E0.8 (reglas de Firestore) estén desplegadas en dev: iniciar sesión con dos cuentas, crear un chat DIRECT, enviar mensajes en ambos sentidos, apagar la red y comprobar que el mensaje queda con "Reintentar", y ocultar un mensaje desde Firestore (`hidden=true`) para ver "Mensaje oculto". Requiere el `google-services.json` del entorno dev (`scripts/fetch-google-services.sh`).
- [ ] Revisar a ojo las capturas de `android/app/src/test/screenshots/chat/` contra los mockups F9 (17) y el estilo de la lista de chats; la comparación con el diseño la hace I2.
## E2.1 · API de sesión y usuarios

No hay pasos bloqueantes: la feature no crea recursos en GCP, Firebase ni consolas.

1. **Texto legal vigente (`LEGAL_TEXT_VERSION`).** Por defecto es `2026-10-29` (constante en `AppConfig`). Cuando E8.4 publique
   las páginas `/privacidad` y `/terminos`, confirma que su versión sea exactamente esa o cambia el valor: o bien se actualiza
   `DEFAULT_LEGAL_TEXT_VERSION`, o bien se define la variable `LEGAL_TEXT_VERSION` en el servicio de Cloud Run (Terraform de E0.5).
   Cada cambio de versión devuelve a todos los usuarios a `NEEDS_TERMS` (es lo que exige la Ley 1581).
2. **Variables de la base en el servicio.** Desde E2.1 el modo `serve` exige `DB_NAME` y `CLOUD_SQL_INSTANCE`. Antes del primer
   `deploy-dev` verifica que el servicio de Cloud Run las recibe (hoy solo las usa el job `migrate`).
3. **Revisión legal.** Pide al docente o a un abogado revisar el texto de aceptación de A4 y el plazo de conservación de
   `consents` (5 años, arquitectura.md §10.2) antes de la prueba cerrada.

## E4.5 · UI de simulación

1. **Backend: llenar `summary.refunded`.** E4.5 agregó `refunded: Boolean = false` a `SimulationSummary` del
   contrato (la ficha pide que F5 solo diga "No se descontó de tu cupo" con `refunded=true`). El motor de E4.3
   (PR #37) ya marca `usage_events.refunded` al fallar; falta que `GET /v1/simulations/{id}` y la lista lo lean
   de ahí y lo devuelvan en el resumen. Sin eso F5 nunca mostrará el mensaje. Dueño sugerido: I1.
2. **Firestore en el dispositivo:** F4 escucha `simulations/{id}` con el SDK, que necesita `google-services.json`
   (`scripts/fetch-google-services.sh`) y las reglas de E0.8 desplegadas en dev. Sin ellos F4 cae al respaldo REST
   (una consulta a los 10 s) y no se actualiza en vivo.
3. **Verificación manual en el PR:** con E4.3 desplegado en dev, instala el flavor dev, inicia una simulación
   con un usuario demo y comprueba: F4 avanza 3 escenarios, sales y vuelves (se reanuda), al terminar abre
   Resultados (F7/F8, E4.6) y "atrás" no vuelve a F4. Fuerza un fallo (apaga el modelo en dev) y confirma F5.
4. **Revisión lado a lado (ADR-0022):** I2 compara `android/app/src/test/screenshots/simulation/` con las
   pantallas 15a, 15b y 15c. Diferencias conocidas: la mascota está dibujada localmente en
   `feature/simulation/SimulationParts.kt` y los iconos en `SimulationIcons.kt` (E1.4, E1.5 y E1.2 aún no están
   en `dev`); los títulos de los escenarios se muestran como "Escenario N" porque la API no los entrega; no hay
   burbujas con texto en el escenario porque `simulations/{id}` solo publica progreso. Cuando se mezclen
   esas features, cambiar las piezas locales y volver a grabar (`./gradlew :app:recordRoborazziDevDebug`).
5. **Reintentar con una persona anónima:** `POST /v1/simulations` pide `otherUserId`; en un match al azar la
   otra persona sigue anónima, así que F5 oculta "Reintentar" y deja "Volver al inicio". Decide con I1 si el
   servidor ofrece un reintento por `simulationId`.

## E4.6 · Resultados y decisión mutua

Lo automatizable ya quedó hecho: API de informe, desbloqueo y decisión (`backend/.../simulation/report/`), pantalla de resultados (`android/app/.../feature/results/`), pruebas (incluidas las de Postgres 16 real, ejecutadas en local contra un Postgres desechable con `PARCHEU_TEST_DB_URL`) y capturas de Roborazzi. No crea recursos en GCP.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | ~~**Mezclar antes las dependencias.**~~ Hecho: `dev` ya tiene E4.3, E5.1, E4.5 y E5.3, y esta rama los integra en su forma definitiva. El diff del PR queda con `simulation/report/**`, `feature/results/**`, `data/results/**`, el contrato, el cableado y los ajustes de pruebas. | I1 | Hecho |
| 2 | ~~**Push de conexión.**~~ Hecho al integrar `dev` (con E5.3): la decisión mutua usa el `FcmNotifier` del contenedor para `NEW_CONNECTION`. | I1 | Hecho |
| 3 | **Probar en dev con dos cuentas** (después del `apply` de E0.5 y de la primera simulación real de E4.3): abrir el resultado en las dos, desbloquear el informe en una cuenta gratis (el segundo desbloqueo del mes debe mostrar 'Ya usaste tu informe gratis…'), pulsar Conectar en ambas y comprobar que la segunda abre el chat y que el nombre aparece solo entonces. | I2 | Con el despliegue de dev |
| 4 | **Comparación lado a lado** de F7, F8 e I3 con los PNG de Claude Design (`scripts/lado-a-lado.sh`, ADR-0022). Las capturas de esta rama están en `android/app/src/test/screenshots/results/` (I3 = `i3_light.png`). Iconos de candado, conectar y avisos son vectores locales (ver el paso 6); el mascot es el de E4.5. | I2 | Ventana de revisión de la ola 8 |
| 5 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E4.6 es I3; la parte de `backend/` la aprueba además I1. | I3 + I1 | Ventana de revisión de la ola 8 (8 nov) |
| 6 | **Pasar F7/F8 a los compuestos de E1.4.** `feature/results/ResultsParts.kt` trae versiones locales de AffinityRing, LockedOverlay y DecisionFooter, y sus iconos, porque se escribió antes de que E1.4 y E1.2 llegaran a `dev`. Cambiarlas por las de `designsystem` (`components/compound/Locked.kt` y demás) en un PR aparte y volver a grabar las capturas de `results/`. No bloquea: el aspecto ya se compara en el paso 4. | I2 | Después del merge |

## E1.4 · Componentes compuestos

Lo automatizable ya quedó hecho: los 25 compuestos en `android/designsystem/.../components/compound/`, pruebas de semántica, de LockedOverlay (sdk 30 y 35) y de avatares, y capturas Roborazzi en ambos temas (`android/designsystem/src/test/screenshots/compound/`).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar primero E1.3 (PR #20).** Esta rama parte de `feature/components-base` porque los compuestos reutilizan los botones, tarjetas y `touchTarget()` de E1.3; el PR muestra también su commit hasta que #20 se mezcle en `dev`. | I2 | Antes del merge de E1.4 |
| 2 | **Revisión visual lado a lado.** Comparar las capturas de `screenshots/compound/*.png` con los mockups B-H de Claude Design (radios, paddings, colores de burbujas, anillo de afinidad, QR). Diferencias aceptadas: el QR de las capturas es sintético (el codificador llega con E6.1) y los iconos son círculos de prueba hasta que E1.2 se mezcle. | I2 | Ventana de revisión de la ola 6 |
| 3 | **Prueba del blur en un dispositivo real.** Robolectric no renderiza `RenderEffect`, así que el desenfoque de `LockedOverlay` solo se verifica por rama (etiqueta `locked_overlay_blur`) y no por píxeles. Abrir una pantalla que lo use (D7 o F7, cuando existan) en un dispositivo/emulador con API >= 31 y en otro con API < 31 y confirmar blur y scrim opaco respectivamente. | I2 | Cuando exista la primera pantalla con LockedOverlay |

## E2.6 · Perfil básico y edad

Lo automatizable ya quedó hecho: `POST /v1/me/age-verification`, `GET /v1/catalog`, la puerta 403 `UNDERAGE`, la migración
`V20261022_0745__universidad_unipaz.sql`, B0 'Tu perfil', el bloqueo por edad y sus pruebas (API, ViewModel, semántica y
Roborazzi en ambos temas). No se crean recursos en GCP ni en consolas.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Confirmar la lista de universidades.** La ficha pide UIS, UNAB, UPB, UDES, UTS, USTA, UCC, UNIPAZ y 'Otra'. V2 sembró además UMB, UDI, UNISANGIL y SENA (siguen activas) y E2.6 agregó UNIPAZ (id 12). Si el equipo decide ocultar alguna, crear una migración `V<yyyyMMdd>_<HHmm>__universidades.sql` con `update universities set active = false where short_name in (...)` (no borrar filas: puede haber usuarios que ya la eligieron). | I1 + equipo | Antes de la prueba cerrada |
| 2 | **Revisión de B0 y del bloqueo (ADR-0022).** No hay mockup: I2 revisa `android/app/src/test/screenshots/basicprofile/*.png` contra el sistema de diseño y anota las diferencias aceptadas en el PR. La flecha de 'Editar perfil' es un vector local hasta que E1.2 (iconos) se mezcle. | I2 | Ventana de revisión de la ola 7 |
| 3 | **'Borrar mi cuenta' depende de E8.3.** El bloqueo por edad abre la ruta `DeleteAccount`, que sigue siendo stub, y `DELETE /v1/me` responde 501 hasta que E8.3 lo implemente. La puerta de edad ya deja pasar `DELETE /v1/me`. | I1/I3 | Al mezclar E8.3 |
| 4 | **Prueba manual en un teléfono** (tras desplegar E2.1 y E2.6 en dev): cuenta nueva → A4 → B0 → elegir fecha de alguien de 18 años, universidad e intereses → B1. Con otra cuenta de prueba, una fecha de menor de 18 → pantalla de bloqueo; cerrar y abrir la app debe volver al bloqueo (el Splash recibe 403 `UNDERAGE`). Para repetir con la misma cuenta hay que limpiar `birth_date`, `age_verified_at` y `underage_at` en la base de dev. | I2 | Antes de cerrar la ola 7 |
| 5 | **Texto del bloqueo.** Pedir al docente revisar el mensaje "ParcheU es solo para mayores de 18…" junto con la revisión legal de E2.1. | I3 | Antes de la prueba cerrada |
## E2.5 · Modos, +18 y consentimiento

Lo automatizable ya quedó hecho: `PUT /v1/me/modes` con la regla +18, el consentimiento de aprendizaje por modo
(`identity/modes/**`, `identity/consent/**`), las pantallas B2-B5 (`feature/modes`, `feature/consent`, `feature/twinready`),
pruebas de servidor (memoria y Postgres), de ViewModel, de UI y capturas Roborazzi (`android/app/src/test/screenshots/modes/`).
No crea recursos en GCP, Firebase ni consolas.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar antes E2.1 (#19), E1.4 (#26) y E1.5 (#18).** Esta rama los integra porque no están en `dev`; el PR mostrará sus commits hasta que se mezclen. | I1 / I2 | Antes del merge de E2.5 |
| 2 | **Pruebas con Postgres (`ModesDbTest`).** En local se omiten (`-PsinContenedores`, sin Docker); corren en `backend-ci`. Revisa que pasen en el PR. | I1 | Al abrir el PR |
| 3 | **Revisión visual lado a lado.** Comparar `screenshots/modes/*.png` con 03b-03e de 'PU Seccion B'. Diferencias intencionales: B2 arranca con todo apagado y valida 0 modos; B3 muestra la fecha verificada en B0 en solo lectura y la casilla 'Confirmo que soy mayor de 18 y quiero activar Pareja'; B4 cambia el interruptor por 'Acepto' / 'Ahora no' y aclara que Pareja queda fuera; los íconos son trazos de Material dibujados en código hasta que se mezcle E1.2. | I2 | Ventana de revisión de la ola 7 |
| 4 | **Prueba de punta a punta en un dispositivo** cuando E2.6 (B0 y `POST /v1/me/age-verification`) esté en `dev`: sin fecha verificada B3 no deja confirmar; con fecha, activar Pareja, Continuar, B4 'Acepto' y revisar en Cloud SQL de dev que `consents` tenga CHAT_LEARNING_AMISTAD y CHAT_LEARNING_ESTUDIO y ninguna de Pareja. | I1 + I2 | Tras mezclar E2.6 |
| 5 | **Revisión legal del texto de B4** (qué aprende y qué nunca hace) junto con la de A4 (E2.1, paso 3). | Equipo | Antes de la prueba cerrada |
## E4.4 · UI de match al azar

Lo automatizable ya quedó hecho: F1, F2 y la hoja de límite de match diario en `android/app/.../feature/conocer/match/`, pruebas de ViewModel con `FakeMatchRepository`, pruebas de la pantalla y capturas Roborazzi en ambos temas (`android/app/src/test/screenshots/match/`).

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Mezclar primero #20 (E1.3), #26 (E1.4) y #18 (E1.5).** E4.4 depende de E1.4 y E1.5, que aún no estaban en `dev`: esta rama los mezcla para poder compilar y el PR muestra también sus commits hasta que esos PR entren a `dev`. | I2 | Antes del merge de E4.4 |
| 2 | **Confirmar con I1 los ids de F3 y F4.** `MatchDto` no trae el id del chat de escenario ni de la simulación; F2 navega a `ScenarioChat(matchId)` y `Simulation(matchId)` y E5.2/E4.5 deben resolverlos por el id del match, o el contrato debe agregar `simulationId`/`chatId` a `MatchDto` o a la respuesta de `POST /v1/matches/{id}/choice`. | I1 e I2 | Antes de integrar E4.5 y F3 |
| 3 | **Revisión visual lado a lado** de `screenshots/match/*.png` con los mockups 13a y 13b. Diferencias conocidas: sin iconos de modo ni de intereses (E1.2 no está en `dev`), sin la ciudad en la línea de edad (el contrato no la trae) y la hoja de límite de match diario es nueva (el inventario no la diseñó: G2 cubre solo simulaciones). | I2 | Ventana de revisión de la ola 7 |
| 4 | **Probar contra dev con dos cuentas**: entrar a la fila con dos usuarios del mismo modo y comprobar que el listener de `matchTickets/{uid}` abre F2 en ambos. Requiere `google-services.json` de dev y las reglas de Firestore desplegadas (E0.8). Docker y Firestore real no están disponibles en CI. | I2 | Con la primera prueba cerrada |
### Corrección de la revisión de la ola 6 (E2.2)

- **Release 0.1 (E10.3):** cortar `release/0.1.0` desde `d7989af`, no desde `dev` (ver ficha E10.3 en backlog.md).
- **PR #20 (E1.3):** su contenido ya entró a `dev` dentro del squash de #28. Cierra #20 como absorbido por #28
  (o rebásalo sobre `dev`) y cuenta E1.3 en el contador de features pequeñas de `docs/revisiones.md`.
- **Secreto `ANTHROPIC_API_KEY`:** #28 se mezcló con `revision-agente` en FAILURE por este secreto ausente;
  créalo con `gh secret set ANTHROPIC_API_KEY` para que la revisión previa al merge vuelva a ocurrir.
- **Spotless tras E0.3 (#7):** el `.editorconfig` de #7 exige coma final; al mezclarlo hay que correr
  `./gradlew spotlessApply` sobre el código de E2.2 y E1.3.

## E4.2 · API de fila de match

Lo automatizable ya quedó hecho: `POST/GET/DELETE /v1/match/queue`, `GET /v1/matches/{id}`, `POST /v1/matches/{id}/choice`, el emparejamiento con `FOR UPDATE SKIP LOCKED`, el espejo `matchTickets/{uid}`, los avisos por `Notifier` (en `fromConfig` es el `FcmNotifier` de E5.3) y el job `POST /internal/jobs/match-sweep`. Las pruebas con Postgres (`MatchingDbTest`, incluida la de 10 usuarios en paralelo y la de `SKIP LOCKED`) y con el emulador de Firestore corren en CI (Testcontainers y `firebase emulators:exec` en `backend-ci`). Las dependencias (E2.5, E2.6, E4.3, E5.1 y E5.3) ya están en `dev` y la rama las integra. Lo que falta depende de infraestructura, de otras features o de revisión:

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Cloud Scheduler `match-sweep` (cada minuto).** Un job que haga `POST <SERVICE_URL>/internal/jobs/match-sweep` con token OIDC de `sa-tasks` y la audiencia `INTERNAL_OIDC_AUDIENCE`. Hoy no existe Cloud Scheduler en el Terraform de E0.5 ni la API habilitada en `parcheu-dev` (no hay `terraform apply` de dev todavía). Sin el job la API sigue siendo correcta (cada consulta resuelve lo vencido que toca), pero nadie recibe el aviso de vencimiento ni se reintentan simulaciones pendientes hasta que la persona vuelva a abrir la app. Es el mismo mecanismo que usarán los jobs de `/internal/jobs/*`. | I1 / usuario (prod) | Con el `apply` de dev |
| 2 | **Variables del servicio `api` en Terraform (`run_env` de E0.5):** `DEMO_USERS_ENABLED` (`true` en dev y durante la prueba cerrada, `false` en prod 1.0) y `SCENARIO_MODE_ENABLED` (`false` hasta que E4.8 se mezcle). Sin variables se usa el valor por defecto de `docs/desarrollo-local.md`: demo encendido salvo en prod, escenario apagado. | I1 | Antes de la prueba cerrada |
| 3 | **Migraciones `V20261022_1000__fila_de_match_con_estado.sql` y `V20261022_1100__version_de_ticket.sql`** (columnas `status`, `match_id` y `version` de `match_queue`). Corren solas en el Cloud Run Job `migrate`. Si otra migración con fecha posterior se mezcla antes, `migration-order` pide renombrarlas. | I1 | Al mezclar |
| 4 | **Chat SCENARIO en Firestore.** Cuando ambos eligen SCENARIO (solo con `SCENARIO_MODE_ENABLED=true`) E4.2 crea la fila de `chats` y `chat_members` en Postgres, pero no escribe `chats/{id}` en Firestore: el espejo del chat es de E5.1. E4.8 debe llamar al espejo con `matches.chat_id` al encender la bandera. Hasta entonces la bandera sigue apagada. | I1 | E4.8 |
| 5 | **Verificación en dev** (cuando haya despliegue): dos cuentas con gemelo listo y el mismo modo activo; `POST /v1/match/queue` desde ambas, comprobar que `GET /v1/match/queue` pasa a `MATCHED`, que aparece `matchTickets/{firebaseUid}` en el Firestore de `parcheu-dev` y que `POST /v1/matches/{id}/choice` con `SIMULATION` crea una simulación `origin = RANDOM`. | I1 | Con el despliegue de dev |
