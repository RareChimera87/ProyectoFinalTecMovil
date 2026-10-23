# Plataforma de ParcheU para un entorno (arquitectura.md §12, ADR-0014).

locals {
  apis = [
    "aiplatform.googleapis.com",
    # Google Play Developer API (E7.1): la cuota y la habilitación van en el
    # proyecto de la cuenta que llama (sa-play en prod).
    "androidpublisher.googleapis.com",
    "artifactregistry.googleapis.com",
    "billingbudgets.googleapis.com",
    "cloudscheduler.googleapis.com",
    "cloudtasks.googleapis.com",
    "cloudtrace.googleapis.com",
    "fcm.googleapis.com",
    "firebase.googleapis.com",
    "firebasehosting.googleapis.com",
    "firebaseinstallations.googleapis.com",
    "firebaserules.googleapis.com",
    "firestore.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "identitytoolkit.googleapis.com",
    "logging.googleapis.com",
    "monitoring.googleapis.com",
    "pubsub.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "securetoken.googleapis.com",
    "sqladmin.googleapis.com",
  ]

  labels = {
    app = "parcheu"
    env = var.environment
  }

  # Audiencia de los tokens OIDC de Cloud Tasks, Scheduler y Pub/Sub. Es un valor
  # fijo (custom audience de Cloud Run) para no depender de la URL del servicio;
  # el backend la recibe en INTERNAL_OIDC_AUDIENCE y la verifica en /internal/*.
  internal_audience = "https://api.parcheu-${var.environment}.internal"
}

data "google_project" "this" {
  project_id = var.project_id
}

resource "google_project_service" "apis" {
  for_each           = toset(local.apis)
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}
