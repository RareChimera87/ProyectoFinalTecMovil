# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

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

Estado al 22 oct: los workflows están en `.github/workflows/` (rama `feature/ci-cd-pipelines`), con
`actionlint` + `shellcheck` en verde y las pruebas de `review-counter` pasando. **Nada de GCP quedó
aplicado:** el agente preparó y validó el `plan` del bootstrap de dev (WIF, `sa-deployer`, `sa-terraform`,
`sa-terraform-plan`, presupuesto; 50 recursos + importar el bucket), pero el entorno no le permitió ejecutar
`terraform apply`. Tampoco hay todavía secretos de WIF en GitHub. Orden recomendado:

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
También quedó creada la cuenta de servicio **`play-publisher@parcheu-dev.iam.gserviceaccount.com`**
(sin roles de GCP) y habilitada la API `androidpublisher.googleapis.com` en `parcheu-dev`.
El agente **no** cargó secretos en GitHub ni creó llaves: el entorno no le permitió materializar credenciales.

| # | Paso | Dueño | Cuándo |
|---|---|---|---|
| 1 | **Secretos de la llave de subida** (si E10.1/E7.0 no los cargaron; `gh secret list` debe mostrar los cuatro): `cd ~/.parcheu && set -a && . ./upload-key.env && set +a && R=RareChimera87/ProyectoFinalTecMovil`, luego `base64 -w0 upload-keystore.jks \| gh secret set UPLOAD_KEYSTORE -R $R`, `printf %s "$UPLOAD_KEYSTORE_PASSWORD" \| gh secret set UPLOAD_KEYSTORE_PASSWORD -R $R`, `printf %s "$UPLOAD_KEY_ALIAS" \| gh secret set UPLOAD_KEY_ALIAS -R $R` y `printf %s "$UPLOAD_KEY_PASSWORD" \| gh secret set UPLOAD_KEY_PASSWORD -R $R`. | I3 (o el usuario) | Antes de la primera corrida |
| 2 | **Llave JSON de la cuenta de servicio de Play** (la llave solo sirve en Play porque la cuenta no tiene roles de GCP; alternativa sin llave para más adelante: WIF que suplante a esta cuenta + `useApplicationDefaultCredentials` de GPP, cuando exista el bootstrap de WIF): `gcloud iam service-accounts keys create ~/.parcheu/play-publisher.json --iam-account play-publisher@parcheu-dev.iam.gserviceaccount.com && gh secret set PLAY_SERVICE_ACCOUNT_JSON -R RareChimera87/ProyectoFinalTecMovil < ~/.parcheu/play-publisher.json`. Guarda o borra la copia local (`chmod 600`). | El usuario | Antes de la primera corrida |
| 3 | **Invitar la cuenta en Play Console**: *Usuarios y permisos → Invitar usuarios →* `play-publisher@parcheu-dev.iam.gserviceaccount.com`; en *Permisos de la app* agrega ParcheU con **Ver información de la app**, **Lanzar a pistas de prueba** y **Administrar pistas de prueba y editar listas de testers**. Sin acceso a producción. El permiso puede tardar hasta 24 h en propagarse. | I3 | Después de la primera subida manual de E7.0 (28 oct) |
| 4 | **Primera corrida (criterio de aceptación):** con la 0.0.1 ya en la pista interna, `gh workflow run android-release --ref feature/android-release-pipeline -f pista=internal` (o desde `dev` una vez mezclado). Debe terminar en verde y aparecer en *Pruebas internas* una versión `0.0.1-int.<n>` con versionCode `100 + n×10`. Si Play responde *"Only releases with status draft may be created on draft app"*, repetir con `-f estado=draft` y lanzar la versión desde la consola. Corre una segunda vez y confirma que el versionCode subió. Deja el enlace de ambas corridas en el PR. | I3 | 28-31 oct |
| 5 | **Disparo tras `deploy-prod`:** `workflow_run` solo se activa con la copia del workflow en `main`; queda activo cuando `release/0.1.0` llegue a `main`. Un tag `vX.Y.Z` sube a la pista cerrada (`alpha`). Si la pista cerrada de Play tiene otro identificador (p. ej. una pista personalizada), cambia `alpha` en el paso *Parámetros de la versión*. | I3 | Con el primer tag |
| 6 | **Opcional:** variable `UPLOAD_CERT_SHA256` del repo si se cambia la llave de subida (por defecto se exige la huella de `docs/play-console.md`). | I3 | Solo si se rota la llave |
| 7 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E10.2 es I1. | I1 | Ventana de la ola 5 (31 oct) |
