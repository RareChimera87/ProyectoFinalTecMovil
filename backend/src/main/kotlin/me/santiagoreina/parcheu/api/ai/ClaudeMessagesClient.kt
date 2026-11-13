package me.santiagoreina.parcheu.api.ai

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Claude con el SDK Java de Anthropic (Messages API), común a los dos backends del SDK: Vertex
 * ([ClaudeVertexClient], `AI_PROVIDER=claude`) y la API directa con clave ([ClaudeApiClient],
 * `AI_PROVIDER=anthropic`). Solo cambia cómo se construye el [AnthropicClientAsync]; el mapeo de
 * mensajes, `system`, techo de tokens, salida JSON y conteo de uso es el mismo.
 *
 * Esfuerzo `LOW`: los turnos son cortos y el costo manda (ADR-0010). La salida JSON usa
 * salidas estructuradas (`output_config.format`) y además se valida con [JsonSchemaCheck].
 * Timeouts y reintentos los aplica el SDK ([TIMEOUT], [MAX_RETRIES]): reintenta 408, 409, 429 y
 * 5xx con espera exponencial y respeta `retry-after`; si se agotan, la excepción llega a
 * [AiGateway], que registra el costo estimado.
 *
 * El cliente se crea en la primera llamada ([factory]): las credenciales (ADC o la clave del
 * secreto) no se resuelven al arrancar.
 */
abstract class ClaudeMessagesClient(factory: () -> AnthropicClientAsync) : LlmClient, AutoCloseable {
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, factory)
    private val client: AnthropicClientAsync by clientDelegate

    private val log = LoggerFactory.getLogger(ClaudeMessagesClient::class.java)

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        val message = client.messages().create(params(request, OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())).await()
        val usage = usageOf(message, request)
        val stop = stopOf(message, usage)
        val text = textOf(message)
        val truncated = stop == StopReason.MAX_TOKENS
        if (truncated && text.isBlank()) {
            throw LlmInvalidOutput("Claude agotó maxOutputTokens sin producir texto (max_tokens)", usage)
        }
        return LlmTextResult(text, request.model, usage, truncated = truncated)
    }

    override suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult {
        val format = JsonOutputFormat.builder()
            .schema(
                JsonOutputFormat.Schema.builder()
                    .additionalProperties(closeObjects(schema).toJavaMap().mapValues { JsonValue.from(it.value) })
                    .build(),
            )
            .build()
        val output = OutputConfig.builder().effort(OutputConfig.Effort.LOW).format(format).build()
        val message = client.messages().create(params(request, output)).await()
        val usage = usageOf(message, request)
        stopOf(message, usage)
        val json = JsonSchemaCheck.parseAndValidate(textOf(message), schema, usage)
        return LlmJsonResult(json, request.model, usage)
    }

    private fun params(request: LlmRequest, output: OutputConfig): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(request.model)
            .maxTokens(request.maxOutputTokens.toLong())
            .outputConfig(output)
            .addUserMessage(request.prompt)
        request.system?.let { builder.system(it) }
        return builder.build()
    }

    /** Lanza [LlmInvalidOutput] (con el uso cobrado) si Claude rechazó la petición. */
    private fun stopOf(message: Message, usage: LlmUsage): StopReason? {
        val stop = message.stopReason().orElse(null)
        if (stop == StopReason.REFUSAL) throw LlmInvalidOutput("Claude rechazó la petición (refusal)", usage)
        return stop
    }

    /**
     * Las salidas estructuradas de Claude exigen `additionalProperties: false` en cada objeto. Además se quitan las
     * restricciones numéricas y de longitud ([UNSUPPORTED_KEYWORDS]), que las salidas estructuradas de Claude no
     * admiten (precaución; los topes se siguen validando aquí con [JsonSchemaCheck] sobre el esquema original).
     */
    private fun closeObjects(schema: JsonObject): JsonObject = JsonObject(
        schema.filterKeys { it !in UNSUPPORTED_KEYWORDS }.mapValues { (key, value) ->
            when {
                key == "properties" && value is JsonObject ->
                    JsonObject(value.mapValues { (_, sub) -> if (sub is JsonObject) closeObjects(sub) else sub })
                key == "items" && value is JsonObject -> closeObjects(value)
                else -> value
            }
        }.let { if (it["type"] == JsonPrimitive("object") && "additionalProperties" !in it) it + ("additionalProperties" to JsonPrimitive(false)) else it },
    )

    private fun textOf(message: Message): String =
        message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")

    /**
     * Tokens cobrados. Si la respuesta no trae `usage` (no debería pasar), se registra el peor caso: la
     * entrada estimada y todo el techo de salida, para que [DailyBudgetGuard] no subestime el gasto.
     */
    private fun usageOf(message: Message, request: LlmRequest): LlmUsage {
        val u = message._usage().asKnown().orElse(null)
        val tokensIn = u?._inputTokens()?.asKnown()?.orElse(null)
        val tokensOut = u?._outputTokens()?.asKnown()?.orElse(null)
        if (tokensIn == null || tokensOut == null) {
            log.atWarn().setMessage("Claude respondió sin usage; se registra el peor caso")
                .addKeyValue("event", "ai_usage_missing").addKeyValue("model", request.model).log()
            val estimatedIn = ((request.system?.length ?: 0) + request.prompt.length + 2) / 3
            return LlmUsage(tokensIn?.toInt() ?: estimatedIn, tokensOut?.toInt() ?: request.maxOutputTokens)
        }
        return LlmUsage(tokensIn.toInt(), tokensOut.toInt())
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    companion object {
        /** Igual que el cliente de Claude en Vertex desde E0.9. */
        val TIMEOUT: Duration = Duration.ofSeconds(120)
        const val MAX_RETRIES = 2

        private val UNSUPPORTED_KEYWORDS = setOf(
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf", "minLength", "maxLength",
        )
    }
}
