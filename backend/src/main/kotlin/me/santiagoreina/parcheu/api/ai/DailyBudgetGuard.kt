package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.UserAiBudgetExceeded
import java.math.BigDecimal
import java.time.Clock
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/**
 * Presupuesto diario escalonado (ADR-0010, arquitectura.md §7.3) sobre el gasto del día en Bogotá:
 *
 * - gasto < 70 % → [Level.NORMAL]: se usa el modelo pedido.
 * - 70 % ≤ gasto < 100 % → [Level.DEGRADED]: todos los propósitos pasan al modelo lite.
 * - gasto ≥ 100 %, o la llamada no cabe en lo que queda → [AiBudgetExceeded] (503 `AI_BUDGET`,
 *   `retryAt` = medianoche de Bogotá) **antes** de llamar al proveedor.
 *
 * Además cada persona tiene su techo diario ([userDailyBudgetUsd], B-M1): al pasarlo, sus llamadas lanzan
 * [UserAiBudgetExceeded] (el mismo 503) y las de los demás siguen. [reserve] cuenta las llamadas en curso
 * (reserva previa con el costo estimado), así que la concurrencia no pasa ninguno de los dos topes.
 */
class DailyBudgetGuard(
    private val usage: AiUsageStore,
    dailyBudgetUsd: BigDecimal,
    private val clock: Clock,
    userDailyBudgetUsd: BigDecimal? = null,
) {
    private val log = LoggerFactory.getLogger(DailyBudgetGuard::class.java)

    enum class Level { NORMAL, DEGRADED }

    data class Decision(val level: Level, val model: String, val spentMicros: Long, val day: LocalDate)

    /** Lo que [reserve] decidió y lo que dejó reservado; se cierra con [AiUsageStore.settle]. */
    data class Reservation(val decision: Decision, val hold: BudgetHold)

    /** Presupuesto en micro-USD. */
    val budgetMicros: Long = dailyBudgetUsd.movePointRight(6).toLong()

    /** Techo diario por persona en micro-USD; null = sin techo personal. */
    val userBudgetMicros: Long? = userDailyBudgetUsd?.movePointRight(6)?.toLong()

    init {
        require(budgetMicros > 0) { "El presupuesto diario de IA debe ser mayor que 0" }
        require(userBudgetMicros == null || userBudgetMicros > 0) { "El techo diario de IA por persona debe ser mayor que 0" }
    }

    fun today(): LocalDate = LocalDate.now(clock.withZone(BogotaZone))

    /** Decide qué modelo usar o lanza [AiBudgetExceeded] si el día ya se gastó. Solo consulta: no reserva. */
    suspend fun check(requestedModel: String, liteModel: String): Decision {
        val day = today()
        val spent = usage.spentMicros(day)
        if (spent >= budgetMicros) budgetExceeded(spent, day)
        return Decision(levelFor(spent), modelFor(spent, requestedModel, liteModel), spent, day)
    }

    /**
     * Decide el modelo y reserva su costo estimado ([estimate] del modelo elegido) en el gasto del día y en el
     * de [userId], con las filas bloqueadas. Lanza [AiBudgetExceeded] si el total ya llegó al tope o la llamada
     * no cabe, y [UserAiBudgetExceeded] si lo mismo pasa con el techo de la persona; en ambos casos sin reservar.
     */
    suspend fun reserve(requestedModel: String, liteModel: String, userId: UUID?, estimate: (model: String, day: LocalDate) -> Long): Reservation {
        val day = today()
        var decision: Decision? = null
        val hold = usage.hold(day, userId) { snapshot ->
            val spent = snapshot.globalMicros
            val model = modelFor(spent, requestedModel, liteModel)
            val micros = estimate(model, day)
            if (spent >= budgetMicros || spent + micros > budgetMicros) budgetExceeded(spent, day)
            val userSpent = snapshot.userMicros
            val userBudget = userBudgetMicros
            if (userSpent != null && userBudget != null && (userSpent >= userBudget || userSpent + micros > userBudget)) {
                // Sin el id de la persona en el log (arquitectura.md §11): basta el evento para la métrica.
                log.atWarn().setMessage(USER_BUDGET_EVENT).addKeyValue("event", USER_BUDGET_EVENT)
                    .addKeyValue("spentMicros", userSpent).addKeyValue("budgetMicros", userBudget).log()
                throw UserAiBudgetExceeded(retryAt = nextMidnight(day))
            }
            decision = Decision(levelFor(spent), model, spent, day)
            micros
        }
        return Reservation(checkNotNull(decision), hold)
    }

    private fun levelFor(spent: Long): Level =
        // Comparación entera: spent/budget ≥ 0,70.
        if (spent * 100 >= budgetMicros * 70) Level.DEGRADED else Level.NORMAL

    private fun modelFor(spent: Long, requestedModel: String, liteModel: String): String =
        if (levelFor(spent) == Level.DEGRADED) liteModel else requestedModel

    private fun budgetExceeded(spent: Long, day: LocalDate): Nothing {
        // Evento para la métrica y la alerta de E10.5 (sin datos personales).
        log.atWarn().setMessage("AiBudgetExceeded").addKeyValue("event", "AiBudgetExceeded")
            .addKeyValue("spentMicros", spent).addKeyValue("budgetMicros", budgetMicros).log()
        throw AiBudgetExceeded(retryAt = nextMidnight(day))
    }

    private fun nextMidnight(day: LocalDate): Instant =
        Instant.fromEpochSeconds(day.plusDays(1).atStartOfDay(BogotaZone).toEpochSecond())

    companion object {
        /** Evento de log cuando una persona llega a su techo diario (B-M1). */
        const val USER_BUDGET_EVENT = "AiUserBudgetExceeded"
    }
}
