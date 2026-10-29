# Cuentas de servicio de mínimo privilegio (arquitectura.md §12). Desde
# fix/wif-deployer-dev las CREA el bootstrap (con el ADC del usuario), junto con
# sus roles de proyecto y los enlaces entre cuentas: así sa-terraform no necesita
# iam.serviceAccountAdmin ni projectIamAdmin, solo actAs sobre estas dos cuentas.
# Aquí solo se leen; el plan falla si el bootstrap no se aplicó antes.

data "google_service_account" "api" {
  project    = var.project_id
  account_id = "sa-api"
}

data "google_service_account" "tasks" {
  project    = var.project_id
  account_id = "sa-tasks"
}
