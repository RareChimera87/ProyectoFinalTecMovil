variable "environment" {
  description = "Entorno a preparar: dev o prod."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment debe ser dev o prod."
  }
}

variable "billing_account" {
  description = "ID de la cuenta de facturación (TF_VAR_billing_account). No se versiona."
  type        = string
}

variable "github_repository" {
  description = "Repositorio de GitHub (owner/nombre) autorizado a usar WIF."
  type        = string
  default     = "RareChimera87/ProyectoFinalTecMovil"
}

# IDs numéricos del repo y de su dueño (`gh api repos/RareChimera87/ProyectoFinalTecMovil --jq '.id,.owner.id'`,
# 28 oct 2026). El pool los exige en vez del nombre: un nombre se puede liberar y volver a registrar
# (repo borrado o usuario renombrado), un ID no se reutiliza. Si el repo se transfiere, cámbialos aquí.
variable "github_repository_id" {
  description = "ID numérico del repositorio (claim repository_id del token OIDC de GitHub)."
  type        = string
  default     = "1428919058"
}

variable "github_repository_owner_id" {
  description = "ID numérico del dueño del repositorio (claim repository_owner_id)."
  type        = string
  default     = "82991195"
}
