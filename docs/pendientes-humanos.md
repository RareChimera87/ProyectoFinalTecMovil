# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E0.5 · Infraestructura con Terraform

Estado al 22 oct: el proyecto `parcheu-dev` existe, tiene facturación y el bucket versionado
`gs://parcheu-dev-tfstate`. Los `plan` de bootstrap y de la plataforma de dev pasan (63 recursos) y
`check-plan.sh`/`check-iam.sh` están en verde. **No se aplicó nada con Terraform**: el entorno del agente no
tuvo permiso para ejecutar `terraform apply` (ni siquiera en dev) ni para crear `parcheu-prod`. La cuenta de facturación está en `~/.parcheu/terraform.env`.

1. **Aplicar el bootstrap de dev** (crea WIF, `sa-deployer`, `sa-terraform`, `sa-terraform-plan` y el presupuesto de facturación; importa el bucket):

   ```bash
   source ~/.parcheu/terraform.env
   cd infra/terraform/bootstrap
   terraform init -reconfigure -backend-config=bucket=parcheu-dev-tfstate -backend-config=prefix=bootstrap
   terraform apply -var environment=dev
   ```

2. **Cargar los secretos de GitHub de dev** con las salidas del bootstrap (desde `infra/terraform/bootstrap`):

   ```bash
   R=RareChimera87/ProyectoFinalTecMovil
   terraform output -raw wif_provider              | gh secret set WIF_PROVIDER -R $R
   terraform output -raw deployer_service_account  | gh secret set WIF_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_plan_service_account | gh secret set TF_PLAN_SERVICE_ACCOUNT_DEV -R $R
   terraform output -raw terraform_service_account | gh secret set TF_SERVICE_ACCOUNT_DEV -R $R   # apply (E0.6)
   ```

   Luego vuelve a correr el job `infra-ci` del PR de E0.5 (`gh run rerun --failed <id>`): el `plan` de dev vía
   WIF debe quedar verde.

3. **Aplicar la plataforma de dev** (o dejar que lo haga CI cuando E0.6 agregue el `apply` al mezclar).
   Tarda unos 15 min por Cloud SQL. Costo esperado: unos USD 10/mes (Cloud SQL `db-f1-micro`; Cloud Run escala a 0):

   ```bash
   cd infra/terraform/envs/dev
   terraform init && terraform plan -out=plan.tfplan
   terraform show -json plan.tfplan > plan.json
   ../../../../scripts/check-plan.sh plan.json && ../../../../scripts/check-iam.sh plan.json
   terraform apply plan.tfplan
   ../../../../scripts/cargar-secretos.sh dev      # valor de hmac-anon-key
   ```

   Si `google_firebase_project` falla por términos sin aceptar: abre https://console.firebase.google.com,
   "Agregar proyecto" → elige `parcheu-dev` (5 min) y luego
   `terraform import module.platform.google_firebase_project.this projects/parcheu-dev` y repite el `apply`.

4. **Cupo de facturación lleno (bloquea prod).** La cuenta de facturación ya llegó a su cupo de proyectos
   vinculados y Google respondió `Cloud billing quota exceeded`. Antes de crear prod, revisa
   `gcloud billing projects list --billing-account=$TF_VAR_billing_account`, desvincula uno que no uses
   (`gcloud billing projects unlink <id>`) o pide más cupo en
   https://support.google.com/code/contact/billing_quota_increase.

5. **Crear prod y su bootstrap** (solo tú):

   ```bash
   source ~/.parcheu/terraform.env
   BILLING_ACCOUNT=$TF_VAR_billing_account scripts/crear-proyectos.sh prod
   ```

   Si el ID `parcheu-prod` está ocupado el script usa un sufijo (lo deja en `~/.parcheu/proyectos.env`):
   en ese caso cambia el ID en `infra/terraform/bootstrap/main.tf`, `infra/terraform/envs/prod/main.tf` y
   `docs/arquitectura.md` §12 en un PR. Después:

   ```bash
   cd infra/terraform/bootstrap
   terraform init -reconfigure -backend-config=bucket=parcheu-prod-tfstate -backend-config=prefix=bootstrap
   terraform apply -var environment=prod
   R=RareChimera87/ProyectoFinalTecMovil
   terraform output -raw wif_provider              | gh secret set WIF_PROVIDER_PROD -R $R
   terraform output -raw deployer_service_account  | gh secret set WIF_SERVICE_ACCOUNT_PROD -R $R
   terraform output -raw terraform_plan_service_account | gh secret set TF_PLAN_SERVICE_ACCOUNT_PROD -R $R
   ```

6. **Plan y apply de prod (27 oct, solo tú).** El plan de prod se validó con un número de proyecto ficticio
   (64 recursos, `check-plan.sh` y `check-iam.sh` en verde: API con mínimo 1 y máximo 5, backups y PITR,
   push de RTDN); el real necesita que el proyecto exista:

   ```bash
   cd infra/terraform/envs/prod
   terraform init && terraform plan -out=plan.tfplan
   terraform show -json plan.tfplan > plan.json
   ../../../../scripts/check-plan.sh plan.json && ../../../../scripts/check-iam.sh plan.json
   terraform apply plan.tfplan
   ../../../../scripts/cargar-secretos.sh prod
   ```

7. **Antes del primer `migrate` (E0.4), en cada entorno:** dar al usuario IAM de `sa-api` la propiedad de la
   base, como `postgres` desde Cloud SQL Studio (consola → Cloud SQL → `parcheu-<env>` → Cloud SQL Studio):
   `ALTER DATABASE parcheu OWNER TO "sa-api@parcheu-<env>.iam"; ALTER SCHEMA public OWNER TO "sa-api@parcheu-<env>.iam";`
