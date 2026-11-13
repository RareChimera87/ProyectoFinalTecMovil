package me.santiagoreina.parcheu.api

import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.santiagoreina.parcheu.api.billing.ManualPlusJob
import me.santiagoreina.parcheu.api.demo.DemoJobs
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.db.JOB_POOL_SIZE
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

    /** Plus manual acotado (enmienda de ADR-0013): Cloud Run Job `grant-plus`, sin ruta HTTP. */
    GRANT_PLUS("grant-plus"),
    REVOKE_PLUS("revoke-plus"),
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
    val config = AppConfig.fromEnv(serving = mode == Mode.SERVE)
    when (mode) {
        Mode.SERVE -> {
            val container = AppContainer.fromConfig(config)
            log.info("Iniciando servicio {} en el puerto {} (env={})", config.version, config.port, config.env)
            container.background?.start()
            embeddedServer(Netty, port = config.port) {
                module(container)
                monitor.subscribe(ApplicationStopping) { container.background?.close() }
            }.start(wait = true)
        }
        Mode.MIGRATE -> {
            val dbConfig = requireNotNull(config.db) { "migrate requiere DB_NAME y la conexión (CLOUD_SQL_INSTANCE o DB_URL)" }
            createDataSource(dbConfig, maxPoolSize = minOf(JOB_POOL_SIZE, dbConfig.maxPoolSize)).use { ds ->
                log.info("Migraciones aplicadas: {}", runMigrations(ds))
            }
        }
        Mode.SEED_DEMO -> DemoJobs.seed(config)
        Mode.PURGE_DEMO -> DemoJobs.purge(config)
        Mode.GRANT_PLUS, Mode.REVOKE_PLUS -> {
            val code = ManualPlusJob.run(config, revoke = mode == Mode.REVOKE_PLUS, args = args.drop(1))
            if (code != ManualPlusJob.EXIT_OK) exitProcess(code)
        }
    }
}
