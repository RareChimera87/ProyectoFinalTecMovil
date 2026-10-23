# Desarrollo local

Guía para preparar una máquina de desarrollo de ParcheU. Las decisiones de fondo están en [arquitectura.md](arquitectura.md) y [ADR-0001](adr/0001-monorepo-y-git-flow.md).

## Toolchains de Java

| Parte | JDK | Notas |
|---|---|---|
| `android/` | 17 | `JAVA_HOME` por defecto de la máquina |
| `contract/` | 17 | toolchain 17 |
| `backend/` | 21 | toolchain 21, resuelto por Gradle |

- Instala **JDK 17** y úsalo como `JAVA_HOME`.
- El **JBR 21** de Android Studio (`/opt/android-studio/jbr` en Linux) sirve como JDK 21 del backend. Para que Gradle lo encuentre sin intervención, agrega a `~/.gradle/gradle.properties`:

  ```properties
  org.gradle.java.installations.paths=/opt/android-studio/jbr
  ```

- Gradle también puede descargar toolchains con el plugin foojay (`foojay-resolver-convention`) si hay red.
- Memoria: `org.gradle.jvmargs=-Xmx2g` y un solo build de Gradle a la vez en máquinas con poca RAM o disco.
- El SDK de Android se declara en `android/local.properties` (`sdk.dir=...`); ese archivo **no se versiona**.

## Comandos habituales

```bash
# Android
cd android && ./gradlew :app:testDebugUnitTest

# Backend: sin contenedores en local (el backend tiene su propio wrapper)
cd backend && ./gradlew build -PsinContenedores

# Imagen del backend sin daemon de Docker (genera backend/build/jib-image.tar)
cd backend && ./gradlew jibBuildTar

# Validador de commits
scripts/test/commit-lint.test.sh
```

## Testcontainers solo en CI

Los tests de backend que necesitan Postgres usan Testcontainers y **solo corren en CI**. En local no se requiere Docker: pasa `-PsinContenedores` para omitirlos (los demás tests usan fakes como `FakeLlmClient` o `FakeTokenVerifier`). Si quieres correrlos localmente, agrega tu usuario al grupo `docker` y vuelve a iniciar sesión.

**Tests de esquema sin Docker (E0.4).** `SchemaContainerTest` también acepta un Postgres 16 propio: define `PARCHEU_TEST_DB_URL` (JDBC) y, si hace falta, `PARCHEU_TEST_DB_USER` y `PARCHEU_TEST_DB_PASSWORD`. **El esquema `public` de esa base se borra al empezar cada test**: usa una base desechable.

```bash
# Ejemplo con los binarios de Postgres 16 del paquete npm @embedded-postgres/linux-x64 (sin root ni Docker)
initdb -D /tmp/pg16 -U postgres --auth=trust && pg_ctl -D /tmp/pg16 -o "-p 54329" start
createdb -h localhost -p 54329 -U postgres e04test
cd backend && PARCHEU_TEST_DB_URL=jdbc:postgresql://localhost:54329/e04test ./gradlew test -PsinContenedores
```

## Migraciones (E0.4, ADR-0011)

- Viven en `backend/src/main/resources/db/migration/`. `V1__esquema.sql` y `V2__semillas.sql` son fijas: **nunca edites una migración ya mezclada** (Flyway valida el checksum); agrega una nueva.
- Nombre de las nuevas: `V<yyyyMMdd>_<HHmm>__<descripcion>.sql` en UTC, descripción en minúsculas con `_` (p. ej. `V20261029_1430__agrega_indice_cuotas.sql`).
- Antes de abrir el PR: `git fetch origin dev && scripts/check-migration-order.sh`. Si otra rama mezcló antes una migración con fecha posterior, renombra la tuya con la fecha y hora actuales.
- `outOfOrder` está activo en todos los entornos, prod incluido (ADR-0011).
- Las tablas Exposed están en `platform/db/tables/` (un archivo por dominio) y solo describen el esquema: nunca uses `SchemaUtils.create`. Si agregas una columna jsonb, clasifícala en `PersonalJsonPaths` o falla `PersonalJsonPathsTest`.
- `consents` solo admite INSERT (trigger). La purga por retención (E8.8) debe ejecutar `set local parcheu.consents_purge = 'on'` en su transacción antes del DELETE.

## google-services.json

`google-services.json` **no se versiona** (está en `.gitignore`). Para obtenerlo:

1. Pide acceso al proyecto Firebase de dev a I1 o I3.
2. Descárgalo desde Firebase Console (Configuración del proyecto, app Android `dev`) o con `scripts/fetch-google-services.sh dev` (lo deja en su sitio; ya existe desde E0.7).
3. Colócalo en `android/app/src/dev/google-services.json` (y `src/prod/` para prod).

Hasta que exista, la app compila solo las partes que no dependen de Firebase.

## Secretos

- Nunca en el repo. Los valores locales viven fuera del árbol, p. ej. en `~/.parcheu/`.
- En CI: secretos de GitHub (`gh secret set`). En GCP: Secret Manager, con autenticación WIF.
- Keystores (`*.jks`, `*.keystore`), credenciales de Play y `.env*` están ignorados.

## Commits y ramas

1. Crea una rama `feature/<kebab>` desde `dev` (el nombre está en la ficha del backlog).
2. Instala el hook: `scripts/instalar-hooks.sh`.
3. Escribe commits en español e imperativo con un verbo de la lista blanca `scripts/verbos-imperativo.txt`. Si falta un verbo legítimo, agrégalo en el mismo PR.
4. Abre un PR contra `dev` con la plantilla; el título sigue la misma regla (se valida en `pr-title`).

Reglas del formato: mayúscula inicial, sin punto final, máximo 72 caracteres y primer verbo en la lista blanca. Se admite un prefijo de feature opcional (`E0.1 Agrega ...`).

## Backend (E0.3)

- **Toolchain.** `backend/` compila con JDK 21. `backend/settings.gradle.kts` aplica `org.gradle.toolchains.foojay-resolver-convention` y `backend/gradle.properties` trae `org.gradle.java.installations.paths=/opt/android-studio/jbr`, así que con solo Java 17 en el PATH Gradle usa el JBR 21 sin descargar nada. En una máquina sin esa ruta Gradle la ignora y foojay descarga un JDK 21 (necesita red la primera vez). No hace falta `JAVA_HOME=21`.
- **Contrato.** `backend/settings.gradle.kts` hace `includeBuild("../contract")`; los DTOs se consumen como `me.santiagoreina.parcheu:contract`.
- **Arranque local.** `cd backend && ./gradlew run` (modo `serve`, puerto 8080). Con `ENV=local` (obligatorio: sin `ENV` el backend no arranca; `./gradlew run` ya lo define) la autenticación usa Fakes: `Authorization: Bearer fake-<uid>` para `/v1/*` y `Bearer oidc-tasks-fake` para `/internal/*`. En `dev` y `prod` los Fakes nunca se cablean, y `ENV=local` se rechaza si existen `K_SERVICE` o `CLOUD_RUN_JOB` (Cloud Run).
- **Variables de entorno** (los valores locales van fuera del repo, en `~/.parcheu/`):
  - Siempre: `ENV` (local|dev|prod), `PORT`, `APP_VERSION` (SHA).
  - `serve` en dev/prod (obligatorias): `INTERNAL_OIDC_AUDIENCE` (audiencia OIDC de `/internal/*`, la custom audience de E0.5; respaldo `SERVICE_URL`, debe ser https), `TASKS_SERVICE_ACCOUNT`, y `FIREBASE_PROJECT_ID` (respaldo `GCP_PROJECT`).
  - `migrate` (obligatorias): `DB_NAME` y `CLOUD_SQL_INSTANCE` o `DB_URL`; opcionales `DB_USER`, `DB_PASSWORD`. `migrate`, `seed-demo` y `purge-demo` no exigen Firebase ni OIDC.
  - `serve` (E2.1) también necesita la base: `DB_NAME` y `CLOUD_SQL_INSTANCE` (o `DB_URL` en local), con las migraciones ya aplicadas. Opcional `LEGAL_TEXT_VERSION` (texto vigente de Términos y Política; por defecto `2026-10-29`, debe coincidir con las páginas web de E8.4).
  - Arranque local completo: Postgres propio + `ENV=local DB_NAME=parcheu DB_URL=jdbc:postgresql://localhost:5432/parcheu DB_USER=postgres ./gradlew run` tras correr el modo `migrate`. Prueba con `curl -XPOST -H 'Authorization: Bearer fake-ana' localhost:8080/v1/session`.
- **Modos:** `serve`, `migrate`, `seed-demo`, `purge-demo` (primer argumento del proceso o de la imagen; sin argumento es `serve`).
- **Tests.** `./gradlew test -PsinContenedores` omite los tests con Testcontainers (solo CI). Los demás usan `testApplication` con `FakeTokenVerifier` y `FakeOidcVerifier`.
- **Puerta de términos (E2.1).** `identity/TermsGate.kt` es un plugin de ruta que `IdentityRoutes` instala sobre la ruta autenticada de `/v1`: toda ruta de cualquier dominio responde 403 `TERMS_REQUIRED` (o `SUSPENDED`) salvo `POST /v1/session`, `POST /v1/me/terms`, `GET /v1/me` y `DELETE /v1/me`. Las pruebas de otros dominios usan `testContainer()`, que ya trae al usuario `ana` con términos aceptados.
- **Agregar un dominio o ruta.** Rellena el `XRoutes.kt` de tu dominio (reemplaza los `stubXxx`) y cablea sus dependencias en `platform/AppContainer.kt`; `Application.kt` no se toca.
- **Logs.** JSON a stdout con `severity`, `traceId` (de `X-Cloud-Trace-Context`) y `route`.

## Pasarela de IA (E0.9, ADR-0008)

- Con `ENV=local` y sin `AI_PROVIDER` el backend usa `FakeLlmClient` y guarda el uso en memoria: no se llama a Vertex ni se gasta nada.
- Para probar con Vertex real en local: `gcloud auth application-default login` y luego `ENV=local AI_PROVIDER=gemini AI_VERTEX_PROJECT=parcheu-dev ./gradlew run`.
- Humo manual (no corre en CI ni en `./gradlew test`; consume unos centavos de USD):

  ```bash
  cd backend
  ./gradlew test -Pvertex -PsinContenedores --tests '*VertexSmokeTest*'
  AI_PROVIDER=claude ./gradlew test -Pvertex -PsinContenedores --tests '*VertexSmokeTest*'   # cuando Claude tenga cuota
  ```

- IDs de modelo, ubicación y precios: `backend/src/main/resources/ai/modelos.properties`. Un modelo nuevo necesita su fila `price.*` o el servicio no arranca.

## Gemelo (E2.3)

- Con `ENV=local` sin base, el gemelo corre en memoria y la tarea `build-persona` se ejecuta en línea al enviar `POST /v1/twin/answers` (cola Fake) con el LLM Fake de la pasarela.
- Con base (dev/prod) la tarea va a la cola `ai-light` de Cloud Tasks: necesita `TASKS_QUEUE_AI_LIGHT` (nombre corto `ai-light` o ruta completa; el nombre corto se completa con `GCP_PROJECT`/`GCP_REGION`, o con `CLOUD_SQL_INSTANCE` si faltan), `SERVICE_URL`, `TASKS_SERVICE_ACCOUNT` e `INTERNAL_OIDC_AUDIENCE`.
- Para ejecutar `TwinDbTest` sin Docker, apunta `PARCHEU_TEST_DB_URL` a un Postgres 16 desechable (ver `TestDatabase`).

## Grafo (E3.1)

- Clave de `anonId` del grafo: `HMAC_ANON_KEY` (valor directo) o `SECRET_HMAC_ANON_KEY` (nombre del secreto `projects/<p>/secrets/hmac-anon-key`, que define el Terraform de E0.5; se lee la versión `latest` en el primer uso). Con `ENV=local` y sin ninguna de las dos se usa una clave fija de desarrollo; en dev y prod, sin clave, `GET /v1/graph` falla con 500 en vez de usar una clave débil.

## Suite de invariantes de privacidad (E3.5)

```bash
# Solo la suite (≥ 1.000 casos por propiedad; la semilla queda en build/test-results)
cd backend && ./gradlew test -PsinContenedores --tests 'me.santiagoreina.parcheu.api.privacy.*'
# Reproducir una corrida concreta
PRIVACY_SEED=-6985993149784619260 ./gradlew test -PsinContenedores --tests 'me.santiagoreina.parcheu.api.privacy.*'
# Demo de mutación para la sustentación (sale ≠ 0 con mutación, 0 sin ella; deja el árbol limpio)
scripts/demo-mutacion-privacidad.sh            # quita el filtro de Pareja de GraphVisibilityPolicy
scripts/demo-mutacion-privacidad.sh memorias   # MemorySelection deja de filtrar memorias por modo
scripts/demo-mutacion-privacidad.sh ninguna    # control: suite en verde
scripts/test/demo-mutacion-privacidad.test.sh  # comprobación rápida de las anclas, sin Gradle
```

La mutación `memorias` se ancla en el filtro por modo de `MemorySelection.select`, que es la selección que usa el `TwinPromptBuilder` real de E4.3 (`memoriesFor` delega en `select` y `sanitize`; `TwinPromptBuilderDelegationTest` lo exige con ASM). El builder no repite ese filtro, así que la mutación llega a su prompt y la detecta también la propiedad "prompt del builder por modo" de `TwinPromptMemoryPropertyTest`, que arma el prompt con el builder real.

La demo exige que el archivo a mutar no tenga cambios sin commit (lo restaura con `git checkout`).
Una vista de terceros nueva se agrega en `ThirdPartyViews.registered` y con su sonda en
`ThirdPartyViewProbes.byView`; sin ambas, `ThirdPartyViewRegistryTest` falla.

## Chat (E5.1)

**Emulador de Firestore (E5.1).** Las pruebas del espejo del chat (`ChatFirestoreEmulatorTest`, y `ChatDbTest` cuando también hay Postgres) solo corren si existe `FIRESTORE_EMULATOR_HOST`. El emulador no necesita Docker, pero sí Java 21:

```bash
cd firebase && npm ci
JAVA_HOME=/opt/android-studio/jbr PATH=/opt/android-studio/jbr/bin:$PATH \
  npx firebase emulators:exec --only firestore --project demo-parcheu "cd ../backend && ./gradlew test -PsinContenedores"
```

## Perfil básico y edad (E2.6)

- La edad se verifica solo en el servidor: `POST /v1/me/age-verification` guarda `birth_date` una sola vez (inmutable) y calcula los 18 años con la fecha de hoy en America/Bogota. La app nunca decide la edad.
- Una cuenta de menor queda con `underage_at` y la puerta de `identity/TermsGate.kt` le responde 403 `UNDERAGE` en toda ruta, incluidas `session` y `terms`, salvo `GET` y `DELETE /v1/me`. Para probarlo en local, envía una fecha de menos de 18 años desde B0 o con `curl`.
- El catálogo de B0 (`GET /v1/catalog`) sale de las semillas de V2 más `V20261022_0745__universidad_unipaz.sql`; `universityId` se valida en `1..32767` (la columna es `smallint`).
## Simulación (E4.3)

- La tarea `simulate` va a la cola `simulations` de Cloud Tasks: además de lo que pide el gemelo necesita `TASKS_QUEUE_SIM`. Con `ENV=local` la cola es Fake y no ejecuta en línea: para correr una simulación local llama `POST /internal/tasks/simulate` con `{"id":"<simulationId>"}` y el token Fake de tareas (`oidc-tasks-fake`).
- El espejo `simulations/{id}` escribe en Firestore con Firebase Admin (ADC de `sa-api`) solo si hay `FIREBASE_PROJECT_ID` y `ENV` no es `local`.
- Al terminar (DONE o FAILED) se avisa a los dos participantes por el `Notifier` de E5.3 (`SIMULATION_READY` → `parcheu://results/{id}`, `SIMULATION_FAILED` → `parcheu://simulation/{id}`).
- Humos manuales contra GCP dev (no corren en CI; una simulación real cuesta 1-3 centavos de USD):

  ```bash
  cd backend && ./gradlew test -Pvertex -PsinContenedores --tests '*SimulationVertexSmokeTest*'
  ```

- `SimulationDbTest` usa Postgres real: en local, `PARCHEU_TEST_DB_URL` apuntando a un Postgres 16 desechable (ver `TestDatabase`).

## Fila de match (E4.2)

- Banderas del servicio `api`: `DEMO_USERS_ENABLED` (por defecto `true` en local y dev, `false` en prod) y `SCENARIO_MODE_ENABLED` (por defecto `false` hasta E4.8). El espejo `matchTickets/{uid}` escribe en Firestore solo si hay `FIREBASE_PROJECT_ID` y `ENV` no es `local`.
- `MatchingDbTest` usa Postgres real (incluye la prueba de concurrencia y la de `SKIP LOCKED`): en local, `PARCHEU_TEST_DB_URL` apuntando a un Postgres 16 desechable (ver `TestDatabase`).
- `MatchTicketFirestoreEmulatorTest` necesita el emulador de Firestore; sin `FIRESTORE_EMULATOR_HOST` se omite. En local, con Java 21 en el `PATH`:

  ```bash
  cd backend && firebase --config ../firebase/firebase.json emulators:exec --only firestore --project demo-parcheu \
    "./gradlew test -PsinContenedores --tests '*FirestoreEmulatorTest*'"
  ```

  (`firebase` global o `npx firebase` tras `npm ci` en `firebase/`; CI lo hace en `backend-ci`.)
