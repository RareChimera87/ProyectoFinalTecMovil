package me.santiagoreina.parcheu.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.santiagoreina.parcheu.api.demo.DemoJobs
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.db.createDataSource
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/** Modos de arranque. La misma imagen sirve para el servicio y los Cloud Run Jobs. */
enum class Mode(val arg: String) {
    SERVE("serve"),
    MIGRATE("migrate"),
    SEED_DEMO("seed-demo"),
    PURGE_DEMO("purge-demo"),
    ;

    companion object {
        fun parse(arg: String?): Mode? =
            if (arg == null) SERVE else entries.firstOrNull { it.arg == arg }
    }
}

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("me.santiagoreina.parcheu.api.Main")
    val mode = Mode.parse(args.firstOrNull())
    if (mode == null) {
        log.error("Modo desconocido '{}'. Modos válidos: {}", args.first(), Mode.entries.joinToString { it.arg })
        exitProcess(2)
    }
    val config = AppConfig.fromEnv()
    when (mode) {
        Mode.SERVE -> {
            val container = AppContainer.fromConfig(config)
            log.info("Iniciando servicio {} en el puerto {} (env={})", config.version, config.port, config.env)
            embeddedServer(Netty, port = config.port) { module(container) }.start(wait = true)
        }
        Mode.MIGRATE -> {
            val dbConfig = requireNotNull(config.db) { "migrate requiere DB_NAME y la conexión (CLOUD_SQL_INSTANCE o DB_URL)" }
            createDataSource(dbConfig, maxPoolSize = 2).use { ds ->
                log.info("Migraciones aplicadas: {}", runMigrations(ds))
            }
        }
        Mode.SEED_DEMO -> DemoJobs.seed()
        Mode.PURGE_DEMO -> DemoJobs.purge()
    }
}
