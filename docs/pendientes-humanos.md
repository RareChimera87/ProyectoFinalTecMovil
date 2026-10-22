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

## E3.4 · Pantalla Tu parche

No hay pasos humanos en consolas externas. Quedan decisiones de contrato para I1 y la dueña de E3.1/E6.3:

- **`NodeDetail` sin id de chat.** "Chatear" (D2 → F9) abre `Chat(chatId = node.id)` porque `GET /v1/graph/nodes/{id}` no devuelve el chat. Agregar `chatId?` a `NodeDetail` y cambiar `ParcheViewModel.onChat()`.
- **`GraphFof.parentId`.** D1 no pinta la capa de amigos de amigos (es de D6, ParcheU+); el hueco `viaId` de `FofCandidate` sigue abierto para E3.1/E6.3.
- **Comunidades.** `GraphDto` no trae la lista de comunidades: el chip "Comunidad" abre D8 (ParcheU+) o el paywall (gratis) y D8 vuelve a D1 con `Parche(community)`, que pasa `?community=` a la API.
- **Iconos.** D1-D5 usan iconos vectoriales propios (`ParcheIcons.kt`) hasta que exista la biblioteca de Material Symbols del sistema de diseño (E1.4).
- **Cierre de sesión (E8.2).** Debe llamar `GraphCache.clear()` para borrar el último grafo guardado.
