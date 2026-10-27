package me.santiagoreina.parcheu.e2e

/**
 * Configuración del smoke e2e (E10.6), solo de variables de entorno. Nunca se versiona nada de aquí.
 *
 * - `E2E_BASE_URL`: URL de la API desplegada (Cloud Run de dev), sin barra final.
 * - `E2E_FIREBASE_PROJECT_ID`: proyecto de Firebase (parcheu-dev).
 * - `E2E_FIREBASE_API_KEY`: clave web del proyecto (`api_key.current_key` de google-services.json); solo sirve para
 *   canjear tokens personalizados por ID tokens.
 * - `E2E_SERVICE_ACCOUNT` (opcional): correo de la cuenta que firma los tokens personalizados vía IAM `signBlob`
 *   (en CI es `sa-deployer`, que se suplanta a sí misma; ver docs/pendientes-humanos.md).
 * - `E2E_REQUIRED=true`: si falta algo, el test falla en vez de omitirse (así lo fija deploy-dev).
 */
data class E2eConfig(
    val baseUrl: String,
    val projectId: String,
    val apiKey: String,
    val serviceAccount: String?,
) {
    companion object {
        fun missing(vars: Map<String, String> = System.getenv()): List<String> =
            listOf("E2E_BASE_URL", "E2E_FIREBASE_PROJECT_ID", "E2E_FIREBASE_API_KEY").filter { vars[it].isNullOrBlank() }

        fun required(vars: Map<String, String> = System.getenv()): Boolean = vars["E2E_REQUIRED"].equals("true", ignoreCase = true)

        /** Null si falta alguna variable obligatoria. */
        fun fromEnv(vars: Map<String, String> = System.getenv()): E2eConfig? {
            if (missing(vars).isNotEmpty()) return null
            return E2eConfig(
                baseUrl = vars.getValue("E2E_BASE_URL").trim().trimEnd('/'),
                projectId = vars.getValue("E2E_FIREBASE_PROJECT_ID").trim(),
                apiKey = vars.getValue("E2E_FIREBASE_API_KEY").trim(),
                serviceAccount = vars["E2E_SERVICE_ACCOUNT"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
