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
