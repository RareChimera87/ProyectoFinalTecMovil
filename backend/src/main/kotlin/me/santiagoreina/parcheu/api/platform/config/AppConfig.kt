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
 * - `INTERNAL_OIDC_AUDIENCE`: audiencia de los tokens OIDC internos (la custom audience que fija E0.5,
 *   `https://api.parcheu-<env>.internal`). Si falta se usa `SERVICE_URL` como respaldo.
 * - `TASKS_SERVICE_ACCOUNT`: correo de `sa-tasks`, única identidad válida en las rutas `/internal`.
 * - `FIREBASE_PROJECT_ID`: proyecto de Firebase Auth; si falta se usa `GCP_PROJECT` (lo define E0.5).
 * - `LEGAL_TEXT_VERSION`: versión vigente de los Términos y la Política de privacidad (E2.1, ADR-0017). Debe coincidir
 *   con la de las páginas web (E8.4); al subirla, todos los usuarios vuelven a `NEEDS_TERMS`.
 *
 * Obligatorias según el modo (solo `serve` valida Firebase y OIDC; `migrate`, `seed-demo` y `purge-demo` no los usan):
 * - todos: `ENV`.
 * - `serve` en dev/prod: proyecto de Firebase (`FIREBASE_PROJECT_ID` o `GCP_PROJECT`), audiencia
 *   (`INTERNAL_OIDC_AUDIENCE` o `SERVICE_URL`, https) y `TASKS_SERVICE_ACCOUNT`.
 * - `migrate`: `DB_NAME` y `CLOUD_SQL_INSTANCE` o `DB_URL`.
 * - `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `CLOUD_SQL_INSTANCE` o `DB_URL` (JDBC directo).
 */
data class AppConfig(
    val env: Env,
    val port: Int,
    val version: String,
    /** Audiencia esperada en los tokens OIDC de `/internal`. */
    val oidcAudience: String,
    val tasksServiceAccount: String,
    val firebaseProjectId: String?,
    val db: DbConfig?,
    /** Versión vigente del texto legal; se guarda en `consents.text_version` al aceptar. */
    val legalTextVersion: String = DEFAULT_LEGAL_TEXT_VERSION,
) {
    companion object {
        /**
         * [serving] es verdadero solo en el modo `serve`: los jobs no necesitan Firebase ni OIDC,
         * así que no se les exige esa configuración.
         */
        fun fromEnv(vars: Map<String, String> = System.getenv(), serving: Boolean = true): AppConfig {
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
                oidcAudience = vars["INTERNAL_OIDC_AUDIENCE"].orNull() ?: vars["SERVICE_URL"].orNull() ?: "http://localhost:8080",
                tasksServiceAccount = vars["TASKS_SERVICE_ACCOUNT"].orNull() ?: LOCAL_TASKS_ACCOUNT,
                firebaseProjectId = vars["FIREBASE_PROJECT_ID"].orNull() ?: vars["GCP_PROJECT"].orNull(),
                db = db,
                legalTextVersion = vars["LEGAL_TEXT_VERSION"].orNull() ?: DEFAULT_LEGAL_TEXT_VERSION,
            )
            if (serving) config.validateForServe(explicitTasksAccount = !vars["TASKS_SERVICE_ACCOUNT"].isNullOrBlank())
            return config
        }

        /** Versión del texto legal publicada con la prueba cerrada (E8.4 usa la misma). */
        const val DEFAULT_LEGAL_TEXT_VERSION = "2026-10-29"

        private const val LOCAL_TASKS_ACCOUNT = "sa-tasks@parcheu-local.iam.gserviceaccount.com"

        private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }
    }

    /** Falla al arrancar el servicio, no en la primera petición, si falta algo que el entorno exige. */
    fun validateForServe(explicitTasksAccount: Boolean = true) {
        if (env != Env.LOCAL) {
            require(!firebaseProjectId.isNullOrBlank()) { "FIREBASE_PROJECT_ID (o GCP_PROJECT) es obligatorio en $env" }
            require(oidcAudience.startsWith("https://")) { "INTERNAL_OIDC_AUDIENCE (o SERVICE_URL) debe ser https en $env" }
            require(explicitTasksAccount) { "TASKS_SERVICE_ACCOUNT es obligatorio en $env" }
            require(tasksServiceAccount.endsWith(".iam.gserviceaccount.com")) {
                "TASKS_SERVICE_ACCOUNT debe ser un correo de cuenta de servicio en $env"
            }
        }
    }
}
