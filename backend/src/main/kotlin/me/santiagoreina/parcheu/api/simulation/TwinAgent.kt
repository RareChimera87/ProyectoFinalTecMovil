package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.api.ai.AiCall
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiPurpose
import me.santiagoreina.parcheu.api.ai.LlmInvalidOutput
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID

/** Respuesta de un agente en un turno. */
data class AgentReply(val text: String, val ended: Boolean, val usage: UsageTotals)

/**
 * Un gemelo que conversa (ADR-0009). Cada instancia conoce **solo** su persona y sus memorias; de
 * la otra persona solo ve lo que dijo en la transcripción del escenario actual. El prompt lo arma
 * [TwinPromptBuilder].
 */
class TwinAgent(
    private val ai: AiGateway,
    val side: Side,
    private val userId: UUID,
    private val persona: String,
    private val memories: List<TwinMemory>,
    private val mode: Mode,
) {
    suspend fun reply(scenario: ScenarioDef, turns: List<StoredTurn>): AgentReply {
        val prompt = TwinPromptBuilder.build(
            AgentPromptInput(
                persona = persona,
                memories = memories,
                mode = mode,
                scenario = scenario,
                transcript = turns.map { AgentLine(mine = it.speaker == side, text = it.text) },
                turnNumber = turns.size + 1,
            ),
        )
        val result = ai.text(
            AiCall(
                purpose = AiPurpose.SIMULATION_TURN,
                prompt = prompt.prompt,
                system = prompt.system,
                userId = userId,
                maxOutputTokens = TURN_OUTPUT_CEILING,
                temperature = 0.9,
            ),
        )
        val ended = END_MARK in result.text
        val text = clipTurn(result.text.replace(END_MARK, " "))
        if (text.isBlank()) throw LlmInvalidOutput("El agente devolvió un turno vacío", result.usage)
        val usage = UsageTotals(result.usage.tokensIn, result.usage.tokensOut, ai.costMicros(result.model, result.usage), result.model)
        return AgentReply(text, ended && turns.size + 1 >= MIN_TURN_TO_END, usage)
    }

    companion object {
        /**
         * Techo de `maxOutputTokens` de un turno. En Gemini 3.x el razonamiento cuenta dentro de este
         * techo, así que se deja holgura; el límite de [TURN_MAX_TOKENS] tokens visibles lo aplica [clipTurn].
         */
        const val TURN_OUTPUT_CEILING = 768

        /** ~4 caracteres por token (la misma estimación que `FakeLlmClient`). */
        const val TURN_MAX_CHARS = TURN_MAX_TOKENS * 4

        /**
         * Deja el turno en una sola línea de como máximo [TURN_MAX_CHARS] caracteres, cortando en la
         * última oración completa (o palabra) para no superar [TURN_MAX_TOKENS] tokens.
         */
        fun clipTurn(raw: String): String {
            val text = raw.replace(Regex("\\s+"), " ").trim().removeSurrounding("\"").trim()
            if (text.length <= TURN_MAX_CHARS) return text
            val head = text.substring(0, TURN_MAX_CHARS)
            val sentenceEnd = head.indexOfLast { it == '.' || it == '!' || it == '?' }
            if (sentenceEnd >= TURN_MAX_CHARS / 2) return head.substring(0, sentenceEnd + 1)
            val space = head.lastIndexOf(' ')
            return (if (space > 0) head.substring(0, space) else head.dropLast(1)).trimEnd() + "…"
        }
    }
}
