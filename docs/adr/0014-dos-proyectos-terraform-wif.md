# ADR-0014: Dos proyectos de GCP con Terraform y WIF

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.5, E0.6

## Contexto
Las reglas exigen infraestructura como código, entornos dev y prod y secretos fuera del repo.

## Decisión
- Proyectos `parcheu-dev` y `parcheu-prod` (cada uno con su Firebase), región us-central1.
- Terraform 1.15: `bootstrap` (estado y WIF), `modules/platform`, `envs/dev`, `envs/prod`; estado en GCS versionado.
- GitHub Actions se autentica con Workload Identity Federation restringida al repositorio; sin llaves JSON.
- CI hace `plan` en PR y `apply` en dev; el `apply` de prod lo ejecuta solo el usuario.

## Alternativas consideradas
- Un proyecto con dos entornos: IAM, cuotas y datos mezclados.
- Configuración manual por consola: no reproducible.

## Consecuencias
- Aislamiento real entre dev y prod; doble configuración de Firebase y SHA.
- El bootstrap requiere al usuario una vez (con su ADC ya autenticado).

## Enmienda (E10.2, 22 oct 2026): cuenta de servicio de Play
- La cuenta de servicio para la API de Play es `sa-play` en **`parcheu-prod`** (la misma que usará E7.1); no existe una cuenta de Play en `parcheu-dev`, porque el perímetro de IAM de dev es más amplio que el de prod y publica en la app real.
- La publicación (workflow `android-release`) se autentica por WIF suplantando a `sa-play`, sin llaves, con Gradle Play Publisher y credenciales por defecto. El proveedor de WIF restringe la suplantación a los environments `play` y `play-alpha`.
- **Excepción temporal:** mientras el bootstrap de WIF de prod no exista, se admite una llave JSON de `sa-play` guardada solo como secreto del environment `play` / `play-alpha` (`PLAY_SERVICE_ACCOUNT_JSON`). Se elimina (llave y secreto) al activarse el WIF; el workflow prefiere WIF cuando el secreto no existe. Verificar la compatibilidad de WIF con GPP queda pendiente de la primera corrida real (criterio de aceptación de E10.2).

## Enmienda (fix/wif-deployer-dev, 28 oct 2026): WIF por rama y tag, y sa-terraform sin IAM
- **Quién suplanta a cada cuenta.** `sa-deployer` de dev solo acepta la rama `dev` (`attribute.ref/refs/heads/dev`): antes aceptaba cualquier rama o PR del repo y con eso un PR podía reescribir las reglas de Firestore o firmar tokens de Firebase. En prod, `sa-deployer` exige el environment `production` **y** una ref de tag `v*`; `sa-play`, el environment `play`/`play-alpha` y la ref que admiten sus políticas. Como un `principalSet` no admite prefijos ni dos atributos, el proveedor mapea el atributo derivado `env_ref` = `<environment>@<ref>` (`tag` para `refs/tags/v*`). El enlace de `sa-play` pasa de `gcloud` a `modules/platform/play.tf`.
- **El pool se ata a IDs**, no al nombre del repo: `repository_id` y `repository_owner_id` (un nombre liberado se puede volver a registrar).
- **Las identidades de ejecución (`sa-api`, `sa-tasks`), sus roles de proyecto y los enlaces entre cuentas se crean en `bootstrap`** con el ADC del usuario. Así `sa-terraform` (apply de dev en CI) no necesita `serviceAccountAdmin`, `serviceAccountUser` de proyecto ni `projectIamAdmin`: solo actAs sobre esas dos cuentas, los admins de cada servicio y un rol personalizado sin acceso a valores de secretos ni a documentos de Firestore. Alternativa descartada: dejarlas en la plataforma con `setIamPolicy` de cuentas a nivel de proyecto (las condiciones de IAM no admiten `resource.name` en cuentas de servicio, así que equivale a `serviceAccountAdmin`).
- **`iam.disableServiceAccountKeyCreation`** no se pone en código: los proyectos no tienen organización y las políticas de organización solo existen dentro de una. La llave de `sa-homelab` (ADR-0033) y la temporal de `sa-play` siguen siendo las únicas admitidas; si los proyectos entran a una organización, se activa la política (paso en `docs/pendientes-humanos.md`).
