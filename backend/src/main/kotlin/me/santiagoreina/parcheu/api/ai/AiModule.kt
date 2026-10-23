package me.santiagoreina.parcheu.api.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Clock

/** Cableado de la pasarela de IA; lo usa `AppContainer` al arrancar. */
object AiModule {
    /**
     * Instancia el cliente del proveedor configurado. Con `AI_PROVIDER` inválido ya falló
     * [AiConfig.fromEnv]; aquí solo se elige la implementación.
     */
    fun createClient(config: AiConfig): LlmClient = when (config.provider) {
        AiProvider.GEMINI -> GeminiVertexClient(requireNotNull(config.vertexProject), config.vertexLocation, config.geminiThinkingLevel)
        AiProvider.CLAUDE -> ClaudeVertexClient(requireNotNull(config.vertexProject), config.vertexLocation)
        AiProvider.FAKE -> FakeLlmClient()
    }

    fun gateway(config: AiConfig, usage: AiUsageStore, clock: Clock, client: LlmClient = createClient(config)): AiGateway =
        AiGateway(client, config, usage, clock)
}

/** JSON de kotlinx a valores Java (Map/List/String/Number/Boolean) para los SDK. */
internal fun JsonElement.toJavaValue(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> toJavaMap()
    is JsonArray -> map { it.toJavaValue() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull
    }
}

internal fun JsonObject.toJavaMap(): Map<String, Any?> = mapValues { it.value.toJavaValue() }
