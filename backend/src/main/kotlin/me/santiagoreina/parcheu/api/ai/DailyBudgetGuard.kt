package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Instant

/**
 * Presupuesto diario escalonado (ADR-0010, arquitectura.md §7.3) sobre el gasto de
 * `ai_usage_daily` del día en Bogotá:
 *
 * - gasto < 70 % → [Level.NORMAL]: se usa el modelo pedido.
 * - 70 % ≤ gasto < 100 % → [Level.DEGRADED]: todos los propósitos pasan al modelo lite.
 * - gasto ≥ 100 % → [AiBudgetExceeded] (503 `AI_BUDGET`, `retryAt` = medianoche de Bogotá)
 *   **antes** de llamar al proveedor.
 */
class DailyBudgetGuard(
    private val usage: AiUsageStore,
    dailyBudgetUsd: BigDecimal,
    private val clock: Clock,
) {
    enum class Level { NORMAL, DEGRADED }

    data class Decision(val level: Level, val model: String, val spentMicros: Long, val day: LocalDate)

    /** Presupuesto en micro-USD. */
    val budgetMicros: Long = dailyBudgetUsd.movePointRight(6).toLong()

    init {
        require(budgetMicros > 0) { "El presupuesto diario de IA debe ser mayor que 0" }
    }

    fun today(): LocalDate = LocalDate.now(clock.withZone(BogotaZone))

    /** Decide qué modelo usar o lanza [AiBudgetExceeded] si el día ya se gastó. */
    suspend fun check(requestedModel: String, liteModel: String): Decision {
        val day = today()
        val spent = usage.spentMicros(day)
        // Comparación entera: spent/budget ≥ 1 y spent/budget ≥ 0,70.
        if (spent >= budgetMicros) throw AiBudgetExceeded(retryAt = nextMidnight(day))
        return if (spent * 100 >= budgetMicros * 70) {
            Decision(Level.DEGRADED, liteModel, spent, day)
        } else {
            Decision(Level.NORMAL, requestedModel, spent, day)
        }
    }

    private fun nextMidnight(day: LocalDate): Instant =
        Instant.fromEpochSeconds(day.plusDays(1).atStartOfDay(BogotaZone).toEpochSecond())
}
