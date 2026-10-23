package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.contract.QuotaKind

/**
 * Límites de cupo (backlog E4.1, ADR-0010).
 *
 * - **Gratis:** 5 simulaciones por semana ISO (corte el lunes 00:00 hora Bogotá), 1 match al
 *   azar por día y 1 informe completo por mes calendario.
 * - **ParcheU+:** sin límite semanal ni mensual, pero con **uso justo diario** por tipo (techo de
 *   costo de IA sin App Check, ADR-0015). Se configura con variables de entorno:
 *   `QUOTA_PLUS_DAILY_SIMULATIONS` (20), `QUOTA_PLUS_DAILY_RANDOM_MATCHES` (10) y
 *   `QUOTA_PLUS_DAILY_FULL_REPORTS` (20).
 */
data class QuotaLimits(
    val freeWeeklySimulations: Int = 5,
    val freeDailyRandomMatches: Int = 1,
    val freeMonthlyFullReports: Int = 1,
    val plusDailySimulations: Int = 20,
    val plusDailyRandomMatches: Int = 10,
    val plusDailyFullReports: Int = 20,
) {
    init {
        listOf(
            freeWeeklySimulations, freeDailyRandomMatches, freeMonthlyFullReports,
            plusDailySimulations, plusDailyRandomMatches, plusDailyFullReports,
        ).forEach { require(it >= 0) { "Los límites de cupo no pueden ser negativos" } }
    }

    /** Regla que aplica a [kind] según el plan del usuario. */
    fun ruleFor(kind: QuotaKind, plus: Boolean): QuotaRule = if (plus) {
        when (kind) {
            QuotaKind.SIMULATION -> QuotaRule(QuotaPeriod.DAY, plusDailySimulations)
            QuotaKind.RANDOM_MATCH -> QuotaRule(QuotaPeriod.DAY, plusDailyRandomMatches)
            QuotaKind.FULL_REPORT -> QuotaRule(QuotaPeriod.DAY, plusDailyFullReports)
        }
    } else {
        when (kind) {
            QuotaKind.SIMULATION -> QuotaRule(QuotaPeriod.ISO_WEEK, freeWeeklySimulations)
            QuotaKind.RANDOM_MATCH -> QuotaRule(QuotaPeriod.DAY, freeDailyRandomMatches)
            QuotaKind.FULL_REPORT -> QuotaRule(QuotaPeriod.MONTH, freeMonthlyFullReports)
        }
    }

    companion object {
        /** Lee el uso justo de Plus del entorno; los límites gratis son fijos (los promete la UI). */
        fun fromEnv(vars: Map<String, String> = System.getenv()): QuotaLimits {
            val defaults = QuotaLimits()
            fun int(name: String, default: Int): Int {
                val raw = vars[name]?.takeIf { it.isNotBlank() } ?: return default
                return raw.trim().toIntOrNull()?.takeIf { it >= 0 }
                    ?: error("$name inválido: '$raw' (se espera un entero >= 0)")
            }
            return defaults.copy(
                plusDailySimulations = int("QUOTA_PLUS_DAILY_SIMULATIONS", defaults.plusDailySimulations),
                plusDailyRandomMatches = int("QUOTA_PLUS_DAILY_RANDOM_MATCHES", defaults.plusDailyRandomMatches),
                plusDailyFullReports = int("QUOTA_PLUS_DAILY_FULL_REPORTS", defaults.plusDailyFullReports),
            )
        }
    }
}

/** Un límite: como mucho [limit] usos no devueltos dentro de la ventana [period] en curso. */
data class QuotaRule(val period: QuotaPeriod, val limit: Int)
