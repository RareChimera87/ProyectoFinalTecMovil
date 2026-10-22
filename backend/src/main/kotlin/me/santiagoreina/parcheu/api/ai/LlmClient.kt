package me.santiagoreina.parcheu.api.ai

import kotlinx.serialization.json.JsonObject

/** Proveedores de LLM (ADR-0008). `FAKE` solo se admite con `ENV=local` y en los tests. */
enum class AiProvider(val id: String) {
    GEMINI("gemini"),
    CLAUDE("claude"),
    FAKE("fake"),
    ;

    companion object {
        fun parse(raw: String): AiProvider? = entries.firstOrNull { it.id == raw.trim().lowercase() }
    }
}

/** Una petición a un modelo. [model] ya viene resuelto por [AiGateway] (nunca lo eligen las features). */
data class LlmRequest(
    val model: String,
    val prompt: String,
    val system: String? = null,
    /** Techo de tokens de salida. En Gemini 3.x el razonamiento cuenta dentro de este techo. */
    val maxOutputTokens: Int = 1024,
    val temperature: Double? = null,
)

/** Tokens que el proveedor cobra. [tokensOut] incluye los de razonamiento. */
data class LlmUsage(val tokensIn: Int, val tokensOut: Int)

data class LlmTextResult(val text: String, val model: String, val usage: LlmUsage)

data class LlmJsonResult(val json: JsonObject, val model: String, val usage: LlmUsage)

/** El modelo respondió algo que no es JSON válido o no cumple el esquema pedido. */
class LlmInvalidOutput(message: String, val usage: LlmUsage? = null) : RuntimeException(message)

/**
 * Cliente de un proveedor de LLM: texto libre y JSON restringido por un JSON Schema.
 * Las implementaciones no registran uso ni aplican presupuesto: eso lo hace [AiGateway].
 */
interface LlmClient {
    val provider: AiProvider

    suspend fun generateText(request: LlmRequest): LlmTextResult

    /**
     * Pide salida JSON que cumpla [schema] (JSON Schema con `type`, `properties`, `required`,
     * `items`). La respuesta se valida con [JsonSchemaCheck]; si no cumple lanza [LlmInvalidOutput].
     */
    suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult
}
