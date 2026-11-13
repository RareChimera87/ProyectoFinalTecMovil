package me.santiagoreina.parcheu.api.ai

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import com.anthropic.vertex.backends.VertexBackend
import com.google.auth.oauth2.GoogleCredentials

/**
 * Claude en Vertex AI con el SDK Java de Anthropic (`com.anthropic:anthropic-java-vertex`,
 * backend [VertexBackend]). Autenticación por ADC. Solo se activa con `AI_PROVIDER=claude`
 * después de confirmar cuota y créditos (ADR-0008). La lógica de mensajes está en [ClaudeMessagesClient].
 */
class ClaudeVertexClient(
    project: String,
    location: String,
) : ClaudeMessagesClient({ buildClient(project, location) }) {
    override val provider = AiProvider.CLAUDE

    private companion object {
        fun buildClient(project: String, location: String): AnthropicClientAsync = AnthropicOkHttpClientAsync.builder()
            .backend(
                VertexBackend.builder()
                    .googleCredentials(GoogleCredentials.getApplicationDefault())
                    .project(project)
                    .region(location)
                    .build(),
            )
            .timeout(ClaudeMessagesClient.TIMEOUT)
            .maxRetries(ClaudeMessagesClient.MAX_RETRIES)
            .build()
    }
}
