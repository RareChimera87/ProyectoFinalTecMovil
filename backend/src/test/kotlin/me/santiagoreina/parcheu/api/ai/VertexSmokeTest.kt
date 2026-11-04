package me.santiagoreina.parcheu.api.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.platform.config.Env
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Humo manual contra Vertex AI dev (criterio de E0.9). **No corre en CI ni en `./gradlew test`**:
 * el tag `vertex` se excluye salvo con `-Pvertex`.
 *
 * ```bash
 * gcloud auth application-default login   # cuenta con roles/aiplatform.user en parcheu-dev
 * cd backend && ./gradlew test -Pvertex --tests '*VertexSmokeTest*' -PsinContenedores
 * ```
 *
 * Variables opcionales: `AI_PROVIDER` (gemini por defecto; `claude` cuando haya cuota),
 * `AI_VERTEX_PROJECT` (parcheu-dev por defecto) y los `AI_MODEL_*`.
 */
@Tag("vertex")
class VertexSmokeTest {
    private fun config(): AiConfig {
        val vars = System.getenv().toMutableMap()
        vars.putIfAbsent("AI_VERTEX_PROJECT", "parcheu-dev")
        vars.putIfAbsent("AI_PROVIDER", "gemini")
        return AiConfig.fromEnv(vars, Env.DEV)
    }

    @Test
    fun `Vertex dev devuelve JSON válido para el esquema del juez y se registra el costo`(): Unit = runBlocking {
        val config = config()
        val client = AiModule.createClient(config)
        val usage = InMemoryAiUsageStore()
        val gateway = AiGateway(client, config, usage, java.time.Clock.systemUTC())
        val result = gateway.json(
            AiCall(
                purpose = AiPurpose.SIMULATION_JUDGE,
                system = "Eres el juez de compatibilidad de ParcheU. Responde solo con el JSON pedido, en español.",
                prompt = "Evalúa la afinidad de amistad entre A (ama el fútbol, planea todo) y B (odia el fútbol, improvisa).",
                maxOutputTokens = 2048,
            ),
            JUDGE_SCHEMA,
        )
        println("Modelo: ${result.model}, tokens: ${result.usage}, JSON: ${result.json}")
        assertTrue(result.json["afinidad"]!!.jsonPrimitive.int in 0..100)
        assertTrue(JsonSchemaCheck.errors(result.json, JUDGE_SCHEMA).isEmpty())
        assertEquals(1, usage.entries.size)
        assertTrue(usage.entries.single().costMicros > 0)
        (client as? AutoCloseable)?.close()
    }

    @Test
    fun `Vertex dev responde texto con el modelo lite`(): Unit = runBlocking {
        val config = config()
        val client = AiModule.createClient(config)
        val gateway = AiGateway(client, config, InMemoryAiUsageStore(), java.time.Clock.systemUTC())
        val result = gateway.text(AiCall(AiPurpose.SMOKE_TEST, "Saluda en una frase corta en español colombiano.", maxOutputTokens = 512))
        println("Modelo: ${result.model}, tokens: ${result.usage}, texto: ${result.text}")
        assertEquals(config.models.lite, result.model)
        assertTrue(result.text.isNotBlank())
        (client as? AutoCloseable)?.close()
    }
}
