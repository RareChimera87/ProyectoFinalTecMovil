# Firebase sobre el proyecto (google-beta). Si falla porque la cuenta no aceptó
# los términos de Firebase, agrega Firebase desde la consola una vez e importa:
#   terraform import module.platform.google_firebase_project.this projects/<project_id>
resource "google_firebase_project" "this" {
  provider   = google-beta
  project    = var.project_id
  depends_on = [google_project_service.apis]
}

resource "google_firestore_database" "default" {
  project                 = var.project_id
  name                    = "(default)"
  location_id             = var.firestore_location
  type                    = "FIRESTORE_NATIVE"
  concurrency_mode        = "OPTIMISTIC"
  delete_protection_state = var.deletion_protection ? "DELETE_PROTECTION_ENABLED" : "DELETE_PROTECTION_DISABLED"
  deletion_policy         = var.deletion_protection ? "ABANDON" : "DELETE"

  depends_on = [google_project_service.apis, google_firebase_project.this]
}
