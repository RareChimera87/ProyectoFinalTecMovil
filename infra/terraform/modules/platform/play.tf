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
  member             = "serviceAccount:${google_service_account.api.email}"
}
