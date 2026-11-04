# Cuenta de servicio de Google Play (ADR-0013 y enmienda de ADR-0014; E7.1).
# Solo en parcheu-prod: la app publicada (flavor prod) y su suscripción viven en
# un único paquete de Play. No tiene roles de IAM de GCP: sus permisos se dan en
# Play Console (Usuarios y permisos). La comparten el backend (verify y RTDN,
# por suplantación) y la publicación desde CI (E10.2, por WIF).
resource "google_service_account" "play" {
  count        = var.play_service_account_enabled ? 1 : 0
  project      = var.project_id
  account_id   = "sa-play"
  display_name = "ParcheU Google Play"
  description  = "API de Google Play Developer: verificación de compras (api) y publicación (CI, E10.2)."
  depends_on   = [google_project_service.apis]
}

# La API (sa-api) pide tokens de corta vida de sa-play: sin llaves JSON.
resource "google_service_account_iam_member" "api_impersonates_play" {
  count              = var.play_service_account_enabled ? 1 : 0
  service_account_id = google_service_account.play[0].name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${data.google_service_account.api.email}"
}

# Publicación desde CI (E10.2): GitHub suplanta a sa-play por WIF. Antes se hacía a
# mano con gcloud y solo miraba el environment; ahora el principal es el atributo
# derivado `env_ref` del bootstrap ("<environment>@<ref>", con "tag" para refs/tags/v*),
# así que además del environment exige la misma ref que sus políticas de despliegue:
# `play` desde dev, main o un tag v*; `play-alpha` desde main (workflow_run) o un tag v*.
# El pool `github` lo crea bootstrap/main.tf en este mismo proyecto.
locals {
  play_wif_env_refs = var.play_service_account_enabled ? toset([
    "play@refs/heads/dev",
    "play@refs/heads/main",
    "play@tag",
    "play-alpha@refs/heads/main",
    "play-alpha@tag",
  ]) : toset([])
}

resource "google_service_account_iam_member" "play_wif" {
  for_each           = local.play_wif_env_refs
  service_account_id = google_service_account.play[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/projects/${data.google_project.this.number}/locations/global/workloadIdentityPools/github/attribute.env_ref/${each.value}"
}
