package me.santiagoreina.parcheu.api.platform.config

/** Entorno de ejecución. `LOCAL` es el único que admite tokens Fake y nunca se asume por defecto. */
enum class Env { LOCAL, DEV, PROD }

/** Datos de conexión a Postgres. En Cloud Run se usa [cloudSqlInstance] (conector IAM, sin VPC). */
data class DbConfig(
    val name: String,
    val user: String,
    val password: String?,
    /** `proyecto:region:instancia`. Si es nulo se usa [jdbcUrl] tal cual. */
    val cloudSqlInstance: String?,
    val jdbcUrl: String?,
)

/**
 * Configuración del servicio, leída solo de variables de entorno (nunca de archivos del repo).
 *
 * - `ENV` (local|dev|prod, obligatorio y sin valor por defecto: si falta, el arranque falla), `PORT` (8080), `APP_VERSION` (SHA del commit).
 * - `SERVICE_URL`: URL pública del servicio; es la audiencia de los tokens OIDC internos.
 * - `TASKS_SERVICE_ACCOUNT`: correo de `sa-tasks`, única identidad válida en las rutas `/internal`.
 * - `FIREBASE_PROJECT_ID`: proyecto de Firebase Auth.
 * - `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `CLOUD_SQL_INSTANCE` o `DB_URL` (JDBC directo).
 */
data class AppConfig(
    val env: Env,
    val port: Int,
    val version: String,
    val serviceUrl: String,
    val tasksServiceAccount: String,
    val firebaseProjectId: String?,
    val db: DbConfig?,
) {
    companion object {
        fun fromEnv(vars: Map<String, String> = System.getenv()): AppConfig {
            // Modo cerrado por defecto: sin ENV nunca se cablean tokens Fake.
            val raw = vars["ENV"]?.takeIf { it.isNotBlank() }
                ?: error("ENV es obligatorio (local, dev o prod); sin él no se arranca para no abrir la autenticación")
            val env = Env.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: error("ENV inválido: '$raw' (se espera local, dev o prod)")
            // Cloud Run define K_SERVICE (servicios) y CLOUD_RUN_JOB (jobs): ahí LOCAL está prohibido.
            require(env != Env.LOCAL || (vars["K_SERVICE"].isNullOrBlank() && vars["CLOUD_RUN_JOB"].isNullOrBlank())) {
                "ENV=local no se admite en Cloud Run (K_SERVICE/CLOUD_RUN_JOB definidos): usa dev o prod"
            }
            val db = vars["DB_NAME"]?.let { name ->
                DbConfig(
                    name = name,
                    user = vars["DB_USER"] ?: "postgres",
                    password = vars["DB_PASSWORD"],
                    cloudSqlInstance = vars["CLOUD_SQL_INSTANCE"],
                    jdbcUrl = vars["DB_URL"],
                )
            }
            val config = AppConfig(
                env = env,
                port = vars["PORT"]?.toIntOrNull() ?: 8080,
                version = vars["APP_VERSION"]?.takeIf { it.isNotBlank() } ?: "dev",
                serviceUrl = vars["SERVICE_URL"] ?: "http://localhost:8080",
                tasksServiceAccount = vars["TASKS_SERVICE_ACCOUNT"] ?: "sa-tasks@parcheu-local.iam.gserviceaccount.com",
                firebaseProjectId = vars["FIREBASE_PROJECT_ID"],
                db = db,
            )
            config.validate()
            return config
        }
    }

    /** Falla al arrancar, no en la primera petición, si falta algo que el entorno exige. */
    fun validate() {
        if (env != Env.LOCAL) {
            require(!firebaseProjectId.isNullOrBlank()) { "FIREBASE_PROJECT_ID es obligatorio en $env" }
            require(serviceUrl.startsWith("https://")) { "SERVICE_URL debe ser https en $env" }
            require(tasksServiceAccount.endsWith(".iam.gserviceaccount.com")) {
                "TASKS_SERVICE_ACCOUNT debe ser un correo de cuenta de servicio en $env"
            }
        }
    }
}
