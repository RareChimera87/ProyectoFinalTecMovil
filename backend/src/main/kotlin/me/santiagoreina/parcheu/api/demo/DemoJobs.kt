package me.santiagoreina.parcheu.api.demo

import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.createDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Modos `seed-demo` y `purge-demo` (Cloud Run Jobs con la misma imagen, ADR-0027). Ambos necesitan la base
 * (`DB_NAME` y `CLOUD_SQL_INSTANCE` o `DB_URL`); `purge-demo` limpia además Firestore si hay proyecto
 * (`FIREBASE_PROJECT_ID` o `GCP_PROJECT`) y `ENV` no es `local`.
 */
object DemoJobs {
    private val log = LoggerFactory.getLogger(javaClass)

    fun seed(config: AppConfig, vars: Map<String, String> = System.getenv(), clock: Clock = Clock.systemUTC()): SeedResult {
        val settings = DemoSettings.fromEnv(config.env, vars)
        return withDatabase(config, "seed-demo") { db -> DemoSeeder(db, settings).seed(clock.instant()) }
            .also { log.info("seed-demo terminó: {}", it) }
    }

    fun purge(config: AppConfig): PurgeResult {
        val project = config.firebaseProjectId
        val mirrors = if (config.env == Env.LOCAL || project.isNullOrBlank()) {
            DemoMirrorCleaner.NONE
        } else {
            FirestoreDemoMirrorCleaner.forProject(project)
        }
        return withDatabase(config, "purge-demo") { db -> runBlocking { DemoPurger(db, mirrors).purge() } }
    }

    private fun <T> withDatabase(config: AppConfig, job: String, block: (Database) -> T): T {
        val dbConfig = requireNotNull(config.db) { "$job requiere DB_NAME y la conexión (CLOUD_SQL_INSTANCE o DB_URL)" }
        return createDataSource(dbConfig, maxPoolSize = 2).use { ds -> block(connectExposed(ds)) }
    }
}
