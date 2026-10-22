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
