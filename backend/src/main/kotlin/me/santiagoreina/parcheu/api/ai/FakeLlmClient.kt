package me.santiagoreina.parcheu.api.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cliente determinista para `ENV=local` y tests: no llama a nadie. Los tokens se estiman con
 * ~4 caracteres por token para que el costo registrado sea realista. Los tests pueden fijar la
 * respuesta con [textResponder] / [jsonResponder] y contar llamadas con [calls].
 */
class FakeLlmClient(
    var textResponder: (LlmRequest) -> String = { "Respuesta simulada (${it.model})" },
    /** Por defecto genera un objeto mínimo que cumple el esquema. */
    var jsonResponder: ((LlmRequest, JsonObject) -> JsonObject)? = null,
) : LlmClient {
    override val provider = AiProvider.FAKE

    private val counter = AtomicInteger()
    val calls: Int get() = counter.get()

    /** Último request recibido (para comprobar el modelo resuelto). */
    @Volatile var lastRequest: LlmRequest? = null
        private set

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        counter.incrementAndGet()
        lastRequest = request
        val text = textResponder(request)
        return LlmTextResult(text, request.model, usageOf(request, text))
    }

    override suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult {
        counter.incrementAndGet()
        lastRequest = request
        val raw = jsonResponder?.invoke(request, schema) ?: sample(schema) as JsonObject
        val usage = usageOf(request, raw.toString())
        val json = JsonSchemaCheck.parseAndValidate(raw.toString(), schema, usage)
        return LlmJsonResult(json, request.model, usage)
    }

    private fun usageOf(request: LlmRequest, output: String) = LlmUsage(
        tokensIn = estimateTokens((request.system ?: "") + request.prompt),
        tokensOut = estimateTokens(output),
    )

    companion object {
        fun estimateTokens(text: String): Int = maxOf(1, (text.length + 3) / 4)

        /** Valor de ejemplo que cumple [schema]. */
        fun sample(schema: JsonObject): JsonElement {
            (schema["enum"] as? JsonArray)?.firstOrNull()?.let { return it }
            return when ((schema["type"] as? JsonPrimitive)?.contentOrNull) {
                "object" -> buildJsonObject {
                    (schema["properties"] as? JsonObject)?.forEach { (name, sub) ->
                        if (sub is JsonObject) put(name, sample(sub))
                    }
                }
                "array" -> buildJsonArray { (schema["items"] as? JsonObject)?.let { add(sample(it)) } }
                "integer", "number" -> JsonPrimitive((schema["minimum"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 50L)
                "boolean" -> JsonPrimitive(true)
                else -> JsonPrimitive("texto simulado")
            }
        }
    }
}
