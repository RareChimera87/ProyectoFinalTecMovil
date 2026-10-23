terraform {
  required_version = ">= 1.15.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }

  # Configuración parcial: bucket y prefijo se pasan en `terraform init`
  # (ver infra/terraform/README.md). El bucket lo crea scripts/crear-proyectos.sh.
  backend "gcs" {}
}

provider "google" {
  project               = local.project_id
  region                = local.region
  billing_project       = local.project_id
  user_project_override = true
}
