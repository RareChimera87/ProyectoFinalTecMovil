package me.santiagoreina.parcheu.api.ai

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import me.santiagoreina.parcheu.api.platform.BogotaZone
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

/** Nivel de modelo (arquitectura.md §7.3). */
enum class ModelTier { CHAT, JUDGE, LITE }

/**
 * Propósito de una llamada: decide el nivel de modelo y queda en `ai_usage.purpose`. Las
 * features que necesiten uno nuevo lo agregan aquí. [chargesUserBudget] = false: la llamada queda en
 * `ai_usage.user_id` de la persona pero no cuenta para su techo diario general (B-M1), solo para el total.
 */
enum class AiPurpose(val id: String, val tier: ModelTier, val chargesUserBudget: Boolean = true) {
    /** Turno de un `TwinAgent` en la simulación (E4.3). */
    SIMULATION_TURN("simulation_turn", ModelTier.CHAT),
    /** Juez de un escenario de simulación (E4.3). */
    SIMULATION_JUDGE("simulation_judge", ModelTier.JUDGE),
    /** Turno del gemelo en el modo escenario (F3). */
    SCENARIO_TURN("scenario_turn", ModelTier.CHAT),
    /** Resumen de persona del gemelo (E2.x, cola ai-light). */
    PERSONA("persona", ModelTier.LITE),
    /** Extracción de memorias candidatas (E9.2). */
    MEMORY_EXTRACTION("memory_extraction", ModelTier.LITE),
    /**
     * Moderación de contenido (E8.6). Fuera del techo personal: si contara, quien gasta su techo con simulaciones
     * escribiría sin moderación (ni ayuda ante autolesión) hasta medianoche. Su costo por persona lo acotan el tope de
     * mensajes moderados (`MODERATION_DAILY_PER_USER`) y el sub-techo `AI_USER_DAILY_MODERATION_USD`, que pasan al
     * muestreo, y el corte duro de la cola muestreada (`MODERATION_SAMPLED_DAILY_PER_USER`), después del cual no se
     * modera más ese día (ADR-0026). El sub-techo es suave: mira el gasto registrado, no las tareas en vuelo.
     */
    MODERATION("moderation", ModelTier.LITE, chargesUserBudget = false),
    /** Nombres de comunidades (E6.4). */
    COMMUNITY_LABEL("community_label", ModelTier.LITE),
    /** Prueba de humo manual contra Vertex. */
    SMOKE_TEST("smoke_test", ModelTier.LITE),
}

/** Lo que una feature pide; el modelo lo resuelve la pasarela. */
data class AiCall(
    val purpose: AiPurpose,
    val prompt: String,
    val system: String? = null,
    val userId: UUID? = null,
    val maxOutputTokens: Int = 1024,
    val temperature: Double? = null,
)

/**
 * Único punto de entrada a la IA para las features (ADR-0008, ADR-0010):
 * 1. elige el modelo del nivel del propósito;
 * 2. reserva en [DailyBudgetGuard] el costo estimado de la llamada en el gasto del día y en el de
 *    [AiCall.userId] (puede degradar a lite o lanzar `AiBudgetExceeded`/`UserAiBudgetExceeded` sin llamar);
 * 3. llama al [LlmClient] del proveedor configurado;
 * 4. registra la llamada en `ai_usage` y `ai_usage_daily` con el costo de la tabla de precios y cambia la
 *    reserva por ese costo real.
 *
 * Si el modelo devuelve JSON inválido se registra igual el uso (se cobró) y se relanza
 * [LlmInvalidOutput] para que la feature reintente. Cualquier otro fallo (timeout, 5xx, cancelación) se
 * registra con un costo estimado (los tokens de entrada, sin salida), porque el proveedor pudo cobrarlo
 * sin devolver el uso (B-M1).
 */
class AiGateway(
    private val client: LlmClient,
    private val config: AiConfig,
    private val usage: AiUsageStore,
    private val clock: Clock,
    val guard: DailyBudgetGuard = DailyBudgetGuard(usage, config.dailyBudgetUsd, clock, config.userDailyBudgetUsd),
) {
    private val log = LoggerFactory.getLogger(AiGateway::class.java)

    val provider: AiProvider get() = client.provider

    fun modelFor(tier: ModelTier): String = when (tier) {
        ModelTier.CHAT -> config.models.chat
        ModelTier.JUDGE -> config.models.judge
        ModelTier.LITE -> config.models.lite
    }

    /**
     * Costo en micro-USD de [tokens] con [model] según la tabla de precios de hoy. Lo usa la
     * simulación (E4.3) para acumular `simulations.cost_micros`; el registro oficial sigue en `ai_usage`.
     */
    fun costMicros(model: String, tokens: LlmUsage): Long = config.prices.costMicros(model, guard.today(), tokens)

    /** Lanza `AiBudgetExceeded` si hoy ya no queda presupuesto; no llama al proveedor (E4.3). */
    suspend fun ensureBudget() {
        guard.check(config.models.chat, config.models.lite)
    }

    suspend fun text(call: AiCall): LlmTextResult = metered(call, { it.usage }) { model -> client.generateText(call.toRequest(model)) }

    suspend fun json(call: AiCall, schema: JsonObject): LlmJsonResult =
        metered(call, { it.usage }) { model -> client.generateJson(call.toRequest(model), schema) }

    private suspend fun <T> metered(call: AiCall, usageOf: (T) -> LlmUsage, block: suspend (model: String) -> T): T {
        val budgetHolder = call.userId?.takeIf { call.purpose.chargesUserBudget }
        val reservation = guard.reserve(modelFor(call.purpose.tier), config.models.lite, budgetHolder) { model, day ->
            config.prices.costMicros(model, day, call.ceilingUsage())
        }
        val result = try {
            block(reservation.decision.model)
        } catch (e: Throwable) {
            val reported = (e as? LlmInvalidOutput)?.usage
            // Se cierra la reserva aunque la corrutina esté cancelada: si no, el gasto quedaría inflado hasta medianoche.
            // Un fallo al cerrarla no tapa el error original.
            try {
                withContext(NonCancellable) { settle(call, reservation, reported ?: call.failedUsage(), estimated = reported == null) }
            } catch (settleError: Throwable) {
                e.addSuppressed(settleError)
            }
            throw e
        }
        // También sin cancelación en el camino exitoso: el proveedor ya cobró y la fila de `ai_usage` no se puede perder.
        withContext(NonCancellable) { settle(call, reservation, usageOf(result), estimated = false) }
        return result
    }

    /**
     * ¿Le queda a [userId] sub-techo diario de moderación (`AI_USER_DAILY_MODERATION_USD`)? Consulta sin reservar, así
     * que es un tope suave: una ráfaga de mensajes simultáneos lo puede pasar por lo que haya en vuelo (acotado por el
     * límite por minuto). Al pasarlo la moderación de esa persona pasa al muestreo en vez de apagarse (ADR-0026).
     */
    suspend fun moderationBudgetLeft(userId: UUID): Boolean =
        usage.userSpentMicros(guard.today(), userId, AiPurpose.MODERATION) < config.userDailyModerationUsd.movePointRight(6).toLong()

    private suspend fun settle(call: AiCall, reservation: DailyBudgetGuard.Reservation, tokens: LlmUsage, estimated: Boolean) {
        val decision = reservation.decision
        val cost = config.prices.costMicros(decision.model, decision.day, tokens)
        // Evento para la métrica de costo de IA por día (E10.5): solo propósito, modelo y costo.
        log.atInfo().setMessage("ai_call").addKeyValue("event", "ai_call")
            .addKeyValue("purpose", call.purpose.toString()).addKeyValue("model", decision.model)
            .addKeyValue("costMicros", cost).addKeyValue("estimated", estimated).log()
        usage.settle(
            reservation.hold,
            AiUsageEntry(
                day = decision.day,
                purpose = call.purpose,
                provider = client.provider,
                model = decision.model,
                userId = call.userId,
                tokensIn = tokens.tokensIn,
                tokensOut = tokens.tokensOut,
                costMicros = cost,
                createdAt = OffsetDateTime.now(clock.withZone(BogotaZone)),
            ),
        )
    }

    private fun AiCall.toRequest(model: String) = LlmRequest(
        model = model,
        prompt = prompt,
        system = system,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
    )
}

/**
 * Tokens de entrada estimados de [AiCall] sin preguntar al proveedor: un token por cada 3 caracteres, por
 * encima de los ~4 por token del español, para que la reserva no se quede corta.
 */
internal fun AiCall.estimatedTokensIn(): Int = ((system?.length ?: 0) + prompt.length + 2) / 3

/** Uso máximo que puede cobrar la llamada (reserva previa): entrada estimada y todo el techo de salida. */
internal fun AiCall.ceilingUsage(): LlmUsage = LlmUsage(estimatedTokensIn(), maxOutputTokens)

/** Uso que se registra cuando la llamada falla sin que el proveedor diga cuánto cobró: solo la entrada estimada. */
internal fun AiCall.failedUsage(): LlmUsage = LlmUsage(estimatedTokensIn(), 0)

