package me.santiagoreina.parcheu.api.ai

import com.google.genai.Client
import com.google.genai.types.BlockedReason
import com.google.genai.types.ClientOptions
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.FinishReason
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonObject

/**
 * Gemini en Vertex AI con el SDK Google Gen AI (`com.google.genai:google-genai`) en modo
 * Vertex. Autenticación por ADC (la cuenta de servicio de Cloud Run o `gcloud auth
 * application-default login` en local). La ubicación por defecto es la multirregión `us`.
 */
class GeminiVertexClient(
    project: String,
    location: String,
    private val thinkingLevel: String?,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxConnectionsPerHost: Int = DEFAULT_MAX_CONNECTIONS_PER_HOST,
) : LlmClient, AutoCloseable {
    override val provider = AiProvider.GEMINI

    // Perezoso: las credenciales (ADC) se resuelven en la primera llamada, no al arrancar.
    // Sin HttpOptions el SDK no tiene timeouts (0 = infinito) y OkHttp limita a 5 llamadas por host.
    private val clientDelegate = lazy {
        Client.builder().vertexAI(true).project(project).location(location)
            .httpOptions(httpOptions(timeoutMs))
            .clientOptions(clientOptions(maxConnectionsPerHost))
            .build()
    }
    private val client: Client by clientDelegate

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        val response = call(request, config(request).build())
        val usage = usageOf(response)
        val output = GeminiResponses.textOf(response, usage)
        if (output.truncated && output.text.isBlank()) {
            throw LlmInvalidOutput("Gemini agotó maxOutputTokens sin producir texto (MAX_TOKENS)", usage)
        }
        return LlmTextResult(output.text, request.model, usage, truncated = output.truncated)
    }

    override suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult {
        val config = config(request)
            .responseMimeType("application/json")
            .responseJsonSchema(schema.toJavaValue())
            .build()
        val response = call(request, config)
        val usage = usageOf(response)
        val json = JsonSchemaCheck.parseAndValidate(GeminiResponses.textOf(response, usage).text, schema, usage)
        return LlmJsonResult(json, request.model, usage)
    }

    private suspend fun call(request: LlmRequest, config: GenerateContentConfig): GenerateContentResponse =
        client.async.models.generateContent(request.model, request.prompt, config).await()

    private fun config(request: LlmRequest): GenerateContentConfig.Builder {
        val builder = GenerateContentConfig.builder().maxOutputTokens(request.maxOutputTokens)
        request.system?.let { builder.systemInstruction(Content.fromParts(Part.fromText(it))) }
        request.temperature?.let { builder.temperature(it.toFloat()) }
        thinkingLevel?.let { builder.thinkingConfig(ThinkingConfig.builder().thinkingLevel(it).build()) }
        return builder
    }

    /** Gemini cobra el razonamiento como salida: se suma a [LlmUsage.tokensOut]. */
    private fun usageOf(response: GenerateContentResponse): LlmUsage = GeminiResponses.usageOf(response)

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000
        const val DEFAULT_MAX_CONNECTIONS_PER_HOST = 32

        internal fun httpOptions(timeoutMs: Int): HttpOptions = HttpOptions.builder().timeout(timeoutMs).build()

        internal fun clientOptions(maxPerHost: Int): ClientOptions =
            ClientOptions.builder().maxConnections(maxPerHost).maxConnectionsPerHost(maxPerHost).build()
    }
}

/** Texto de una respuesta de Gemini; [truncated] si terminó por `MAX_TOKENS`. */
internal data class GeminiText(val text: String, val truncated: Boolean)

/**
 * Lectura de respuestas de Gemini sin `GenerateContentResponse.text()`, que lanza
 * `IllegalArgumentException` ante cualquier `finishReason` distinto de STOP/MAX_TOKENS y
 * perdería los tokens ya cobrados. Los bloqueos se convierten en [LlmInvalidOutput] con el uso.
 */
internal object GeminiResponses {
    fun usageOf(response: GenerateContentResponse): LlmUsage {
        val meta = response.usageMetadata().orElse(null) ?: return LlmUsage(0, 0)
        val input = meta.promptTokenCount().orElse(0)
        val output = meta.candidatesTokenCount().orElse(0) + meta.thoughtsTokenCount().orElse(0)
        return LlmUsage(input, output)
    }

    fun textOf(response: GenerateContentResponse, usage: LlmUsage): GeminiText {
        val blocked = response.promptFeedback().orElse(null)?.blockReason()?.orElse(null)
        if (blocked != null && blocked.knownEnum() != BlockedReason.Known.BLOCKED_REASON_UNSPECIFIED) {
            throw LlmInvalidOutput("Gemini bloqueó el prompt ($blocked)", usage)
        }
        val candidate = response.candidates().orElse(emptyList()).firstOrNull()
            ?: throw LlmInvalidOutput("Gemini no devolvió candidatos", usage)
        val finish: FinishReason? = candidate.finishReason().orElse(null)
        val truncated = finish?.knownEnum() == FinishReason.Known.MAX_TOKENS
        if (finish != null && !truncated && finish.knownEnum() !in COMPLETED) {
            throw LlmInvalidOutput("Gemini terminó la respuesta con $finish", usage)
        }
        val parts = candidate.content().flatMap { it.parts() }.orElse(emptyList())
        val text = parts.filter { it.thought().orElse(false) != true }
            .mapNotNull { it.text().orElse(null) }
            .joinToString("")
        return GeminiText(text, truncated)
    }

    private val COMPLETED = setOf(
        FinishReason.Known.FINISH_REASON_UNSPECIFIED,
        FinishReason.Known.STOP,
        FinishReason.Known.CONTINUATION,
    )
}
