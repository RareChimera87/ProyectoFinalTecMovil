# infra

Terraform: `bootstrap`, `modules/platform`, `envs/dev`, `envs/prod` (ADR-0014). Se crea en la feature E0.5.
El `terraform apply` de prod lo ejecuta solo el usuario.
Las identidades (WIF, `sa-deployer`, `sa-terraform`, `sa-api`, `sa-tasks`) nacen en `bootstrap/` con el ADC del usuario; detalle en [`terraform/README.md`](terraform/README.md#cuentas-de-servicio).
