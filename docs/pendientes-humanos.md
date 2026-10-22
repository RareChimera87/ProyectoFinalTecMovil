# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E0.6 · Pipelines de CI/CD

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
