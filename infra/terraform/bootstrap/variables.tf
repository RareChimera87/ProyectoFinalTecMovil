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
