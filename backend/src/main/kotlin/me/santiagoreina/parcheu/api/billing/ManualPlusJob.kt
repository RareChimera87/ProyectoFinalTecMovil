package me.santiagoreina.parcheu.api.billing

import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.db.JOB_POOL_SIZE
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.createDataSource
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Modos `grant-plus` y `revoke-plus` del contenedor (Cloud Run Job `grant-plus`, enmienda de ADR-0013):
 *
 * ```
 * grant-plus  (--email=<correo> | --uid=<firebase uid>) [--days=1..30, por defecto 7; --days=0 revoca]
 * revoke-plus (--email=<correo> | --uid=<firebase uid>)
 * ```
 *
 * Sin parámetros falla (los argumentos por defecto del job son solo `grant-plus`). Códigos de salida:
 * 0 hecho (o nada que revocar), 2 argumentos inválidos, 3 rechazo ([ManualPlusRefused]).
 */
object ManualPlusJob {
    private val log = LoggerFactory.getLogger(javaClass)

    const val EXIT_OK = 0
    const val EXIT_USAGE = 2
    const val EXIT_REFUSED = 3

    /** Petición ya validada: [days] = 0 es revocar. */
    data class Request(val selector: UserSelector, val days: Int)

    /** Lee los argumentos (sin el modo). Lanza [IllegalArgumentException] con un mensaje sin el correo. */
    fun parse(revoke: Boolean, args: List<String>): Request {
        var email: String? = null
        var uid: String? = null
        var days: Int? = null
        args.forEach { arg ->
            val key = arg.substringBefore('=')
            val value = arg.substringAfter('=', missingDelimiterValue = "").trim()
            require(arg.contains('=') && value.isNotEmpty()) { "Argumento inválido '$key': se espera --clave=valor" }
            when (key) {
                "--email" -> { require(email == null) { "--email repetido" }; email = value }
                "--uid" -> { require(uid == null) { "--uid repetido" }; uid = value }
                "--days" -> {
                    require(!revoke) { "revoke-plus no acepta --days" }
                    require(days == null) { "--days repetido" }
                    days = value.toIntOrNull()?.takeIf { it in 0..ManualPlusService.MAX_MANUAL_DAYS }
                        ?: throw IllegalArgumentException("--days debe ser un entero entre 1 y ${ManualPlusService.MAX_MANUAL_DAYS} (0 revoca)")
                }
                else -> throw IllegalArgumentException("Argumento desconocido '$key' (válidos: --email, --uid, --days)")
            }
        }
        require((email == null) != (uid == null)) { "Indica exactamente uno: --email=<correo> o --uid=<firebase uid>" }
        val selector = email?.let { e ->
            // Correo exacto: igualdad, nunca LIKE; los comodines se rechazan para que nadie crea que funcionan.
            require(e.count { it == '@' } == 1 && !e.startsWith('@') && !e.endsWith('@')) { "--email no es un correo" }
            require(e.none { it == '%' || it == '*' || it.isWhitespace() }) { "--email no admite comodines ni espacios" }
            UserSelector.ByEmail(e)
        } ?: UserSelector.ByFirebaseUid(uid!!)
        return Request(selector, if (revoke) 0 else days ?: ManualPlusService.DEFAULT_MANUAL_DAYS)
    }

    /** Ejecuta la petición sobre [service] y devuelve el código de salida. */
    fun execute(service: ManualPlusService, request: Request): Int = try {
        val outcome = if (request.days == 0) service.revoke(request.selector) else service.grant(request.selector, request.days)
        log.info("Resultado: {}", describe(outcome))
        EXIT_OK
    } catch (e: ManualPlusRefused) {
        log.error("Rechazado ({}): {}", e.reason, e.message)
        EXIT_REFUSED
    } catch (e: IllegalArgumentException) {
        log.error("Argumentos inválidos: {}", e.message)
        EXIT_USAGE
    }

    /** Punto de entrada desde `Main`: [args] sin el modo. */
    fun run(config: AppConfig, revoke: Boolean, args: List<String>, clock: Clock = Clock.systemUTC()): Int {
        val request = try {
            parse(revoke, args)
        } catch (e: IllegalArgumentException) {
            log.error("Argumentos inválidos: {}. Uso: grant-plus (--email=<correo> | --uid=<uid>) [--days=1..30] | revoke-plus (--email=… | --uid=…)", e.message)
            return EXIT_USAGE
        }
        val dbConfig = requireNotNull(config.db) { "grant-plus requiere DB_NAME y la conexión (CLOUD_SQL_INSTANCE o DB_URL)" }
        return createDataSource(dbConfig, maxPoolSize = minOf(JOB_POOL_SIZE, dbConfig.maxPoolSize)).use { ds ->
            val db = connectExposed(ds)
            execute(ManualPlusService(ExposedBillingStore(db), ExposedManualPlusUsers(db), clock), request)
        }
    }

    private fun describe(outcome: ManualPlusOutcome): String = when (outcome) {
        is ManualPlusOutcome.Granted -> when {
            !outcome.changed -> "usuario ${outcome.userId} ya tenía Plus manual hasta ${outcome.expiresAt}: sin cambios"
            outcome.extended -> "Plus manual de ${outcome.userId} extendido hasta ${outcome.expiresAt}"
            else -> "Plus manual concedido a ${outcome.userId} hasta ${outcome.expiresAt}"
        }
        is ManualPlusOutcome.Revoked -> "Plus manual de ${outcome.userId} retirado"
        is ManualPlusOutcome.NothingToRevoke -> "usuario ${outcome.userId} sin Plus manual vigente: nada que revocar"
    }
}
