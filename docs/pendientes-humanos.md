# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

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
