package me.santiagoreina.parcheu.api.ai

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
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
) : LlmClient, AutoCloseable {
    override val provider = AiProvider.GEMINI

    // Perezoso: las credenciales (ADC) se resuelven en la primera llamada, no al arrancar.
    private val clientDelegate = lazy { Client.builder().vertexAI(true).project(project).location(location).build() }
    private val client: Client by clientDelegate

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        val response = call(request, config(request).build())
        return LlmTextResult(response.text().orEmpty(), request.model, usageOf(response))
    }

    override suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult {
        val config = config(request)
            .responseMimeType("application/json")
            .responseJsonSchema(schema.toJavaValue())
            .build()
        val response = call(request, config)
        val usage = usageOf(response)
        val json = JsonSchemaCheck.parseAndValidate(response.text().orEmpty(), schema, usage)
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
    private fun usageOf(response: GenerateContentResponse): LlmUsage {
        val meta = response.usageMetadata().orElse(null) ?: return LlmUsage(0, 0)
        val input = meta.promptTokenCount().orElse(0)
        val output = meta.candidatesTokenCount().orElse(0) + meta.thoughtsTokenCount().orElse(0)
        return LlmUsage(input, output)
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }
}
