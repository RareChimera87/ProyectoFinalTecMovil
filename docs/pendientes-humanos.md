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
| 4 | **Avisos al terminar.** El puerto general `Notifier` llega con E5.3 (PR #30). Mientras no se mezcle, el contenedor cablea `NoopSimulationNotifier`. Al mezclar E5.3, agregar en `simulation/` un adaptador `SimulationNotifier` → `Notifier.notify(userId, Notification(kind = "simulation_done"/"simulation_failed", ...))` con la ruta al resultado, y cablearlo en `simulationModule(...)`. | I1 | Al mezclar E5.3 |
| 5 | **`refunded` para F5 (E4.5).** El criterio de E4.5 pide mostrar "No se descontó de tu cupo" solo si el resumen trae `refunded = true`, pero `SimulationSummary` del contrato no tiene ese campo. El servidor ya devuelve el cupo; falta agregar `refunded: Boolean = false` al contrato (cambio compatible) y llenarlo desde `usage_events`. Decidir si lo hace E4.5 o un PR corto del contrato. | I1 + I2 | Antes de E4.5 |
| 6 | **Aprobación del revisor par** del PR (feature major, check `major-gate`): según `docs/equipo.md` el par de E4.3 es I2. | I2 | Ventana de revisión de la ola 7 (6 nov) |

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
