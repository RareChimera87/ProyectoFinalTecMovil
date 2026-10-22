package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.Usage
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID
import kotlin.time.Instant as KInstant

/**
 * Cupos de simulaciones, matches al azar e informes completos (backlog E4.1, ADR-0010).
 *
 * Uso desde otras features: [consume] justo cuando el servidor acepta la acción (con `ref_id` =
 * id de la simulación, del match por usuario o del informe) y [refund] si luego falla sin culpa
 * del usuario (p. ej. `AI_BUDGET`). [ensureAvailable] solo consulta, sin cobrar.
 */
class QuotaService(
    private val usage: UsageRepository,
    private val entitlements: EntitlementService,
    private val limits: QuotaLimits,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(QuotaService::class.java)

    /** Resultado de [consume]. */
    enum class Outcome {
        /** Se cobró un uso nuevo. */
        CHARGED,

        /** Ese `ref_id` ya estaba registrado (repetición o reintento): no se cobra de nuevo. */
        ALREADY_CHARGED,
    }

    /**
     * Cobra un uso de [kind]. Idempotente por `(kind, refId)`: repetir el mismo `refId` no cobra
     * dos veces, aunque ese uso se haya devuelto. Lanza [QuotaExceeded] (402, `resetsAt` = fin de
     * la ventana) si no queda cupo y [Conflict] si el `refId` ya lo usó otra persona.
     */
    fun consume(userId: UUID, kind: QuotaKind, refId: String): Outcome {
        if (refId.isBlank()) throw ValidationFailed("ref_id vacío")
        val now = clock.instant()
        val rule = limits.ruleFor(kind, entitlements.isPlus(userId, now))
        val window = windowFor(rule.period, now)
        return when (val result = usage.charge(userId, kind, refId, window, rule.limit, now)) {
            ChargeResult.Charged -> Outcome.CHARGED
            is ChargeResult.AlreadyCharged -> {
                if (result.ownerId != userId) throw Conflict("El ref_id $refId ya está registrado para otra persona")
                Outcome.ALREADY_CHARGED
            }
            ChargeResult.LimitReached -> {
                // Métrica basada en logs: rechazos de cuota (arquitectura.md §12).
                log.info("quota_rejected kind={} period={}", kind, rule.period)
                throw QuotaExceeded(window.end.toKotlin(), kind)
            }
        }
    }

    /** Lanza [QuotaExceeded] si hoy no se podría cobrar un uso de [kind]; no registra nada. */
    fun ensureAvailable(userId: UUID, kind: QuotaKind) {
        val now = clock.instant()
        val rule = limits.ruleFor(kind, entitlements.isPlus(userId, now))
        val window = windowFor(rule.period, now)
        if (usage.countActive(userId, kind, window) >= rule.limit) throw QuotaExceeded(window.end.toKotlin(), kind)
    }

    /** Devuelve el uso: deja de contar para el cupo. Idempotente; false si el `refId` no existe. */
    fun refund(kind: QuotaKind, refId: String): Boolean = usage.refund(kind, refId)

    /**
     * Estado para `GET /v1/quota` e Inicio. Gratis: usados y límite de su ventana. Plus: `limit`
     * nulo (sin límite visible) y `used` del día; el uso justo no se muestra.
     */
    fun status(userId: UUID): QuotaDto {
        val now = clock.instant()
        val plus = entitlements.isPlus(userId, now)
        fun usageOf(kind: QuotaKind): Usage {
            val rule = limits.ruleFor(kind, plus)
            val window = windowFor(rule.period, now)
            return Usage(
                used = usage.countActive(userId, kind, window),
                limit = if (plus) null else rule.limit,
                resetsAt = window.end.toKotlin(),
            )
        }
        return QuotaDto(
            simulations = usageOf(QuotaKind.SIMULATION),
            randomMatch = usageOf(QuotaKind.RANDOM_MATCH),
            fullReport = usageOf(QuotaKind.FULL_REPORT),
            plus = plus,
        )
    }

    fun isPlus(userId: UUID): Boolean = entitlements.isPlus(userId, clock.instant())
}

internal fun java.time.Instant.toKotlin(): KInstant = KInstant.fromEpochSeconds(epochSecond, nano)
