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
