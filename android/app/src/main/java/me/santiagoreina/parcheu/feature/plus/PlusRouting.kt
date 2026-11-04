package me.santiagoreina.parcheu.feature.plus

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Paywall

/*
 * Cómo un error premium (402) o un cupo agotado llega a G1 o a la hoja G2 (E7.3). Las rutas están
 * congeladas (ADR-0006): G1 y G2 comparten la ruta `Paywall(source)` y el `source` decide cuál se muestra.
 * Un `source` normal (`home`, `fof`, ...) abre G1; `limit:<KIND>:<resetsAt>` abre la hoja G2 de ese cupo.
 */

/** Qué pantalla de la ruta [Paywall] corresponde a un `source`. */
sealed interface PlusEntry {
    /** G1, con el origen para analítica y regreso. */
    data class Paywall(val source: String) : PlusEntry

    /** G2: hoja de límite del cupo [kind]; [resetsAt] es cuándo se renueva, si se sabe. */
    data class Limit(val kind: QuotaKind, val resetsAt: Instant?) : PlusEntry
}

object PlusSources {
    private const val LIMIT_PREFIX = "limit:"
    private const val UNKNOWN = "-"

    /** `source` de la ruta [Paywall] que abre la hoja G2 de [kind]. */
    fun limit(kind: QuotaKind, resetsAt: Instant? = null): String =
        "$LIMIT_PREFIX${kind.name}:${resetsAt?.epochSeconds ?: UNKNOWN}"

    /** Interpreta el `source` de la ruta; cualquier cosa que no sea un límite válido abre G1. */
    fun parse(source: String): PlusEntry {
        if (!source.startsWith(LIMIT_PREFIX)) return PlusEntry.Paywall(source)
        val parts = source.removePrefix(LIMIT_PREFIX).split(':')
        val kind =
            parts.firstOrNull()?.let { name -> QuotaKind.entries.firstOrNull { it.name == name } }
                ?: return PlusEntry.Paywall(source)
        val resetsAt = parts.getOrNull(1)?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
        return PlusEntry.Limit(kind, resetsAt)
    }
}

/** Traduce los errores premium de la API a la pantalla de ParcheU+ que corresponde. */
object PlusErrorRouter {
    /**
     * `PLUS_REQUIRED` abre G1; `QUOTA_EXCEEDED(kind)` abre la G2 de ese cupo (o G1 si el servidor no
     * dijo cuál). Cualquier otro error no es premium y devuelve `null`.
     */
    fun route(error: AppError, source: String): AppRoute? = when (error) {
        AppError.PlusRequired -> Paywall(source)
        is AppError.QuotaExceeded ->
            Paywall(error.kind?.let { PlusSources.limit(it, error.resetsAt) } ?: source)
        else -> null
    }

    fun isPremium(error: AppError): Boolean = route(error, "") != null
}
