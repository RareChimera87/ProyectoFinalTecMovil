package me.santiagoreina.parcheu.api.ai

import kotlinx.serialization.json.JsonObject
import me.santiagoreina.parcheu.api.platform.BogotaZone
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

/** Nivel de modelo (arquitectura.md §7.3). */
enum class ModelTier { CHAT, JUDGE, LITE }

/**
 * Propósito de una llamada: decide el nivel de modelo y queda en `ai_usage.purpose`. Las
 * features que necesiten uno nuevo lo agregan aquí.
 */
enum class AiPurpose(val id: String, val tier: ModelTier) {
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
    /** Moderación de contenido (E8.6). */
    MODERATION("moderation", ModelTier.LITE),
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
 * 2. consulta [DailyBudgetGuard] (puede degradar a lite o lanzar `AiBudgetExceeded` sin llamar);
 * 3. llama al [LlmClient] del proveedor configurado;
 * 4. registra la llamada en `ai_usage` y `ai_usage_daily` con el costo de la tabla de precios.
 *
 * Si el modelo devuelve JSON inválido se registra igual el uso (se cobró) y se relanza
 * [LlmInvalidOutput] para que la feature reintente.
 */
class AiGateway(
    private val client: LlmClient,
    private val config: AiConfig,
    private val usage: AiUsageStore,
    private val clock: Clock,
    val guard: DailyBudgetGuard = DailyBudgetGuard(usage, config.dailyBudgetUsd, clock),
) {
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

    suspend fun text(call: AiCall): LlmTextResult {
        val decision = guard.check(modelFor(call.purpose.tier), config.models.lite)
        val result = recordingFailures(call, decision) { client.generateText(call.toRequest(decision.model)) }
        record(call, decision, result.usage)
        return result
    }

    suspend fun json(call: AiCall, schema: JsonObject): LlmJsonResult {
        val decision = guard.check(modelFor(call.purpose.tier), config.models.lite)
        val result = recordingFailures(call, decision) { client.generateJson(call.toRequest(decision.model), schema) }
        record(call, decision, result.usage)
        return result
    }

    private suspend fun <T> recordingFailures(call: AiCall, decision: DailyBudgetGuard.Decision, block: suspend () -> T): T =
        try {
            block()
        } catch (e: LlmInvalidOutput) {
            e.usage?.let { record(call, decision, it) }
            throw e
        }

    private suspend fun record(call: AiCall, decision: DailyBudgetGuard.Decision, tokens: LlmUsage) {
        usage.record(
            AiUsageEntry(
                day = decision.day,
                purpose = call.purpose,
                provider = client.provider,
                model = decision.model,
                userId = call.userId,
                tokensIn = tokens.tokensIn,
                tokensOut = tokens.tokensOut,
                costMicros = config.prices.costMicros(decision.model, decision.day, tokens),
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
