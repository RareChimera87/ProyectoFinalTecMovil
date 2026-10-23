package me.santiagoreina.parcheu.api.ai

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.vertex.backends.VertexBackend
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration

/**
 * Claude en Vertex AI con el SDK Java de Anthropic (`com.anthropic:anthropic-java-vertex`,
 * backend [VertexBackend]). Autenticación por ADC. Solo se activa con `AI_PROVIDER=claude`
 * después de confirmar cuota y créditos (ADR-0008).
 *
 * Esfuerzo `LOW`: los turnos son cortos y el costo manda (ADR-0010). La salida JSON usa
 * salidas estructuradas (`output_config.format`) y además se valida con [JsonSchemaCheck].
 */
class ClaudeVertexClient(
    project: String,
    location: String,
) : LlmClient, AutoCloseable {
    override val provider = AiProvider.CLAUDE

    // Perezoso: las credenciales (ADC) se resuelven en la primera llamada, no al arrancar.
    private val clientDelegate = lazy { buildClient(project, location) }
    private val client: AnthropicClientAsync by clientDelegate

    private fun buildClient(project: String, location: String): AnthropicClientAsync = AnthropicOkHttpClientAsync.builder()
        .backend(
            VertexBackend.builder()
                .googleCredentials(GoogleCredentials.getApplicationDefault())
                .project(project)
                .region(location)
                .build(),
        )
        .timeout(Duration.ofSeconds(120))
        .maxRetries(2)
        .build()

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        val message = client.messages().create(params(request, OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())).await()
        val usage = usageOf(message)
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw LlmInvalidOutput("Claude rechazó la petición (refusal)", usage)
        }
        return LlmTextResult(textOf(message), request.model, usage)
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
        val usage = usageOf(message)
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw LlmInvalidOutput("Claude rechazó la petición (refusal)", usage)
        }
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

    /** Las salidas estructuradas de Claude exigen `additionalProperties: false` en cada objeto. */
    private fun closeObjects(schema: JsonObject): JsonObject = JsonObject(
        schema.mapValues { (key, value) ->
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

    private fun usageOf(message: Message): LlmUsage {
        val u = message.usage()
        return LlmUsage(u.inputTokens().toInt(), u.outputTokens().toInt())
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }
}
